package com.github.capntrips.kernelflasher.ui.screens.slot

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.ui.components.FlashButton
import com.github.capntrips.kernelflasher.ui.components.SlotCard
import com.github.capntrips.kernelflasher.ui.theme.KernelFlasherTheme

@ExperimentalAnimationApi
@ExperimentalMaterial3Api
@Composable
fun SlotContent(viewModel: SlotViewModelInterface, slotSuffix: String, navController: NavController) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by uiState.isRefreshing.collectAsState()
    val context = LocalContext.current
    Column {
        SlotCard(
            title = stringResource(if (slotSuffix == "_a") R.string.slot_a else R.string.slot_b),
            slotStateFlow = viewModel.uiState,
            navController = navController,
            isSlotScreen = true
        )
        AnimatedVisibility(!isRefreshing) {
            Column {
                Spacer(Modifier.height(5.dp))
                if (!uiState.wasFlashed) {
                    FlashButton(uiState)
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        onClick = {
                            uiState.backup(context) {
                                if (slotSuffix == "_a")
                                    navController.navigate("slotA/backups")
                                else
                                    navController.navigate("slotB/backups")
                            }
                        }
                    ) {
                        Text(stringResource(R.string.backup))
                    }
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        onClick = {
                            if (slotSuffix == "_a")
                                navController.navigate("slotA/backups")
                            else
                                navController.navigate("slotB/backups")
                        }
                    ) {
                        Text(stringResource(R.string.restore))
                    }
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        onClick = { if (!isRefreshing) uiState.getKernel(context) }
                    ) {
                        Text(stringResource(R.string.check_kernel_version))
                    }
                    AnimatedVisibility(uiState.isVendorDlkmMounted) {
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            onClick = { if (!isRefreshing) uiState.unmountVendorDlkm(context) }
                        ) {
                            Text(stringResource(R.string.unmount_vendor_dlkm))
                        }
                    }
                    AnimatedVisibility(!uiState.isVendorDlkmMounted && uiState.hasVendorDlkm) {
                        Column {
                            OutlinedButton(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(4.dp),
                                onClick = { uiState.mountVendorDlkm(context) }
                            ) {
                                Text(stringResource(R.string.mount_vendor_dlkm))
                            }
                            OutlinedButton(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(4.dp),
                                onClick = { uiState.unmapVendorDlkm(context) }
                            ) {
                                Text(stringResource(R.string.unmap_vendor_dlkm))
                            }
                        }
                    }
                    AnimatedVisibility(!uiState.isVendorDlkmMounted && !uiState.hasVendorDlkm) {
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            onClick = { if (!isRefreshing) uiState.mapVendorDlkm(context) }
                        ) {
                            Text(stringResource(R.string.map_vendor_dlkm))
                        }
                    }
                } else {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        onClick = { uiState.reboot() }
                    ) {
                        Text(stringResource(R.string.reboot))
                    }
                }
            }
        }
    }
}

@ExperimentalAnimationApi
@ExperimentalMaterial3Api
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun SlotContentPreviewDark() {
    SlotContentPreviewLight()
}

@ExperimentalAnimationApi
@ExperimentalMaterial3Api
@Preview(showBackground = true)
@Composable
fun SlotContentPreviewLight() {
    KernelFlasherTheme {
        Scaffold {
            val viewModel: SlotViewModelPreview = viewModel()
            val navController = rememberNavController()
            SlotContent(viewModel, "_a", navController)
        }
    }
}
