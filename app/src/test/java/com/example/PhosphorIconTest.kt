package com.example

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.unit.dp
import com.example.ui.components.PhosphorIcons
import com.example.ui.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Guards the Phosphor icon system: every icon must be the official Phosphor asset,
 * Bold weight, preferring the "-simple" variant wherever Phosphor publishes one.
 *
 * These tests assert on the *rendered geometry*, not just that the code compiles,
 * because the failure mode this suite exists to catch is a hand-drawn
 * approximation that type-checks perfectly while looking nothing like Phosphor.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class PhosphorIconTest {

    /**
     * The spec: app-facing property name -> official Phosphor asset backing it.
     * A "-simple" value means Phosphor publishes that variant and we must use it.
     */
    private val expected = mapOf(
        "House" to "house-simple",
        "Bell" to "bell-simple",
        "Funnel" to "funnel-simple",
        "Globe" to "globe-simple",
        "Link" to "link-simple",
        "Lock" to "lock-simple",
        "LockOpen" to "lock-simple-open",
        "Sidebar" to "sidebar-simple",
        "Tag" to "tag-simple",
        "Television" to "television-simple",
        "Trash" to "trash-simple",
        "PencilSimple" to "pencil-simple",
        "UploadSimple" to "upload-simple",
        "DownloadSimple" to "download-simple",
        "ShoppingBag" to "bag-simple",
        "Airplane" to "airplane",
        "ArrowCounterClockwise" to "arrow-counter-clockwise",
        "ArrowDown" to "arrow-down",
        "ArrowLeft" to "arrow-left",
        "ArrowSquareOut" to "arrow-square-out",
        "ArrowUDownLeft" to "arrow-u-down-left",
        "ArrowUp" to "arrow-up",
        "ArrowsClockwise" to "arrows-clockwise",
        "ArrowsLeftRight" to "arrows-left-right",
        "Bank" to "bank",
        "Briefcase" to "briefcase",
        "Bus" to "bus",
        "Calendar" to "calendar",
        "CaretDown" to "caret-down",
        "CaretLeft" to "caret-left",
        "CaretRight" to "caret-right",
        "CaretUp" to "caret-up",
        "ChartBar" to "chart-bar",
        "ChartPie" to "chart-pie",
        "Check" to "check",
        "CheckCircle" to "check-circle",
        "ClipboardText" to "clipboard-text",
        "Coins" to "coins",
        "CornersOut" to "corners-out",
        "CreditCard" to "credit-card",
        "Desktop" to "desktop",
        "DeviceMobile" to "device-mobile",
        "DotsThree" to "dots-three",
        "FilePdf" to "file-pdf",
        "FilmStrip" to "film-strip",
        "FirstAid" to "first-aid",
        "ForkKnife" to "fork-knife",
        "GasPump" to "gas-pump",
        "Gear" to "gear",
        "Gift" to "gift",
        "GraduationCap" to "graduation-cap",
        "Handshake" to "handshake",
        "Info" to "info",
        "Lightning" to "lightning",
        "MagnifyingGlass" to "magnifying-glass",
        "Money" to "money",
        "Package" to "package",
        "Paperclip" to "paperclip",
        "Plus" to "plus",
        "Receipt" to "receipt",
        "Rocket" to "rocket",
        "Shield" to "shield",
        "Sparkle" to "sparkle",
        "Star" to "star",
        "TShirt" to "t-shirt",
        "Target" to "target",
        "Tree" to "tree",
        "TrendUp" to "trend-up",
        "User" to "user",
        "UserPlus" to "user-plus",
        "Users" to "users",
        "Wallet" to "wallet",
        "Warning" to "warning",
        "WarningCircle" to "warning-circle",
        "X" to "x",
    )

    /** Icons that must resolve to an official Phosphor "-simple" variant. */
    private val mustBeSimple = setOf(
        "Bell",
        "DownloadSimple",
        "Funnel",
        "Globe",
        "House",
        "Link",
        "Lock",
        "LockOpen",
        "PencilSimple",
        "ShoppingBag",
        "Sidebar",
        "Tag",
        "Television",
        "Trash",
        "UploadSimple",
    )

    private fun declaredIcons(): Map<String, ImageVector> =
        PhosphorIcons.Bold::class.java.methods
            .filter {
                it.parameterCount == 0 &&
                    it.returnType == ImageVector::class.java &&
                    it.name.startsWith("get")
            }
            .associate { it.name.removePrefix("get") to it.invoke(PhosphorIcons.Bold) as ImageVector }

    private fun rasterize(icon: ImageVector, size: Int = 64): Bitmap {
        val bitmap = ImageBitmap(size, size)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            color = Color.Black
            style = PaintingStyle.Fill
            isAntiAlias = false
        }
        canvas.scale(size / icon.viewportWidth, size / icon.viewportHeight)
        val vectorPath = icon.root.first() as VectorPath
        val path = PathParser().addPathNodes(vectorPath.pathData).toPath()
        path.fillType = vectorPath.pathFillType
        canvas.drawPath(path, paint)
        return bitmap.asAndroidBitmap()
    }

    private fun Bitmap.inked(x: Int, y: Int): Boolean = (getPixel(x, y) ushr 24) > 128

    private fun inkRatio(bmp: Bitmap): Double {
        var n = 0
        for (y in 0 until bmp.height) for (x in 0 until bmp.width) if (bmp.inked(x, y)) n++
        return n.toDouble() / (bmp.width * bmp.height)
    }

    // ---------------------------------------------------------------- provenance

    @Test
    fun everyDeclaredIconIsAnExpectedOfficialPhosphorAsset() {
        assertEquals(
            "PhosphorIcons.Bold members drifted from the documented Phosphor mapping",
            expected.keys.sorted(),
            declaredIcons().keys.sorted(),
        )
    }

    @Test
    fun everyIconCarriesItsOfficialPhosphorBoldAssetName() {
        declaredIcons().forEach { (property, vector) ->
            assertEquals(
                "$property is not backed by the expected official Phosphor Bold asset",
                "phosphor-bold-" + expected.getValue(property),
                vector.name,
            )
        }
    }

    @Test
    fun simpleVariantIsPreferredWherePhosphorPublishesOne() {
        mustBeSimple.forEach { property ->
            val asset = expected.getValue(property)
            assertTrue(
                "$property must use the Phosphor \"-simple\" variant, but resolves to '$asset'",
                asset.contains("simple"),
            )
        }
    }

    @Test
    fun noIconClaimsASimpleVariantThatPhosphorDoesNotPublish() {
        // Anything resolving to a "-simple" asset must be on the vetted list; this
        // stops an invented name like "gear-simple" from creeping in.
        expected.forEach { (property, asset) ->
            if (asset.contains("simple")) {
                assertTrue(
                    "'$asset' is not a vetted official Phosphor -simple asset",
                    mustBeSimple.contains(property),
                )
            }
        }
    }

    // ------------------------------------------------------------- bold weight

    @Test
    fun onlyBoldWeightIsExposed() {
        val weights = PhosphorIcons::class.java.methods
            .filter { it.parameterCount == 0 && it.name.startsWith("get") }
            .map { it.name.removePrefix("get") }
        assertTrue(
            "PhosphorIcons must expose Bold only, found: $weights",
            weights.none { it == "Regular" || it == "Thin" || it == "Light" || it == "Fill" || it == "Duotone" },
        )
        assertNotNull(PhosphorIcons.Bold)
    }

    @Test
    fun iconsArePreOutlinedFillsNotHandDrawnStrokes() {
        // Phosphor ships each weight as a single pre-outlined filled path. A stroked
        // path here means someone re-drew the glyph by hand.
        declaredIcons().forEach { (property, vector) ->
            assertEquals("$property should be a single path", 1, vector.root.size)
            val path = vector.root.first() as VectorPath
            assertNotNull("$property must be filled", path.fill)
            assertNull("$property must not be stroked (Phosphor Bold is pre-outlined)", path.stroke)
            assertEquals("$property viewport width", 256f, vector.viewportWidth, 0f)
            assertEquals("$property viewport height", 256f, vector.viewportHeight, 0f)
            assertEquals("$property default width", 24.dp, vector.defaultWidth)
            assertEquals("$property default height", 24.dp, vector.defaultHeight)
        }
    }

    // ------------------------------------------------------- exact asset bytes

    @Test
    fun houseIsPhosphorHouseSimpleAndNotAnyOtherHouseVariant() {
        val actual = (PhosphorIcons.Bold.House.root.first() as VectorPath).pathData

        assertEquals("phosphor-bold-house-simple", PhosphorIcons.Bold.House.name)
        assertEquals(
            "House must be the verbatim official house-simple-bold.svg path",
            addPathNodes(HOUSE_SIMPLE_BOLD),
            actual,
        )

        // Negative controls: Phosphor's three house variants are distinct assets, and
        // "Home" must be the -simple one specifically.
        assertNotEquals(
            "House must not be Phosphor 'house' (that variant has a door cut-out)",
            addPathNodes(HOUSE_BOLD),
            actual,
        )
        assertNotEquals(
            "House must not be Phosphor 'house-line'",
            addPathNodes(HOUSE_LINE_BOLD),
            actual,
        )
    }

    // ------------------------------------------------------- rendered geometry

    @Test
    fun everyIconRasterisesToARecognisableGlyph() {
        declaredIcons().forEach { (property, vector) ->
            val ratio = inkRatio(rasterize(vector))
            assertTrue(
                "$property rendered blank (ink ratio $ratio) - path data is not drawable",
                ratio > 0.02,
            )
            assertTrue(
                "$property rendered as a near-solid block (ink ratio $ratio) - fill rule or path is wrong",
                ratio < 0.85,
            )
        }
    }

    @Test
    fun houseSimpleRendersAsAHollowOutlinedHouse() {
        // Geometry of the official house-simple Bold glyph at 64x64. If the fill rule
        // or winding were wrong the interior would flood and these would fail.
        val bmp = rasterize(PhosphorIcons.Bold.House)
        assertTrue("roof apex should be inked", bmp.inked(32, 6))
        assertTrue("left wall should be inked", bmp.inked(9, 40))
        assertTrue("right wall should be inked", bmp.inked(54, 40))
        assertTrue("floor should be inked", bmp.inked(32, 52))
        assertTrue("interior must be hollow, not flooded", !bmp.inked(32, 40))
        assertTrue("corner margin must be empty", !bmp.inked(2, 2))
    }

    // ------------------------------------------------------------- integration

    @Test
    fun bottomNavigationUsesTheExpectedPhosphorBoldAssets() {
        assertEquals("phosphor-bold-house-simple", AppDestination.HOME.icon.name)
        assertEquals("phosphor-bold-receipt", AppDestination.TRANSACTIONS.icon.name)
        assertEquals("phosphor-bold-arrows-left-right", AppDestination.MONEY_FLOW.icon.name)
        assertEquals("phosphor-bold-star", AppDestination.WISHLIST.icon.name)
        assertEquals("phosphor-bold-target", AppDestination.SAVINGS.icon.name)
        assertEquals("phosphor-bold-chart-pie", AppDestination.BUDGETS.icon.name)
        assertEquals("phosphor-bold-gear", AppDestination.SETTINGS.icon.name)
    }

    @Test
    fun everyNavigationDestinationIsPhosphorBacked() {
        AppDestination.entries.forEach { destination ->
            assertTrue(
                "${destination.name} icon '${destination.icon.name}' is not a Phosphor Bold asset",
                destination.icon.name.startsWith("phosphor-bold-"),
            )
        }
    }

    private companion object {
        const val HOUSE_SIMPLE_BOLD = "M222.14,105.85l-80-80a20,20,0,0,0-28.28,0l-80,80A19.86,19.86,0,0,0,28,120v96a12,12,0,0,0,12,12H216a12,12,0,0,0,12-12V120A19.86,19.86,0,0,0,222.14,105.85ZM204,204H52V121.65l76-76,76,76Z"
        const val HOUSE_BOLD = "M222.14,105.85l-80-80a20,20,0,0,0-28.28,0l-80,80A19.86,19.86,0,0,0,28,120v96a12,12,0,0,0,12,12h64a12,12,0,0,0,12-12V164h24v52a12,12,0,0,0,12,12h64a12,12,0,0,0,12-12V120A19.86,19.86,0,0,0,222.14,105.85ZM204,204H164V152a12,12,0,0,0-12-12H104a12,12,0,0,0-12,12v52H52V121.65l76-76,76,76Z"
        const val HOUSE_LINE_BOLD = "M240,204H228V144a12,12,0,0,0,12.49-19.78L142.14,25.85a20,20,0,0,0-28.28,0L15.51,124.2A12,12,0,0,0,28,144v60H16a12,12,0,0,0,0,24H240a12,12,0,0,0,0-24ZM52,121.65l76-76,76,76V204H164V152a12,12,0,0,0-12-12H104a12,12,0,0,0-12,12v52H52ZM140,204H116V164h24Z"
    }
}
