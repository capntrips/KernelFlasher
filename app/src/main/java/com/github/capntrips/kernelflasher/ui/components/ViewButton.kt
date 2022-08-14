package com.github.capntrips.kernelflasher.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.github.capntrips.kernelflasher.R

@Composable
fun ViewButton(
    onClick: () -> Unit
) {
    TextButton(
        modifier = Modifier.padding(0.dp),
        shape = RoundedCornerShape(4.0.dp),
        contentPadding = PaddingValues(
            horizontal = ButtonDefaults.ContentPadding.calculateLeftPadding(LayoutDirection.Ltr) - (6.667).dp,
            vertical = ButtonDefaults.ContentPadding.calculateTopPadding()
        ),
        onClick = onClick
    ) {
        Text(stringResource(R.string.view), maxLines = 1)
    }
}
