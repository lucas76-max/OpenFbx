package fr.openfbx.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Halo de couleur en haut de chaque page.
 *
 * La teinte Material choisie émane du centre-gauche, puis se diffuse vers la
 * droite et le bas jusqu'à se fondre dans la couleur de fond (noir, blanc ou
 * OLED). Aucun bord franc : le dégradé est volontairement très large.
 */
@Composable
fun TopGlow(
    modifier: Modifier = Modifier,
    height: Dp = 340.dp,
) {
    val glow = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background

    Canvas(modifier.fillMaxWidth().height(height)) {
        val center = Offset(size.width * 0.34f, size.height * 0.02f)
        val radius = size.width * 1.15f

        drawRect(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to glow.copy(alpha = 0.42f),
                    0.35f to glow.copy(alpha = 0.24f),
                    0.68f to glow.copy(alpha = 0.08f),
                    1.00f to glow.copy(alpha = 0.00f),
                ),
                center = center,
                radius = radius,
            ),
        )

        // Fondu vertical vers le fond : évite toute ligne de démarcation.
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to background.copy(alpha = 0.00f),
                    0.55f to background.copy(alpha = 0.35f),
                    1.00f to background,
                ),
            ),
        )
    }
}

/**
 * Pastille « fleur » : huit lobes arrondis et symétriques, sans aucun angle
 * droit, remplie d'une couleur Material légèrement plus claire que le fond.
 */
@Composable
fun FlowerBadge(
    icon: ImageVector = Icons.Filled.Palette,
    size: Dp = 60.dp,
    modifier: Modifier = Modifier,
) {
    val petals = MaterialTheme.colorScheme.primaryContainer
    val core = MaterialTheme.colorScheme.primaryContainer
    val content = MaterialTheme.colorScheme.onPrimaryContainer

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            // Huit lobes qui se chevauchent : la silhouette reste ondulée et douce.
            val lobeRadius = radius * 0.42f
            val distance = radius * 0.55f
            repeat(8) { index ->
                val angle = (index * 2.0 * PI / 8.0).toFloat() - (PI / 8.0).toFloat()
                drawCircle(
                    color = petals,
                    radius = lobeRadius,
                    center = Offset(
                        x = this.size.width / 2f + cos(angle) * distance,
                        y = this.size.height / 2f + sin(angle) * distance,
                    ),
                )
            }
            drawCircle(color = core, radius = radius * 0.58f)
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(size * 0.40f),
        )
    }
}

/** Halo décoratif posé derrière une icône, sans contour. */
@Composable
fun SoftHalo(color: Color, modifier: Modifier = Modifier, size: Dp = 120.dp) {
    Canvas(modifier.size(size)) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0f)),
            ),
            radius = this.size.minDimension / 2f,
        )
    }
}

private fun DrawScope.drawFlower(
    center: Offset,
    radius: Float,
    petals: Int,
    color: Color,
) {
    val petalRadius = radius * 0.44f
    val distance = radius - petalRadius
    repeat(petals) { index ->
        val angle = (index * 2.0 * PI / petals).toFloat()
        drawCircle(
            color = color,
            radius = petalRadius,
            center = Offset(center.x + cos(angle) * distance, center.y + sin(angle) * distance),
        )
    }
}
