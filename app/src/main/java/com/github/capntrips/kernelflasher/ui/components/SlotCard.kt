package com.github.capntrips.kernelflasher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.ui.state.slot.SlotStateInterface
import kotlinx.coroutines.flow.StateFlow

@ExperimentalMaterial3Api
@Composable
fun SlotCard(
    title: String,
    slotStateFlow: StateFlow<SlotStateInterface>,
    navController: NavController,
    isSlotScreen: Boolean = false,
) {
    val slot by slotStateFlow.collectAsState()
    val isRefreshing by slot.isRefreshing.collectAsState()
    DataCard (
        title = title,
        button = {
            if (!isSlotScreen) {
                AnimatedVisibility(!isRefreshing) {
                    ViewButton {
                        navController.navigate(if (slot.slotSuffix == "_a") "slotA" else "slotB")
                    }
                }
            }
        }
    ) {
        DataRow(
            label = stringResource(R.string.boot_sha1),
            value = slot.sha1.substring(0, 8),
            valueStyle = MaterialTheme.typography.titleSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Thin
            )
        )
        AnimatedVisibility(!isRefreshing && slot.kernelVersion != null) {
            DataRow(
                label = stringResource(R.string.kernel_version),
                value = if (slot.kernelVersion != null) slot.kernelVersion!! else ""
            )
        }
        var vendorDlkmValue = stringResource(R.string.not_found)
        if (slot.hasVendorDlkm) {
            vendorDlkmValue = if (slot.isVendorDlkmMounted) {
                String.format("%s, %s", stringResource(R.string.exists), stringResource(R.string.mounted))
            } else {
                String.format("%s, %s", stringResource(R.string.exists), stringResource(R.string.unmounted))
            }
        }
        DataRow(
            label = stringResource(R.string.vendor_dlkm),
            value = vendorDlkmValue
        )
    }
}
