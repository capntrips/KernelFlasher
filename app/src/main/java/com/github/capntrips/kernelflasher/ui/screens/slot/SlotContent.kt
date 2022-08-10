package com.github.capntrips.kernelflasher.ui.screens.slot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.ui.components.FlashButton
import com.github.capntrips.kernelflasher.ui.components.SlotCard

@ExperimentalUnitApi
@ExperimentalAnimationApi
@ExperimentalMaterial3Api
@Composable
fun ColumnScope.SlotContent(
    viewModel: SlotViewModel,
    slotSuffix: String,
    navController: NavController
) {
    val context = LocalContext.current
    SlotCard(
        title = stringResource(if (slotSuffix == "_a") R.string.slot_a else R.string.slot_b),
        viewModel = viewModel,
        navController = navController,
        isSlotScreen = true
    )
    AnimatedVisibility(!viewModel.isRefreshing) {
        Column {
            Spacer(Modifier.height(5.dp))
            if (viewModel.isActive) {
                FlashButton(viewModel, callback = {
                    navController.navigate("slot$slotSuffix/flash")
                })
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                onClick = {
                    viewModel.backup(context) {
                        navController.navigate("slot$slotSuffix/backups")
                    }
                }
            ) {
                Text(stringResource(R.string.backup))
            }
            if (viewModel.isActive) {
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    onClick = {
                        navController.navigate("slot$slotSuffix/backups")
                    }
                ) {
                    Text(stringResource(R.string.restore))
                }
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                onClick = { if (!viewModel.isRefreshing) viewModel.getKernel(context) }
            ) {
                Text(stringResource(R.string.check_kernel_version))
            }
            if (viewModel.hasVendorDlkm) {
                AnimatedVisibility(!viewModel.isRefreshing) {
                    AnimatedVisibility(viewModel.isVendorDlkmMounted) {
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            onClick = { viewModel.unmountVendorDlkm(context) }
                        ) {
                            Text(stringResource(R.string.unmount_vendor_dlkm))
                        }
                    }
                    AnimatedVisibility(!viewModel.isVendorDlkmMounted && viewModel.isVendorDlkmMapped) {
                        Column {
                            OutlinedButton(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(4.dp),
                                onClick = { viewModel.mountVendorDlkm(context) }
                            ) {
                                Text(stringResource(R.string.mount_vendor_dlkm))
                            }
                            OutlinedButton(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(4.dp),
                                onClick = { viewModel.unmapVendorDlkm(context) }
                            ) {
                                Text(stringResource(R.string.unmap_vendor_dlkm))
                            }
                        }
                    }
                    AnimatedVisibility(!viewModel.isVendorDlkmMounted && !viewModel.isVendorDlkmMapped) {
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            onClick = { viewModel.mapVendorDlkm(context) }
                        ) {
                            Text(stringResource(R.string.map_vendor_dlkm))
                        }
                    }
                }
            }
        }
    }
}
