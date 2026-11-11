package com.yehor.finora.ui.operations

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yehor.finora.R
import com.yehor.finora.designsystem.component.button.PrimaryButton
import com.yehor.finora.designsystem.motion.FinoraMotion
import com.yehor.finora.designsystem.motion.rememberReduceMotion
import com.yehor.finora.designsystem.theme.finance
import com.yehor.finora.designsystem.theme.hairline
import kotlinx.coroutines.launch

// The keypad screen and the details shelf that rises over it, shared by every mode of the editor.

/** Sizes of the keypad screen for the height it has. */
internal data class EditorLayout(
    val keyHeight: Dp,
    val showQuickAmounts: Boolean,
    val amountMaxFontSize: TextUnit,
    val amountTopPadding: Dp,
    val amountSpacing: Dp,
)

/**
 * Tall screens get the full layout; shorter ones shrink the keys (never below the 48dp touch target), the amount
 * and its spacing, and the shortest drop the quick-amount pills so Next always fits (e.g. 360×640 with 3-button nav).
 */
internal fun editorLayoutFor(available: Dp): EditorLayout = when {
    available >= 660.dp -> EditorLayout(58.dp, true, 56.sp, 28.dp, 14.dp)
    available >= 600.dp -> EditorLayout(52.dp, true, 48.sp, 16.dp, 10.dp)
    else -> EditorLayout(48.dp, false, 44.sp, 8.dp, 8.dp)
}

private val ShelfShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private const val SHELF_SCRIM_ALPHA = 0.45f
private const val AMOUNT_SHRINK = 0.85f
private val AmountLift = 24.dp

/** Room kept above the shelf for the shrunken top bar, amount and account pill. */
private val ShelfTopClearance = 190.dp
/** Top spacer + 48dp top bar: the area the scrim leaves uncovered. */
private val TopBarClearance = 56.dp
private val DragDismissDistance = 80.dp
private const val DRAG_DISMISS_VELOCITY = 1200f

/**
 * Stage [EditorStage.AMOUNT]: top bar, [amount] block, [keypad] and the Next CTA.
 * Stage [EditorStage.DETAILS]: the shelf slides up over the keypad behind a scrim while the amount block shrinks
 * to 85% and lifts by 24dp so it stays visible. Scrim tap, handle tap/drag-down and Back return to the keypad;
 * nothing typed is lost because every value lives in the view models.
 */
@Composable
internal fun EditorScaffold(
    stage: EditorStage,
    onStageChange: (EditorStage) -> Unit,
    topBar: @Composable () -> Unit,
    amount: @Composable ColumnScope.(EditorLayout) -> Unit,
    keypad: @Composable ColumnScope.(EditorLayout) -> Unit,
    shelfKindLabel: String,
    shelfScroll: ScrollState,
    shelfContent: @Composable ColumnScope.() -> Unit,
    shelfFooter: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable () -> Unit = {},
) {
    val open = stage == EditorStage.DETAILS
    val reduceMotion = rememberReduceMotion()
    val focusManager = LocalFocusManager.current
    // Leaving the shelf hides the keyboard of the note field.
    LaunchedEffect(open) { if (!open) focusManager.clearFocus() }
    BackHandler(enabled = open) { onStageChange(EditorStage.AMOUNT) }

    val progress by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = if (reduceMotion) snap() else FinoraMotion.sheet,
        label = "shelfProgress",
    )

    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val shelfMaxHeight = (maxHeight - statusTop - ShelfTopClearance).coerceAtLeast(240.dp)
            val layout = editorLayoutFor(maxHeight - statusTop - navBottom)

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                topBar()
                // The amount takes what is left after the keypad and Next, so on short screens it is the amount
                // area that gives way, never the CTA.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = layout.amountTopPadding)
                        .graphicsLayer {
                            val scale = 1f - (1f - AMOUNT_SHRINK) * progress
                            scaleX = scale
                            scaleY = scale
                            translationY = -AmountLift.toPx() * progress
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(layout.amountSpacing),
                ) { amount(layout) }
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { keypad(layout) }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(stringResource(R.string.editor_next), onClick = { onStageChange(EditorStage.DETAILS) })
                Spacer(Modifier.height(16.dp))
            }

            AnimatedVisibility(
                visible = open,
                enter = if (reduceMotion) EnterTransition.None else fadeIn(tween(300)),
                exit = if (reduceMotion) ExitTransition.None else fadeOut(tween(250)),
            ) {
                // The scrim starts under the top bar so ✕, the kind pill and the ⋯ menu stay usable with the shelf up.
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(top = statusTop + TopBarClearance)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = SHELF_SCRIM_ALPHA))
                        .clickable(interactionSource = null, indication = null) { onStageChange(EditorStage.AMOUNT) },
                )
            }

            AnimatedVisibility(
                visible = open,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = if (reduceMotion) fadeIn(tween(150)) else slideInVertically(tween(380)) { it },
                exit = if (reduceMotion) fadeOut(tween(150)) else slideOutVertically(tween(300)) { it },
            ) {
                DetailsShelf(
                    maxHeight = shelfMaxHeight,
                    kindLabel = shelfKindLabel,
                    scroll = shelfScroll,
                    onDismiss = { onStageChange(EditorStage.AMOUNT) },
                    content = shelfContent,
                    footer = shelfFooter,
                )
            }

            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { overlay() }
        }
    }
}

@Composable
private fun DetailsShelf(
    maxHeight: Dp,
    kindLabel: String,
    scroll: ScrollState,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val dismissDistance = with(density) { DragDismissDistance.toPx() }
    val dragState = rememberDraggableState { delta ->
        scope.launch { dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f)) }
    }
    val handleLabel = stringResource(R.string.editor_details)

    Surface(
        shape = ShelfShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = hairline(MaterialTheme.finance.isDark),
        shadowElevation = 16.dp,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .graphicsLayer { translationY = dragOffset.value },
    ) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 18.dp),
        ) {
            // Handle + header: tap or drag down to drop the shelf back to the keypad.
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = dragState,
                        orientation = Orientation.Vertical,
                        onDragStopped = { velocity ->
                            if (dragOffset.value > dismissDistance || velocity > DRAG_DISMISS_VELOCITY) {
                                onDismiss()
                            } else {
                                dragOffset.animateTo(0f)
                            }
                        },
                    ),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clickable(role = Role.Button, onClickLabel = handleLabel, onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)),
                    )
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.editor_details),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(kindLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scroll)
                    .padding(top = 8.dp),
                content = content,
            )
            Column(Modifier.padding(top = 8.dp, bottom = 16.dp), content = footer)
        }
    }
}
