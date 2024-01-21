package com.github.capntrips.kernelflasher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun RowScope.DataValue(
    value: String,
    color: Color = Color.Unspecified,
    style: TextStyle = MaterialTheme.typography.titleSmall,
    clickable: Boolean = false,
) {
    SelectionContainer(Modifier.alignByBaseline()) {
        var clicked by remember { mutableStateOf(false) }
        val modifier = if (clickable) {
            Modifier
                .clickable { clicked = !clicked }
                .alignByBaseline()
        } else {
            Modifier
                .alignByBaseline()
        }
        Text(
            modifier = modifier,
            text = value,
            color = color,
            style = style,
            maxLines = if (clicked) Int.MAX_VALUE else 1,
            overflow = if (clicked) TextOverflow.Visible else TextOverflow.Ellipsis
        )
    }
}
