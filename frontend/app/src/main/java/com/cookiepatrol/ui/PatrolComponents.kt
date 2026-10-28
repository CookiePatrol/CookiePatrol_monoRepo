package com.cookiepatrol.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class PatrolSymbol { SHIELD, BELL, MESSAGE, DOWNLOAD, SCREEN, INFO, WARNING }

@Composable
fun PatrolIcon(symbol: PatrolSymbol, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier.size(48.dp).background(tint.copy(alpha = 0.10f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        // Decorative vector marks; the adjacent text carries the accessible label.
        Canvas(Modifier.size(28.dp)) {
            val unit = size.minDimension / 24f
            fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
            fun line(x: Float, y: Float, endX: Float, endY: Float) {
                drawLine(tint, point(x, y), point(endX, endY), 1.8f * unit, StrokeCap.Round)
            }
            fun outline(vararg coordinates: Pair<Float, Float>, closed: Boolean = false) {
                val path = Path().apply {
                    coordinates.forEachIndexed { index, (x, y) ->
                        if (index == 0) moveTo(x * unit, y * unit) else lineTo(x * unit, y * unit)
                    }
                    if (closed) close()
                }
                drawPath(path, tint, style = Stroke(1.8f * unit, cap = StrokeCap.Round))
            }
            when (symbol) {
                PatrolSymbol.SHIELD -> {
                    outline(12f to 2f, 20f to 5f, 19f to 14f, 16f to 19f, 12f to 22f,
                        8f to 19f, 5f to 14f, 4f to 5f, closed = true)
                    outline(8f to 12f, 11f to 15f, 16f to 9f)
                }
                PatrolSymbol.BELL -> {
                    val path = Path().apply {
                        moveTo(5 * unit, 17 * unit)
                        lineTo(7 * unit, 14 * unit)
                        lineTo(7 * unit, 9 * unit)
                        cubicTo(7 * unit, 2 * unit, 17 * unit, 2 * unit, 17 * unit, 9 * unit)
                        lineTo(17 * unit, 14 * unit)
                        lineTo(19 * unit, 17 * unit)
                        close()
                    }
                    drawPath(path, tint, style = Stroke(1.8f * unit))
                    line(10f, 21f, 14f, 21f)
                }
                PatrolSymbol.MESSAGE -> {
                    outline(3f to 4f, 21f to 4f, 21f to 17f, 10f to 17f,
                        5f to 21f, 5f to 17f, 3f to 17f, closed = true)
                    line(7f, 9f, 17f, 9f)
                    line(7f, 13f, 13f, 13f)
                }
                PatrolSymbol.DOWNLOAD -> {
                    line(12f, 3f, 12f, 15f)
                    outline(7f to 10f, 12f to 15f, 17f to 10f)
                    outline(4f to 16f, 4f to 21f, 20f to 21f, 20f to 16f)
                }
                PatrolSymbol.SCREEN -> {
                    outline(5f to 2f, 19f to 2f, 19f to 22f, 5f to 22f, closed = true)
                    line(10f, 18f, 14f, 18f)
                }
                PatrolSymbol.INFO -> {
                    drawCircle(tint, 9 * unit, style = Stroke(1.8f * unit))
                    drawCircle(tint, 1 * unit, point(12f, 7f))
                    line(12f, 11f, 12f, 17f)
                }
                PatrolSymbol.WARNING -> {
                    outline(12f to 3f, 22f to 21f, 2f to 21f, closed = true)
                    line(12f, 9f, 12f, 14f)
                    drawCircle(tint, 1 * unit, point(12f, 18f))
                }
            }
        }
    }
}

@Composable
fun PatrolCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) { Column(content = content) }
}

@Composable
fun PatrolButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 72.dp),
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = containerColor, contentColor = contentColor,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
    ) { Text(label, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold) }
}
