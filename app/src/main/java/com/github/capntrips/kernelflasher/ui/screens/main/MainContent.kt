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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
        val cardWidth = remember { mutableStateOf(0) }
        DataRow(stringResource(R.string.model), "${Build.MODEL} (${Build.DEVICE})", mutableMaxWidth = cardWidth)
        DataRow(stringResource(R.string.build_number), Build.ID, mutableMaxWidth = cardWidth)
        DataRow(stringResource(R.string.kernel_version), System.getProperty("os.version")!!, mutableMaxWidth = cardWidth)
        DataRow(stringResource(R.string.slot_suffix), viewModel.slotSuffix, mutableMaxWidth = cardWidth)
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
    AnimatedVisibility(!viewModel.isRefreshing) {
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            onClick = { navController.navigate("updates") }
        ) {
            Text(stringResource(R.string.updates))
        }
    }
    if (viewModel.hasRamoops) {
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            onClick = { viewModel.saveRamoops(context) }
        ) {
            Text(stringResource(R.string.save_ramoops))
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
    AnimatedVisibility(!viewModel.isRefreshing) {
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            onClick = { navController.navigate("reboot") }
        ) {
            Text(stringResource(R.string.reboot))
        }
    }
}
