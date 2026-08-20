package io.legado.app.ui.widget.components.menuItem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcon

/**
 * Discovery-only overload for long-press + marquee source rows.
 * The upstream RoundDropdownMenuItem implementation remains untouched.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoundDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    marquee: Boolean,
    isSelected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean = true,
    contentPadding: PaddingValues = MenuDefaults.DropdownMenuItemContentPadding,
    interactionSource: MutableInteractionSource? = null,
) {
    val interaction = interactionSource ?: remember { MutableInteractionSource() }
    val scheme = LegadoTheme.colorScheme
    val contentColor = when {
        color != Color.Unspecified -> color
        isSelected -> scheme.primary
        else -> scheme.onSurface
    }

    Surface(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = androidx.compose.material3.MaterialTheme.shapes.small,
        color = scheme.surface,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier
                .padding(contentPadding)
                .heightIn(min = 48.dp)
                .widthIn(min = 120.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(Modifier.width(12.dp))
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                Text(
                    text = text,
                    modifier = Modifier
                        .widthIn(max = 200.dp)
                        .then(if (marquee) Modifier.basicMarquee() else Modifier),
                    maxLines = 1,
                    style = LegadoTheme.typography.labelLargeEmphasized,
                    color = contentColor,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (trailingIcon != null) {
                trailingIcon()
            } else {
                AppIcon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isSelected) contentColor else Color.Transparent,
                )
            }
        }
    }
}
