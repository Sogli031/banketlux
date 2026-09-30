package com.banketlux.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.banketlux.R
import com.banketlux.ui.theme.BanketGold
import com.banketlux.ui.theme.BanketLabelCaps

/**
 * Zaglavlje ekrana. Ako je [showLogo] uključen i nema ikonice za nazad,
 * levo stoji mali okrugli logo — brend diskretno, bez zauzimanja prostora.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BanketTopBar(
    title: String,
    subtitle: String? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    showLogo: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            if (subtitle == null) {
                Text(title, style = MaterialTheme.typography.titleLarge)
            } else {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            when {
                navigationIcon != null -> navigationIcon()
                showLogo -> BanketLogoMark()
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.primary,
            actionIconContentColor = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
private fun BanketLogoMark() {
    Box(
        modifier = Modifier.padding(start = 16.dp, end = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.logo),
            contentDescription = "Banket Lux",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(1.dp, BanketGold.copy(alpha = 0.4f), CircleShape)
        )
    }
}

/** Zlatni naslov sekcije u sitnim verzalnim slovima. */
@Composable
fun BanketSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = BanketLabelCaps,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

/**
 * Sekcija bez kartice: zlatni naslov, opcioni podnaslov, pa sadržaj.
 * Kartice-u-karticama iz stare verzije zamenjene su ravnim grupama.
 */
@Composable
fun BanketSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.padding(vertical = 4.dp)) {
        BanketSectionLabel(title)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

/** Tamna kartica sa tankim okvirom — osnovna površina liste. */
@Composable
fun BanketCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    val color = MaterialTheme.colorScheme.surfaceContainerLow
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = color,
            border = border
        ) {
            Column(content = content)
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = color,
            border = border
        ) {
            Column(content = content)
        }
    }
}

/** Sitan čip za količinu / trajanje. */
@Composable
fun BanketChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            text = text,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
