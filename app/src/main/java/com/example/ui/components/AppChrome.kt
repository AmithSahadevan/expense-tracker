package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.entities.UserEntity

/**
 * Shared app chrome: the gradient top bar and the bottom fade that every page draws over
 * its scrolling content.
 *
 * Both are *overlays*, not layout slots. Content is laid out full-bleed underneath and
 * scrolls behind them, so rows dissolve into the background instead of being clipped by a
 * hard edge. Each stays solid only where it must remain legible - behind the title and its
 * chrome, behind the dock - and then fades across a long run into the page.
 */
object AppChrome {

    /** Height of the title row itself, below the status bar. */
    val TitleRowHeight: Dp = 56.dp

    /**
     * How far the top gradient runs past the header before it is fully transparent. Kept
     * short so the chrome visibly ends just below the header (or the search row) - long
     * enough to soften the edge, not so long that it shades the content below it.
     */
    val TopFadeHeight: Dp = 40.dp

    /** Breathing room between the header's lower edge and the first row of content. */
    val ContentTopGap: Dp = 16.dp

    /** Total height of the bottom scrim, including the dock that sits inside it. */
    val BottomScrimHeight: Dp = 170.dp

    /** Bottom padding a page's scroll container needs to clear the dock. */
    val BottomContentPadding: Dp = 132.dp

    /**
     * Top padding for a title-only page, whose header height is known without measuring.
     */
    val topContentPadding: Dp
        @Composable get() = contentTopPadding(
            WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + TitleRowHeight
        )

    /**
     * Where a page's content starts, given the height of its header - the title row alone,
     * or the title plus a search row on pages that carry one.
     *
     * Content begins just below the header and the fade overlaps its first rows, mirroring
     * how the bottom scrim overlaps the last ones. [topFadeBrush] is weighted so that
     * overlap lands in its light tail, shading the first row without washing it out.
     */
    fun contentTopPadding(headerHeight: Dp): Dp = headerHeight + ContentTopGap

    /**
     * The header plate behind the title and any search row. Held at the same density as
     * the background behind the dock (96-100%) so both ends of the screen read as one
     * material; the hint of translucency only shows as content passes beneath it.
     */
    @Composable
    fun topHeaderBrush(): Brush {
        val bg = MaterialTheme.colorScheme.background
        return Brush.verticalGradient(
            0.0f to bg,
            0.60f to bg.copy(alpha = 0.99f),
            1.0f to bg.copy(alpha = 0.96f),
        )
    }

    /** Transparent at the top, solid behind the dock, so the dock always stays legible. */
    @Composable
    fun bottomBrush(): Brush {
        val bg = MaterialTheme.colorScheme.background
        return Brush.verticalGradient(
            0.0f to Color.Transparent,
            0.28f to bg.copy(alpha = 0.72f),
            0.52f to bg.copy(alpha = 0.96f),
            1.0f to bg,
        )
    }

    /**
     * Carries the header's lower edge the rest of the way to transparent.
     *
     * The ramp runs across the strip's whole height rather than mirroring [bottomBrush]'s
     * stop *fractions*: the bottom scrim is three times taller, so copying its fractions
     * would compress the same fall-off into a third of the distance and read as an edge.
     * Spreading it out here gives both ends a comparably gradual transition.
     */
    @Composable
    fun topFadeBrush(): Brush {
        val bg = MaterialTheme.colorScheme.background
        return Brush.verticalGradient(
            0.0f to bg.copy(alpha = 0.96f),
            0.18f to bg.copy(alpha = 0.55f),
            0.40f to bg.copy(alpha = 0.20f),
            0.70f to bg.copy(alpha = 0.06f),
            1.0f to Color.Transparent,
        )
    }
}

/**
 * The gradient title bar shared by every page. Draw it as the last child of a [Box] so it
 * overlays the page's scrolling content.
 *
 * The header itself - status bar, title, and any [content] such as a search row - stays
 * solid so text never sits on moving pixels. Below it a [AppChrome.TopFadeHeight] gradient
 * dissolves into the page. Pages with extra chrome read the solid height back through
 * [onSolidHeightChanged] and use it as their scroll container's top padding, so content
 * scrolls up under the fade rather than stopping at a visible edge.
 *
 * A page whose content never scrolls passes `showFade = false`: with nothing moving
 * beneath it the gradient has nothing to dissolve into, and reads as a stray band.
 */
@Composable
fun GradientTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    centerTitle: Boolean = false,
    showFade: Boolean = true,
    onSolidHeightChanged: ((Dp) -> Unit)? = null,
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("gradient_top_bar"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppChrome.topHeaderBrush())
                // Must sit *above* statusBarsPadding in the chain: a size callback placed
                // below it reports the padded-in content size, under-reporting the header
                // by the whole status bar height.
                .onSizeChanged { size ->
                    onSolidHeightChanged?.invoke(with(density) { size.height.toDp() })
                }
                .statusBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppChrome.TitleRowHeight)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (navigationIcon != null) {
                        navigationIcon()
                    }
                    if (centerTitle) {
                        Box(modifier = Modifier.weight(1f))
                    } else {
                        TopBarTitle(title = title, modifier = Modifier.weight(1f))
                    }
                    actions()
                }
                if (centerTitle) {
                    TopBarTitle(title = title, modifier = Modifier.align(Alignment.Center))
                }
            }
            content?.invoke(this)
        }

        if (showFade) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppChrome.TopFadeHeight)
                    .background(AppChrome.topFadeBrush()),
            )
        }
    }
}

@Composable
private fun TopBarTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Black,
            letterSpacing = (-0.5).sp,
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

/**
 * The bottom fade that darkens content as it scrolls under the dock. Draw it beneath the
 * dock so the dock's icons sit on solid background.
 */
@Composable
fun BottomFadeScrim(
    modifier: Modifier = Modifier,
    height: Dp = AppChrome.BottomScrimHeight,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(AppChrome.bottomBrush())
            .testTag("bottom_fade_scrim"),
    )
}

/**
 * The user's avatar, shared by the dock and the navigation drawer so both stay in sync
 * with whatever the profile is set to.
 */
@Composable
fun ProfileAvatar(
    currentUser: UserEntity?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    onClick: (() -> Unit)? = null,
) {
    val avatarColor = remember(currentUser?.avatarColorHex) {
        try {
            Color(android.graphics.Color.parseColor(currentUser?.avatarColorHex ?: "#0C0F14"))
        } catch (_: Exception) {
            null
        }
    }
    val onSurface = MaterialTheme.colorScheme.onSurface
    val isDarkAvatar = currentUser?.avatarColorHex?.lowercase() == "#0c0f14" ||
        currentUser?.avatarColorHex?.lowercase() == "#242426"
    val displayColor = if (isDarkAvatar || avatarColor == null) onSurface else avatarColor

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(displayColor.copy(alpha = 0.15f))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (currentUser?.avatarImagePath != null) {
            AsyncImage(
                model = "file:///android_asset/${currentUser.avatarImagePath}",
                contentDescription = "Profile",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = PhosphorIcons.Bold.User,
                contentDescription = "Profile",
                tint = displayColor,
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
