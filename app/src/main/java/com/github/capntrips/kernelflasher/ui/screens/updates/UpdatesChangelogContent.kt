package com.github.capntrips.kernelflasher.ui.screens.updates

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.ui.components.DataCard

@Suppress("unused")
@ExperimentalUnitApi
@ExperimentalMaterial3Api
@Composable
fun ColumnScope.UpdatesChangelogContent(
    viewModel: UpdatesViewModel,
    @Suppress("UNUSED_PARAMETER") navController: NavController
) {
    viewModel.currentUpdate?.let { currentUpdate ->
        DataCard(currentUpdate.kernelName)
        Spacer(Modifier.height(16.dp))
        Text(viewModel.changelog!!,
            style = LocalTextStyle.current.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = TextUnit(12.0f, TextUnitType.Sp),
                lineHeight = TextUnit(18.0f, TextUnitType.Sp)
            )
        )
    }
}
