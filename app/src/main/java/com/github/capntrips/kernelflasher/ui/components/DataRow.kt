package com.github.capntrips.kernelflasher.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun DataRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    valueStyle: TextStyle = MaterialTheme.typography.titleSmall
) {
    Row {
        Text(
            modifier = Modifier.alignByBaseline(),
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(Modifier.width(8.dp))
        SelectionContainer(Modifier.alignByBaseline()) {
            Text(
                modifier = Modifier.alignByBaseline(),
                text = value,
                color = valueColor,
                style = valueStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
