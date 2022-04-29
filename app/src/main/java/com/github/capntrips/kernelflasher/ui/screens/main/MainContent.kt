package com.github.capntrips.kernelflasher.ui.screens.main

import android.content.res.Configuration
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.ui.components.DataCard
import com.github.capntrips.kernelflasher.ui.components.DataRow
import com.github.capntrips.kernelflasher.ui.components.SlotCard
import com.github.capntrips.kernelflasher.ui.theme.KernelFlasherTheme

@ExperimentalMaterial3Api
@Composable
fun MainContent(viewModel: MainViewModelInterface, navController: NavController) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val context = LocalContext.current
    Column {
        DataCard (title = stringResource(R.string.device)) {
            DataRow(
                label = stringResource(R.string.model),
                value = "${Build.MODEL} (${Build.DEVICE})"
            )
            DataRow(
                label = stringResource(R.string.build_number),
                value = Build.ID
            )
            DataRow(
                label = stringResource(R.string.kernel_version),
                value = System.getProperty("os.version")!!
            )
            DataRow(
                label = stringResource(R.string.slot_suffix),
                value = uiState.slotSuffix
            )
        }
        Spacer(Modifier.height(16.dp))
        SlotCard(
            title = stringResource(R.string.slot_a),
            slotStateFlow = uiState.slotA,
            navController = navController
        )
        Spacer(Modifier.height(16.dp))
        SlotCard(
            title = stringResource(R.string.slot_b),
            slotStateFlow = uiState.slotB,
            navController = navController
        )
        Spacer(Modifier.height(16.dp))
        AnimatedVisibility(!isRefreshing) {
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                onClick = { navController.navigate("backups") }
            ) {
                Text(stringResource(R.string.backups))
            }
        }
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            onClick = { viewModel.saveDmesg(context) }
        ) {
            Text(stringResource(R.string.save_dmesg))
        }
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            onClick = { viewModel.saveLogcat(context) }
        ) {
            Text(stringResource(R.string.save_logcat))
        }
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            onClick = { viewModel.reboot() }
        ) {
            Text(stringResource(R.string.reboot))
        }
    }
}

@ExperimentalMaterial3Api
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun MainContentPreviewDark() {
    MainContentPreviewLight()
}

@ExperimentalMaterial3Api
@Preview(showBackground = true)
@Composable
fun MainContentPreviewLight() {
    KernelFlasherTheme {
        Scaffold {
            val context = LocalContext.current
            val navController = rememberNavController()
            val viewModel = MainViewModelPreview(context, navController)
            MainContent(viewModel, navController)
        }
    }
}
