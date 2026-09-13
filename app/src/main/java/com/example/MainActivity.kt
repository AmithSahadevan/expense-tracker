package com.example

import android.os.Build
import android.os.Bundle
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.local.AppDatabase
import com.example.data.repository.AuthRepository
import com.example.data.repository.ExpenseTrackerRepository
import com.example.ui.AppShell
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ExpenseTrackerViewModel
import com.example.ui.viewmodel.ExpenseTrackerViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: ExpenseTrackerViewModel by viewModels {
        val database = AppDatabase.getInstance(applicationContext)
        val authRepo = AuthRepository(database.userDao())
        val expenseRepo = ExpenseTrackerRepository(database)
        ExpenseTrackerViewModelFactory(authRepo, expenseRepo)
    }

    // Flag indicating if the VSYNC keep-alive choreographer loop is currently running
    private var isFrameCallbackActive = false

    // VSYNC Keep-Alive Loop: Posts on every frame to prevent OEM display server downclocking (e.g. HyperOS / MIUI)
    private val keepAliveFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (isFrameCallbackActive) {
                Choreographer.getInstance().postFrameCallback(this)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppShell(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 1. Force display mode, window attributes, and SurfaceControl to max refresh rate (120Hz)
        configureMaxRefreshRate()

        // 2. Start VSYNC Keep-Alive Loop on resume
        startKeepAliveFrameCallback()
    }

    override fun onPause() {
        super.onPause()
        // Stop VSYNC Keep-Alive Loop when activity is not active to conserve battery
        stopKeepAliveFrameCallback()
    }

    /**
     * Inspects available display modes and applies maximum available refresh rate (e.g. 120Hz)
     * across window attributes, display mode ID, and root surface control (Android 12+ / S).
     */
    private fun configureMaxRefreshRate() {
        // Inspect active display and its supported modes
        val activeDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }

        val supportedModes = activeDisplay?.supportedModes ?: emptyArray()
        val maxMode = supportedModes.maxByOrNull { it.refreshRate }
        val maxRefreshRate = maxMode?.refreshRate ?: 120f

        val layoutParams = window.attributes

        // Standard API (API 23+): preferredRefreshRate
        layoutParams.preferredRefreshRate = maxRefreshRate

        // Set hidden platform fields preferredMinDisplayRefreshRate & preferredMaxDisplayRefreshRate (bypasses OEM throttling like HyperOS)
        try {
            val minField = layoutParams.javaClass.getField("preferredMinDisplayRefreshRate")
            minField.setFloat(layoutParams, maxRefreshRate)
        } catch (_: Throwable) {
            // Field not present or restricted
        }

        try {
            val maxField = layoutParams.javaClass.getField("preferredMaxDisplayRefreshRate")
            maxField.setFloat(layoutParams, maxRefreshRate)
        } catch (_: Throwable) {
            // Field not present or restricted
        }

        // Android 6.0+ (API 23+): Lock to specific display mode ID matching peak refresh rate (e.g. 120Hz mode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && maxMode != null) {
            layoutParams.preferredDisplayModeId = maxMode.modeId
        }

        window.attributes = layoutParams

        // Android 12+ (API 31+): Force root SurfaceControl frame rate to max refresh rate with ALWAYS strategy
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            window.decorView.post {
                try {
                    val rootSurfaceControl = window.decorView.rootSurfaceControl
                    if (rootSurfaceControl != null) {
                        val setFrameRateMethod = rootSurfaceControl.javaClass.getMethod(
                            "setFrameRate",
                            Float::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType
                        )
                        setFrameRateMethod.invoke(
                            rootSurfaceControl,
                            maxRefreshRate,
                            android.view.Surface.FRAME_RATE_COMPATIBILITY_DEFAULT,
                            android.view.Surface.CHANGE_FRAME_RATE_ALWAYS
                        )
                    }
                } catch (_: Throwable) {
                    // Graceful safeguard against vendor-specific SurfaceControl exceptions
                }
            }
        }
    }

    private fun startKeepAliveFrameCallback() {
        if (!isFrameCallbackActive) {
            isFrameCallbackActive = true
            Choreographer.getInstance().postFrameCallback(keepAliveFrameCallback)
        }
    }

    private fun stopKeepAliveFrameCallback() {
        isFrameCallbackActive = false
        Choreographer.getInstance().removeFrameCallback(keepAliveFrameCallback)
    }
}

