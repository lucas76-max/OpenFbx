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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Motif décoratif du haut d'écran : une fleur de pétales translucides.
 *
 * Purement graphique (aucune interaction), très désaturé pour ne jamais gêner la
 * lecture des textes posés dessus.
 */
@Composable
fun PetalBackdrop(
    modifier: Modifier = Modifier,
    height: Dp = 280.dp,
) {
    val petal = MaterialTheme.colorScheme.primary
    val halo = MaterialTheme.colorScheme.tertiary

    Canvas(modifier.fillMaxWidth().height(height)) {
        drawFlower(
            center = Offset(size.width * 0.80f, size.height * 0.16f),
            radius = size.minDimension * 0.20f,
            petals = 9,
            color = petal.copy(alpha = 0.10f),
        )
        drawCircle(
            color = halo.copy(alpha = 0.05f),
            radius = size.minDimension * 0.34f,
            center = Offset(size.width * 0.80f, size.height * 0.16f),
        )
        drawFlower(
            center = Offset(size.width * 0.12f, size.height * 0.58f),
            radius = size.minDimension * 0.11f,
            petals = 7,
            color = halo.copy(alpha = 0.06f),
        )
    }
}

private fun DrawScope.drawFlower(
    center: Offset,
    radius: Float,
    petals: Int,
    color: Color,
) {
    val petalRadius = radius * 0.46f
    val distance = radius - petalRadius
    repeat(petals) { index ->
        val angle = (index * 2.0 * Math.PI / petals).toFloat()
        drawCircle(
            color = color,
            radius = petalRadius,
            center = Offset(center.x + cos(angle) * distance, center.y + sin(angle) * distance),
        )
    }
    drawCircle(color = color, radius = petalRadius * 1.15f, center = center)
}

/**
 * Pastille dentelée (fleur / engrenage) contenant une icône : sert d'emblème aux
 * en-têtes de section mis en avant.
 */
@Composable
fun ScallopedBadge(
    icon: ImageVector = Icons.Filled.Palette,
    size: Dp = 60.dp,
    modifier: Modifier = Modifier,
) {
    val petals = MaterialTheme.colorScheme.primaryContainer
    val core = MaterialTheme.colorScheme.surfaceContainerHighest
    val content = MaterialTheme.colorScheme.onPrimaryContainer

    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = this.size.minDimension / 2f
            val scallop = radius * 0.30f
            val distance = radius - scallop * 1.7f
            repeat(12) { index ->
                val angle = (index * 2.0 * Math.PI / 12).toFloat()
                drawCircle(
                    color = petals,
                    radius = scallop,
                    center = Offset(
                        x = this.size.width / 2f + cos(angle) * distance,
                        y = this.size.height / 2f + sin(angle) * distance,
                    ),
                )
            }
            drawCircle(color = core, radius = radius * 0.66f)
        }
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(size * 0.42f),
        )
    }
}
