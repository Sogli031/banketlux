package com.banketlux.ui.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.banketlux.ui.theme.BanketAppear
import com.banketlux.ui.theme.BanketMotion
import com.banketlux.ui.theme.extendedColors

enum class BanketTone { Info, Success, Warning, Error, Neutral }

@Composable
fun BanketEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 224.dp),
        contentAlignment = Alignment.Center
    ) {
        // Poruka blago izlazi odozdo (providnost + pomeranje) umesto da iskoči.
        BanketAppear(
            enter = fadeIn(BanketMotion.effects()) +
                slideInVertically(BanketMotion.spatial()) { it / 6 }
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() }
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                if (actionLabel != null && onAction != null) {
                    Button(
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                        onClick = onAction
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

/**
 * Ton se razlikuje i ikonicom i naslovom, ne samo bojom. Ikonica je ukrasna
 * (bez opisa) jer naslov već kaže šta je u pitanju — TalkBack ne čita dvaput.
 * Poruka se otvara (visina + providnost) umesto da naglo pomeri sadržaj ispod.
 */
@Composable
fun BanketAlertPanel(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: BanketTone = BanketTone.Info
) {
    val style = tone.alertStyle()
    BanketAppear(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = style.containerColor
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = style.contentColor,
                    modifier = Modifier.size(24.dp)
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = style.contentColor
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun BanketTone.alertStyle(): ToneStyle {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    return when (this) {
        BanketTone.Info -> ToneStyle(
            containerColor = extended.info.container,
            contentColor = extended.info.onContainer,
            icon = Icons.Default.Info
        )
        BanketTone.Success -> ToneStyle(
            containerColor = extended.success.container,
            contentColor = extended.success.onContainer,
            icon = Icons.Default.CheckCircle
        )
        BanketTone.Warning -> ToneStyle(
            containerColor = extended.warning.container,
            contentColor = extended.warning.onContainer,
            icon = Icons.Default.Warning
        )
        BanketTone.Error -> ToneStyle(
            containerColor = colors.errorContainer,
            contentColor = colors.onErrorContainer,
            icon = Icons.Default.Error
        )
        BanketTone.Neutral -> ToneStyle(
            containerColor = colors.surfaceContainerHigh,
            contentColor = colors.onSurfaceVariant,
            icon = Icons.Default.Info
        )
    }
}

private data class ToneStyle(
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector
)
