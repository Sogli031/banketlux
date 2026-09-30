package com.banketlux.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.banketlux.domain.model.BookingStatus
import com.banketlux.ui.theme.BanketNavy
import com.banketlux.ui.theme.BanketInfo
import com.banketlux.ui.theme.BanketInfoContainer
import com.banketlux.ui.theme.BanketOnWarningContainer
import com.banketlux.ui.theme.BanketSurfaceMuted
import com.banketlux.ui.theme.BanketSuccess
import com.banketlux.ui.theme.BanketSuccessContainer
import com.banketlux.ui.theme.BanketWarningContainer

@Composable
fun BanketStatusPill(
    status: BookingStatus,
    modifier: Modifier = Modifier
) {
    val presentation = when (status) {
        BookingStatus.INQUIRY -> StatusPresentation(
            label = "Upit",
            icon = Icons.AutoMirrored.Filled.Help,
            containerColor = BanketInfoContainer,
            contentColor = BanketInfo
        )
        BookingStatus.CONFIRMED -> StatusPresentation(
            label = "Potvrđeno",
            icon = Icons.Default.CheckCircle,
            containerColor = BanketSuccessContainer,
            contentColor = BanketSuccess
        )
        BookingStatus.COMPLETED -> StatusPresentation(
            label = "Završeno",
            icon = Icons.Default.DoneAll,
            containerColor = BanketSurfaceMuted,
            contentColor = BanketNavy
        )
        BookingStatus.CANCELLED -> StatusPresentation(
            label = "Otkazano",
            icon = Icons.Default.Block,
            containerColor = BanketWarningContainer,
            contentColor = BanketOnWarningContainer
        )
    }

    Surface(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Status: ${presentation.label}"
        },
        shape = MaterialTheme.shapes.extraLarge,
        color = presentation.containerColor,
        contentColor = presentation.contentColor
    ) {
        Row(
            modifier = Modifier
                .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = presentation.icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = presentation.label,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

private data class StatusPresentation(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color
)
