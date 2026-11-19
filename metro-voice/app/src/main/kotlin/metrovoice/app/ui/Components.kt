package metrovoice.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import metrovoice.core.graph.Line

/** Content column: full width on a phone, capped and centred on an unfolded foldable or tablet. */
@Composable
fun ScreenColumn(
    scroll: Boolean = false,
    bottomBar: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(LocalPalette.current.canvas).safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxHeight().padding(horizontal = 20.dp)) {
            val body = Modifier.weight(1f).fillMaxWidth()
            Column(if (scroll) body.verticalScroll(rememberScrollState()) else body, content = content)
            bottomBar?.invoke(this)
        }
    }
}

@Composable
fun TopBar(title: String? = null, onBack: (() -> Unit)? = null, leading: @Composable RowScope.() -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            SquareIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Назад", onBack)
            Spacer(Modifier.width(12.dp))
        }
        leading()
        if (title != null) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions()
    }
}

/** Rounded-square outlined icon button, 48 dp. */
@Composable
fun SquareIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Surface(
        onClick = onClick, shape = RoundedCornerShape(Radius.control), color = p.card,
        border = BorderStroke(1.dp, p.outline), modifier = modifier.size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = description, tint = p.ink, modifier = Modifier.size(22.dp)) }
    }
}

/** The one loud action on a screen. */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = LocalPalette.current
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(Radius.tile),
        color = if (enabled) p.ink else p.outline, contentColor = p.onInk,
        modifier = modifier.heightIn(min = 56.dp),
    ) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Quiet action: outlined pill on any background. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = false,
    minHeight: Dp = 48.dp,
) {
    val p = LocalPalette.current
    Surface(
        onClick = onClick, shape = RoundedCornerShape(Radius.control),
        color = if (filled) p.card else Color.Transparent,
        border = if (filled) null else BorderStroke(1.dp, p.ink.copy(alpha = 0.18f)),
        contentColor = p.ink, modifier = modifier.heightIn(min = minHeight),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = modifier.padding(top = 24.dp, bottom = 12.dp))
}

/** White (or tinted) rounded container. Clickable when [onClick] is given. */
@Composable
fun CardSurface(
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.card,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.card)
    val inner: @Composable () -> Unit = { Column(Modifier.padding(padding), content = content) }
    if (onClick != null) Surface(onClick = onClick, shape = shape, color = color, contentColor = LocalPalette.current.ink, modifier = modifier, content = inner)
    else Surface(shape = shape, color = color, contentColor = LocalPalette.current.ink, modifier = modifier, content = inner)
}

/**
 * Line colour made safe for text: map colours are tuned for paper, not for white-on-colour labels.
 * Darkened until white text on it reaches 4.5:1.
 */
fun Line.displayColor(): Color {
    var c = when (color?.lowercase()) {
        null -> Color(0xFF1B4DDB)
        "green" -> Color(0xFF1E9E4A)
        "red" -> Color(0xFFE02424)
        else -> runCatching { Color(android.graphics.Color.parseColor(color)) }.getOrDefault(Color(0xFF1B4DDB))
    }
    while (c.luminance() > 0.183f) c = Color(c.red * 0.96f, c.green * 0.96f, c.blue * 0.96f)
    return c
}

/** Line number in the line colour, as on metro maps. Grows with the system font size. */
@Composable
fun LineBadge(line: Line, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    Box(
        modifier.defaultMinSize(size, size).clip(CircleShape).background(line.displayColor()).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(line.id.take(2), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Small status label on a coloured pill: "вы здесь", "далее". [icon] shows the direction of travel. */
@Composable
fun Tag(text: String, color: Color, icon: ImageVector? = null, iconDescription: String? = null) {
    Surface(shape = RoundedCornerShape(50), color = color, modifier = Modifier.padding(vertical = 4.dp)) {
        Row(Modifier.padding(start = if (icon != null) 6.dp else 10.dp, end = 10.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Icon(icon, contentDescription = iconDescription, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/**
 * Front of a train with sound waves: the app's one illustration, drawn in code so it follows the theme
 * and the line colour. A slot for richer artwork per transport mode later ([mode]).
 */
@Composable
fun ModeIllustration(mode: String, accent: Color, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val body = if (p.dark) Color(0xFF2B2B2B) else Color.White
    val glass = if (p.dark) Color(0xFF0E0E0E) else p.ink
    val rail = p.ink.copy(alpha = 0.25f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bw = w * 0.56f
        val bh = h * 0.74f
        val left = w * 0.10f
        val top = h * 0.10f
        val r = CornerRadius(bw * 0.20f)
        // Tram and bus get a flatter roof; the train is taller. Enough to tell them apart at a glance.
        val roof = if (mode == "subway") 0f else bh * 0.10f
        drawRoundRect(body, Offset(left, top + roof), Size(bw, bh - roof), r)
        drawRoundRect(p.ink.copy(alpha = 0.10f), Offset(left, top + roof), Size(bw, bh - roof), r, style = Stroke(w * 0.012f))
        drawRoundRect(glass, Offset(left + bw * 0.13f, top + roof + bh * 0.12f), Size(bw * 0.74f, bh * 0.34f), CornerRadius(bw * 0.10f))
        drawRect(accent, Offset(left, top + bh * 0.58f), Size(bw, bh * 0.11f))
        val lampY = top + bh * 0.83f
        drawCircle(Color(0xFFFFD66B), bw * 0.065f, Offset(left + bw * 0.22f, lampY))
        drawCircle(Color(0xFFFFD66B), bw * 0.065f, Offset(left + bw * 0.78f, lampY))
        val railY = top + bh + h * 0.07f
        drawLine(rail, Offset(left - w * 0.04f, railY), Offset(left + bw + w * 0.04f, railY), w * 0.02f, StrokeCap.Round)
        // Sound waves: the announcements.
        val cx = left + bw + w * 0.02f
        val cy = top + bh * 0.30f
        for (i in 1..3) {
            val rad = w * (0.05f + 0.06f * i)
            drawArc(
                p.ink.copy(alpha = 0.55f - 0.13f * i), startAngle = -40f, sweepAngle = 80f, useCenter = false,
                topLeft = Offset(cx - rad, cy - rad), size = Size(rad * 2, rad * 2),
                style = Stroke(w * 0.022f, cap = StrokeCap.Round),
            )
        }
    }
}

fun modeTitle(mode: String): String = when (mode) {
    "subway" -> "Метро"
    "tram" -> "Трамвай"
    "bus" -> "Автобус"
    "light_rail" -> "Городская электричка"
    else -> "Линии"
}

fun stationsCount(n: Int): String = "$n " + when {
    n % 100 in 11..14 -> "станций"
    n % 10 == 1 -> "станция"
    n % 10 in 2..4 -> "станции"
    else -> "станций"
}
