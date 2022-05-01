package com.github.capntrips.kernelflasher.ui.screens.main

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.ui.components.DataCard
import com.github.capntrips.kernelflasher.ui.components.DataRow
import com.github.capntrips.kernelflasher.ui.components.SlotCard

@ExperimentalMaterial3Api
@Composable
fun ColumnScope.MainContent(
    viewModel: MainViewModel,
    navController: NavController
) {
    val context = LocalContext.current
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
            value = viewModel.slotSuffix
        )
    }
    Spacer(Modifier.height(16.dp))
    SlotCard(
        title = stringResource(R.string.slot_a),
        viewModel = viewModel.slotA,
        navController = navController
    )
    Spacer(Modifier.height(16.dp))
    SlotCard(
        title = stringResource(R.string.slot_b),
        viewModel = viewModel.slotB,
        navController = navController
    )
    Spacer(Modifier.height(16.dp))
    AnimatedVisibility(!viewModel.isRefreshing) {
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
