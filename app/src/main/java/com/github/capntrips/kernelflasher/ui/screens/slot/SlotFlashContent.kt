package com.github.capntrips.kernelflasher.ui.screens.slot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.common.PartitionUtil
import com.github.capntrips.kernelflasher.ui.components.DataCard
import com.github.capntrips.kernelflasher.ui.components.FlashButton
import com.github.capntrips.kernelflasher.ui.components.FlashList
import com.github.capntrips.kernelflasher.ui.components.SlotCard
import kotlinx.serialization.ExperimentalSerializationApi

@ExperimentalAnimationApi
@ExperimentalMaterialApi
@ExperimentalMaterial3Api
@ExperimentalUnitApi
@ExperimentalSerializationApi
@Composable
fun ColumnScope.SlotFlashContent(
    viewModel: SlotViewModel,
    slotSuffix: String,
    navController: NavController
) {
    val context = LocalContext.current
    if (!listOf("/flash/ak3", "/flash/image/flash", "/backup/backup").any { navController.currentDestination!!.route!!.endsWith(it) }) {
        SlotCard(
            title = stringResource(if (slotSuffix == "_a") R.string.slot_a else if (slotSuffix == "_b") R.string.slot_b else R.string.slot),
            viewModel = viewModel,
            navController = navController,
            isSlotScreen = true,
            showDlkm = false
        )
        Spacer(Modifier.height(16.dp))
        if (navController.currentDestination!!.route!!.endsWith("/flash")) {
            DataCard (stringResource(R.string.flash))
            Spacer(Modifier.height(5.dp))
            FlashButton(stringResource(R.string.flash_ak3_zip), callback = { uri ->
                navController.navigate("slot$slotSuffix/flash/ak3") {
                    popUpTo("slot$slotSuffix")
                }
                viewModel.flashAk3(context, uri)
            })
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                onClick = {
                    navController.navigate("slot$slotSuffix/flash/image")
                }
            ) {
                Text(stringResource(R.string.flash_partition_image))
            }
        } else if (navController.currentDestination!!.route!!.endsWith("/flash/image")) {
            DataCard (stringResource(R.string.flash_partition_image))
            Spacer(Modifier.height(5.dp))
            for (partitionName in PartitionUtil.AvailablePartitions) {
                FlashButton(partitionName, callback = { uri ->
                    navController.navigate("slot$slotSuffix/flash/image/flash") {
                        popUpTo("slot$slotSuffix")
                    }
                    viewModel.flashImage(context, uri, partitionName)
                })
            }
        } else if (navController.currentDestination!!.route!!.endsWith("/backup")) {
            DataCard (stringResource(R.string.backup))
            Spacer(Modifier.height(5.dp))
            val disabledColor = ButtonDefaults.buttonColors(
                Color.Transparent,
                MaterialTheme.colorScheme.onSurface
            )
            for (partitionName in PartitionUtil.AvailablePartitions) {
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (viewModel.backupPartitions[partitionName] == true) 1.0f else 0.5f),
                    shape = RoundedCornerShape(4.dp),
                    colors = if (viewModel.backupPartitions[partitionName]!!) ButtonDefaults.outlinedButtonColors() else disabledColor,
                    onClick = {
                        viewModel.backupPartitions[partitionName] = !viewModel.backupPartitions[partitionName]!!
                    },
                ) {
                    Box(Modifier.fillMaxWidth()) {
                        Checkbox(viewModel.backupPartitions[partitionName]!!, null,
                            Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = -(16.dp)))
                        Text(partitionName, Modifier.align(Alignment.Center))
                    }
                }
            }
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                onClick = {
                    viewModel.backup(context)
                    navController.navigate("slot$slotSuffix/backup/backup") {
                        popUpTo("slot$slotSuffix")
                    }
                },
                enabled = viewModel.backupPartitions.filter { it.value }.isNotEmpty()
            ) {
                Text(stringResource(R.string.backup))
            }
        }
    } else {
        Text("")
        FlashList(
            stringResource(if (navController.currentDestination!!.route!!.endsWith("/backup/backup")) R.string.backup else R.string.flash),
            if (navController.currentDestination!!.route!!.contains("ak3")) viewModel.uiPrintedOutput else viewModel.flashOutput
        ) {
            AnimatedVisibility(!viewModel.isRefreshing && viewModel.wasFlashSuccess != null) {
                Column {
                    if (navController.currentDestination!!.route!!.contains("ak3")) {
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            onClick = { viewModel.saveLog(context) }
                        ) {
                            if (navController.currentDestination!!.route!!.contains("ak3")) {
                                Text(stringResource(R.string.save_ak3_log))
                            } else if (navController.currentDestination!!.route!!.endsWith("/backup/backup")) {
                                Text(stringResource(R.string.save_backup_log))
                            } else {
                                Text(stringResource(R.string.save_flash_log))
                            }
                        }
                    }
                    if (navController.currentDestination!!.route!!.contains("ak3")) {
                        AnimatedVisibility(!navController.currentDestination!!.route!!.endsWith("/backups/{backupId}/flash/ak3") && viewModel.wasFlashSuccess != false) {
                            OutlinedButton(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(4.dp),
                                onClick = {
                                    viewModel.backupZip(context) {
                                        navController.navigate("slot$slotSuffix/backups") {
                                            popUpTo("slot$slotSuffix")
                                        }
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.save_ak3_zip_as_backup))
                            }
                        }
                    }
                    if (viewModel.wasFlashSuccess != false && navController.currentDestination!!.route!!.endsWith("/backup/backup")) {
                        OutlinedButton(
                            modifier = Modifier
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(4.dp),
                            onClick = { navController.popBackStack() }
                        ) {
                            Text(stringResource(R.string.back))
                        }
                    } else {
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
            }
        }
    }
}
