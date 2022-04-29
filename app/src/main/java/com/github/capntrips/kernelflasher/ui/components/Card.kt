package com.github.capntrips.kernelflasher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// TODO: Remove when card is supported in material3: https://m3.material.io/components/cards/implementation/android
@Composable
fun Card(
    shape: Shape = RoundedCornerShape(4.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    border: BorderStroke? = null,
    tonalElevation: Dp = 2.dp,
    shadowElevation: Dp = 1.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = shape,
        color = backgroundColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        shadowElevation = shadowElevation,
        border = border
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp, (13.788).dp, 18.dp, 18.dp),
            content = content
        )
    }
}
