package com.banketlux.ui.components

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.banketlux.ui.theme.BanketInfo
import com.banketlux.ui.theme.BanketInfoContainer
import com.banketlux.ui.theme.BanketOnWarningContainer
import com.banketlux.ui.theme.BanketSuccess
import com.banketlux.ui.theme.BanketSuccessContainer
import com.banketlux.ui.theme.BanketSurfaceMuted
import com.banketlux.ui.theme.BanketTextMuted
import com.banketlux.ui.theme.BanketWarningContainer

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
            .heightIn(min = 220.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
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

@Composable
fun BanketAlertPanel(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: BanketTone = BanketTone.Info
) {
    val style = tone.alertStyle()
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = style.containerColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = style.contentDescription,
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

@Composable
private fun BanketTone.alertStyle(): ToneStyle {
    return when (this) {
        BanketTone.Info -> ToneStyle(
            containerColor = BanketInfoContainer,
            contentColor = BanketInfo,
            icon = Icons.Default.Info,
            contentDescription = "Informacija"
        )
        BanketTone.Success -> ToneStyle(
            containerColor = BanketSuccessContainer,
            contentColor = BanketSuccess,
            icon = Icons.Default.CheckCircle,
            contentDescription = "Uspešno"
        )
        BanketTone.Warning -> ToneStyle(
            containerColor = BanketWarningContainer,
            contentColor = BanketOnWarningContainer,
            icon = Icons.Default.Warning,
            contentDescription = "Upozorenje"
        )
        BanketTone.Error -> ToneStyle(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
            icon = Icons.Default.Error,
            contentDescription = "Greška"
        )
        BanketTone.Neutral -> ToneStyle(
            containerColor = BanketSurfaceMuted,
            contentColor = BanketTextMuted,
            icon = Icons.Default.Info,
            contentDescription = "Obaveštenje"
        )
    }
}

private data class ToneStyle(
    val containerColor: Color,
    val contentColor: Color,
    val icon: ImageVector,
    val contentDescription: String
)
