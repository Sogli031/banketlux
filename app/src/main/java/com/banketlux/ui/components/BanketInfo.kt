package com.banketlux.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.banketlux.ui.theme.BanketInfo
import com.banketlux.ui.theme.BanketInfoContainer
import com.banketlux.ui.theme.BanketOnWarningContainer
import com.banketlux.ui.theme.BanketSuccess
import com.banketlux.ui.theme.BanketSuccessContainer
import com.banketlux.ui.theme.BanketWarningContainer

@Composable
fun BanketInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueEmphasis: Boolean = false
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = if (valueEmphasis) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 48.dp)
        )
    }
}

@Composable
fun BanketMetricBlock(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: BanketTone = BanketTone.Neutral
) {
    val colors = metricColors(tone)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = colors.containerColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = colors.labelColor
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.valueColor
            )
        }
    }
}

@Composable
private fun metricColors(tone: BanketTone): MetricColors {
    return when (tone) {
        BanketTone.Info -> MetricColors(
            containerColor = BanketInfoContainer,
            labelColor = BanketInfo,
            valueColor = BanketInfo
        )
        BanketTone.Success -> MetricColors(
            containerColor = BanketSuccessContainer,
            labelColor = BanketSuccess,
            valueColor = BanketSuccess
        )
        BanketTone.Warning -> MetricColors(
            containerColor = BanketWarningContainer,
            labelColor = BanketOnWarningContainer,
            valueColor = BanketOnWarningContainer
        )
        BanketTone.Error -> MetricColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            labelColor = MaterialTheme.colorScheme.onErrorContainer,
            valueColor = MaterialTheme.colorScheme.onErrorContainer
        )
        BanketTone.Neutral -> MetricColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            valueColor = MaterialTheme.colorScheme.onSurface
        )
    }
}

private data class MetricColors(
    val containerColor: Color,
    val labelColor: Color,
    val valueColor: Color
)
