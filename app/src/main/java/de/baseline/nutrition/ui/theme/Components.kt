package de.baseline.nutrition.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(BaselineShapes.glass)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(BaselineSpacing.large),
        content = content,
    )
}

@Composable
fun BaselineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        shape = BaselineShapes.input,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
    )
}

@Composable
fun BaselineStatus(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
fun BaselineSkeleton(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(BaselineShapes.input)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .defaultMinSize(minHeight = 56.dp),
    )
}

@Composable
fun BaselineDialog(
    title: String,
    text: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        },
    )
}
