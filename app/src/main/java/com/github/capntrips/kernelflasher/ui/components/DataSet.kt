package com.github.capntrips.kernelflasher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

@Composable
fun DataSet(
    label: String,
    labelColor: Color = Color.Unspecified,
    labelStyle: TextStyle = MaterialTheme.typography.labelMedium,
    content: @Composable (ColumnScope.() -> Unit)
) {
    Text(
        text = label,
        color = labelColor,
        style = labelStyle
    )
    Column(Modifier.padding(start = 16.dp)) {
        content()
    }
}
