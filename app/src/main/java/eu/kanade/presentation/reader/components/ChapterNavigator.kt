package eu.kanade.presentation.reader.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalSlider
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.theme.TachiyomiPreviewTheme
import eu.kanade.presentation.util.isTabletUi
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource
import kotlin.math.roundToInt

enum class ChapterNavigatorType {
    HORIZONTAL_LTR,
    HORIZONTAL_RTL,
    VERTICAL_LEFT,
    VERTICAL_RIGHT,
    ;

    fun isHorizontal() = this in setOf(HORIZONTAL_LTR, HORIZONTAL_RTL)
}

@Composable
fun ChapterNavigator(
    type: ChapterNavigatorType,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    currentPage: Int,
    totalPages: Int,
    displayCurrentPage: Int,
    displayTotalPages: Int,
    visiblePageIndices: IntRange?,
    onPageIndexChange: (Int) -> Unit,
    onPageIndexChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    val state = remember(totalPages) {
        SliderState(
            value = currentPage.toFloat(),
            steps = totalPages - 2,
            valueRange = 1f..totalPages.toFloat(),
        )
    }
    state.value = currentPage.toFloat()
    state.onValueChange = { onPageIndexChange(it.roundToInt() - 1) }
    state.onValueChangeFinished = onPageIndexChangeFinished

    val interactionSource = remember { MutableInteractionSource() }
    val sliderDragged by interactionSource.collectIsDraggedAsState()
    LaunchedEffect(currentPage) {
        if (sliderDragged) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val isTabletUi = isTabletUi()
    val mainAxisPadding = if (isTabletUi) 24.dp else 8.dp

    // Match with toolbar background color set in ReaderActivity
    val backgroundColor = MaterialTheme.colorScheme
        .surfaceColorAtElevation(3.dp)
        .copy(alpha = if (isSystemInDarkTheme()) 0.9f else 0.95f)
    val buttonColor = IconButtonDefaults.filledIconButtonColors(
        containerColor = backgroundColor,
        disabledContainerColor = backgroundColor,
    )

    if (type.isHorizontal()) {
        HorizontalChapterNavigator(
            isRtl = type == ChapterNavigatorType.HORIZONTAL_RTL,
            state = state,
            onNextChapter = onNextChapter,
            enabledNext = enabledNext,
            onPreviousChapter = onPreviousChapter,
            enabledPrevious = enabledPrevious,
            totalPages = totalPages,
            displayCurrentPage = displayCurrentPage,
            displayTotalPages = displayTotalPages,
            visiblePageIndices = visiblePageIndices,
            interactionSource = interactionSource,
            mainAxisPadding = mainAxisPadding,
            backgroundColor = backgroundColor,
            buttonColor = buttonColor,
            modifier = modifier,
        )
    } else {
        VerticalChapterNavigator(
            state = state,
            onNextChapter = onNextChapter,
            enabledNext = enabledNext,
            onPreviousChapter = onPreviousChapter,
            enabledPrevious = enabledPrevious,
            totalPages = totalPages,
            displayCurrentPage = displayCurrentPage,
            displayTotalPages = displayTotalPages,
            visiblePageIndices = visiblePageIndices,
            interactionSource = interactionSource,
            mainAxisPadding = mainAxisPadding,
            backgroundColor = backgroundColor,
            buttonColor = buttonColor,
            modifier = modifier,
        )
    }
}

@Composable
fun HorizontalChapterNavigator(
    isRtl: Boolean,
    state: SliderState,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    totalPages: Int,
    displayCurrentPage: Int,
    displayTotalPages: Int,
    visiblePageIndices: IntRange?,
    interactionSource: MutableInteractionSource,
    mainAxisPadding: Dp,
    backgroundColor: Color,
    buttonColor: IconButtonColors,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    // We explicitly handle direction based on the reader viewer rather than the system direction
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = mainAxisPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                enabled = if (isRtl) enabledNext else enabledPrevious,
                onClick = if (isRtl) onNextChapter else onPreviousChapter,
                colors = buttonColor,
            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipPrevious,
                    contentDescription = stringResource(
                        if (isRtl) MR.strings.action_next_chapter else MR.strings.action_previous_chapter,
                    ),
                )
            }

            if (totalPages > 1) {
                CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(backgroundColor)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(contentAlignment = Alignment.CenterEnd) {
                            Text(text = displayCurrentPage.toString())
                            // Keep the slider still when the displayed page number gains a digit
                            Text(text = displayTotalPages.toString(), color = Color.Transparent)
                        }

                        Slider(
                            state = state,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            interactionSource = interactionSource,
                            track = { FillerPageTrack(it, totalPages, visiblePageIndices, vertical = false) },
                        )

                        Text(text = displayTotalPages.toString())
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            FilledIconButton(
                enabled = if (isRtl) enabledPrevious else enabledNext,
                onClick = if (isRtl) onPreviousChapter else onNextChapter,
                colors = buttonColor,
            ) {
                Icon(
                    imageVector = Icons.Outlined.SkipNext,
                    contentDescription = stringResource(
                        if (isRtl) MR.strings.action_previous_chapter else MR.strings.action_next_chapter,
                    ),
                )
            }
        }
    }
}

@Composable
fun VerticalChapterNavigator(
    state: SliderState,
    onNextChapter: () -> Unit,
    enabledNext: Boolean,
    onPreviousChapter: () -> Unit,
    enabledPrevious: Boolean,
    totalPages: Int,
    displayCurrentPage: Int,
    displayTotalPages: Int,
    visiblePageIndices: IntRange?,
    interactionSource: MutableInteractionSource,
    mainAxisPadding: Dp,
    backgroundColor: Color,
    buttonColor: IconButtonColors,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = mainAxisPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FilledIconButton(
            enabled = enabledPrevious,
            onClick = onPreviousChapter,
            colors = buttonColor,
        ) {
            Icon(
                imageVector = Icons.Outlined.SkipPrevious,
                contentDescription = stringResource(MR.strings.action_previous_chapter),
                modifier = Modifier.rotate(90f),
            )
        }

        if (totalPages > 1) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(backgroundColor)
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = displayCurrentPage.toString())

                VerticalSlider(
                    state = state,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp),
                    interactionSource = interactionSource,
                    track = { FillerPageTrack(it, totalPages, visiblePageIndices, vertical = true) },
                )

                Text(text = displayTotalPages.toString())
            }
        } else {
            Spacer(Modifier.weight(1f))
        }

        FilledIconButton(
            enabled = enabledNext,
            onClick = onNextChapter,
            colors = buttonColor,
        ) {
            Icon(
                imageVector = Icons.Outlined.SkipNext,
                contentDescription = stringResource(MR.strings.action_next_chapter),
                modifier = Modifier.rotate(90f),
            )
        }
    }
}

@Composable
private fun FillerPageTrack(
    state: SliderState,
    totalPages: Int,
    visiblePageIndices: IntRange?,
    vertical: Boolean,
) {
    val fillerColor = Color(0xFFFF9800)
    SliderDefaults.Track(
        sliderState = state,
        modifier = Modifier.drawWithContent {
            drawContent()
            val visible = visiblePageIndices ?: return@drawWithContent
            if (totalPages < 2) return@drawWithContent
            val length = if (vertical) size.height else size.width
            val inset = if (vertical) size.width / 2 else size.height / 2
            val radius = SliderDefaults.TickSize.toPx() / 2f
            fun drawFiller(index: Int) {
                val position = inset + (length - inset * 2) * index / (totalPages - 1)
                val center = if (vertical) {
                    Offset(size.width / 2, position)
                } else {
                    val x = if (layoutDirection == LayoutDirection.Rtl) size.width - position else position
                    Offset(x, size.height / 2)
                }
                drawCircle(fillerColor, radius, center)
            }
            for (index in 0 until visible.first) drawFiller(index)
            for (index in visible.last + 1 until totalPages) drawFiller(index)
        },
    )
}

@Preview
@Composable
private fun ChapterNavigatorPreview() {
    var currentPage by remember { mutableIntStateOf(1) }
    TachiyomiPreviewTheme {
        ChapterNavigator(
            type = ChapterNavigatorType.VERTICAL_RIGHT,
            onNextChapter = {},
            enabledNext = true,
            onPreviousChapter = {},
            enabledPrevious = true,
            currentPage = currentPage,
            totalPages = 10,
            displayCurrentPage = currentPage,
            displayTotalPages = 10,
            visiblePageIndices = 0..9,
            onPageIndexChange = { currentPage = (it + 1) },
            onPageIndexChangeFinished = {},
        )
    }
}
