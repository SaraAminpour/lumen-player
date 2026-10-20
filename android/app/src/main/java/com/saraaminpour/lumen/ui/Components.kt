package com.saraaminpour.lumen.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Pill button with optional icon; primary = accent gradient (PillButton.qml). */
@Composable
fun PillButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    checked: Boolean = false,
    height: Dp = 40.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f), label = "pill")
    val shape = RoundedCornerShape(50)
    val fg = if (primary) accent.onAccent else if (checked) accent.light else C.text
    Row(
        modifier
            .scale(s)
            .height(height)
            .clip(shape)
            .then(
                if (primary) Modifier.background(accent.brush)
                else Modifier.background(if (checked) accent.a.copy(alpha = 0.22f) else C.card)
                    .border(1.dp, if (checked) accent.light.copy(alpha = 0.6f) else C.glassBorder, shape)
            )
            .clickable(src, null, enabled = enabled, onClick = onClick)
            .padding(horizontal = if (text.isEmpty()) 10.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg.copy(alpha = if (enabled) 1f else 0.4f), modifier = Modifier.size(18.dp))
            if (text.isNotEmpty()) Spacer(Modifier.width(8.dp))
        }
        if (text.isNotEmpty()) Text(
            text, color = fg.copy(alpha = if (enabled) 1f else 0.4f), fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small selectable chip (Chip.qml). */
@Composable
fun Chip(text: String, checked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) =
    PillButton(text, modifier, checked = checked, height = 34.dp, onClick = onClick)

/** Round icon button used on the player overlay. */
@Composable
fun IconBtn(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = C.text,
    active: Boolean = false,
    enabled: Boolean = true,
    desc: String? = null,
    onClick: () -> Unit,
) {
    val accent = LocalAccent.current
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.86f else 1f, spring(dampingRatio = 0.45f), label = "ib")
    Box(
        modifier.size(size).scale(s).clip(CircleShape)
            .background(if (active) accent.a.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(src, null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, desc, tint = if (active) accent.light else tint.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(size * 0.5f))
    }
}

/** Card with an uppercase header (Section.qml). */
@Composable
fun Section(title: String, icon: ImageVector, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val accent = LocalAccent.current
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.card)
            .border(1.dp, C.glassBorder, RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = accent.light, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(title.uppercase(), color = C.textDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        }
        content()
    }
}

@Composable
fun LSwitch(text: String, checked: Boolean, hint: String = "", onChange: (Boolean) -> Unit) {
    val accent = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(text, color = C.text, fontSize = 14.sp)
            if (hint.isNotEmpty()) Text(hint, color = C.textFaint, fontSize = 12.sp)
        }
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent.a, checkedThumbColor = Color.White,
                uncheckedTrackColor = C.card, uncheckedBorderColor = C.glassBorder, uncheckedThumbColor = C.textDim,
            ),
        )
    }
}

@Composable
fun LSlider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {},
) {
    val accent = LocalAccent.current
    Slider(
        value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range,
        onValueChangeFinished = onDone, modifier = modifier.fillMaxWidth().height(32.dp),
        colors = SliderDefaults.colors(
            thumbColor = Color.White, activeTrackColor = accent.light, inactiveTrackColor = C.hover,
        ),
    )
}

@Composable
fun Label(text: String, color: Color = C.textDim) = Text(text, color = color, fontSize = 12.sp)

/** On-screen pill message, like the desktop Toast.qml. */
class OsdState {
    var text by mutableStateOf("")
    var icon by mutableStateOf<ImageVector?>(null)
    var serial by mutableIntStateOf(0)
    var shown by mutableStateOf(false)
    fun show(t: String, ic: ImageVector? = null) { text = t; icon = ic; serial++; shown = true }
}

@Composable
fun Osd(state: OsdState, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    LaunchedEffect(state.serial) { if (state.shown) { delay(1500); state.shown = false } }
    AnimatedVisibility(
        state.shown, modifier,
        enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), initialScale = 0.85f),
        exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.9f),
    ) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(C.glass).border(1.dp, C.glassBorder, RoundedCornerShape(50))
                .padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.icon?.let { Icon(it, null, tint = accent.light, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp)) }
            Text(state.text, color = C.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Thin gradient progress bar. */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val accent = LocalAccent.current
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(C.hover)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0.02f, 1f)).height(6.dp).clip(RoundedCornerShape(3.dp)).background(accent.brush))
    }
}
