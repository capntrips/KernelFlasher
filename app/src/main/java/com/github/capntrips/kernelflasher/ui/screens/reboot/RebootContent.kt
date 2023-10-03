package com.github.capntrips.kernelflasher.ui.screens.reboot

import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R

@Suppress("UnusedReceiverParameter")
@Composable
fun ColumnScope.RebootContent(
    viewModel: RebootViewModel,
    @Suppress("UNUSED_PARAMETER") ignoredNavController: NavController
) {
    val context = LocalContext.current
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        onClick = { viewModel.rebootSystem() }
    ) {
        Text(stringResource(R.string.reboot))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && context.getSystemService(PowerManager::class.java)?.isRebootingUserspaceSupported == true) {
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            onClick = { viewModel.rebootUserspace() }
        ) {
            Text(stringResource(R.string.reboot_userspace))
        }
    }
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        onClick = { viewModel.rebootRecovery() }
    ) {
        Text(stringResource(R.string.reboot_recovery))
    }
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        onClick = { viewModel.rebootBootloader() }
    ) {
        Text(stringResource(R.string.reboot_bootloader))
    }
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        onClick = { viewModel.rebootDownload() }
    ) {
        Text(stringResource(R.string.reboot_download))
    }
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        onClick = { viewModel.rebootEdl() }
    ) {
        Text(stringResource(R.string.reboot_edl))
    }
}
