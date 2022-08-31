package com.github.capntrips.kernelflasher.ui.screens.backups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.common.PartitionUtil
import com.github.capntrips.kernelflasher.ui.components.DataCard
import com.github.capntrips.kernelflasher.ui.components.DataRow
import com.github.capntrips.kernelflasher.ui.components.DataSet
import com.github.capntrips.kernelflasher.ui.components.FlashList
import com.github.capntrips.kernelflasher.ui.components.SlotCard
import com.github.capntrips.kernelflasher.ui.components.ViewButton
import com.github.capntrips.kernelflasher.ui.screens.slot.SlotViewModel

@ExperimentalUnitApi
@ExperimentalMaterial3Api
@Composable
fun ColumnScope.SlotBackupsContent(
    slotViewModel: SlotViewModel,
    backupsViewModel: BackupsViewModel,
    slotSuffix: String,
    navController: NavController
) {
    val context = LocalContext.current
    if (!navController.currentDestination!!.route!!.startsWith("slot{slotSuffix}/backups/{backupId}/restore")) {
        SlotCard(
            title = stringResource(if (slotSuffix == "_a") R.string.slot_a else R.string.slot_b),
            viewModel = slotViewModel,
            navController = navController,
            isSlotScreen = true,
        )
        Spacer(Modifier.height(16.dp))
        if (backupsViewModel.currentBackup != null && backupsViewModel.backups.containsKey(backupsViewModel.currentBackup)) {
            val currentBackup = backupsViewModel.backups.getValue(backupsViewModel.currentBackup!!)
            DataCard (backupsViewModel.currentBackup!!) {
                val cardWidth = remember { mutableStateOf(0) }
                DataRow(stringResource(R.string.backup_type), currentBackup.type, mutableMaxWidth = cardWidth)
                DataRow(stringResource(R.string.kernel_version), currentBackup.kernelVersion, mutableMaxWidth = cardWidth)
                if (currentBackup.type == "raw") {
                    DataRow(
                        label = stringResource(R.string.boot_sha1),
                        value = currentBackup.bootSha1!!.substring(0, 8),
                        valueStyle = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        mutableMaxWidth = cardWidth
                    )
                    if (currentBackup.hashes != null) {
                        val hashWidth = remember { mutableStateOf(0) }
                        DataSet(stringResource(R.string.hashes)) {
                            for (partitionName in PartitionUtil.PartitionNames) {
                                val hash = currentBackup.hashes.get(partitionName)
                                if (hash != null) {
                                    DataRow(
                                        label = partitionName,
                                        value = hash.substring(0, 8),
                                        valueStyle = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        mutableMaxWidth = hashWidth
                                    )
                                }
                            }
                        }
                    }
                }
            }
            AnimatedVisibility(!slotViewModel.isRefreshing) {
                Column {
                    Spacer(Modifier.height(5.dp))
                    if (slotViewModel.isActive) {
                        if (currentBackup.type == "raw") {
                            OutlinedButton(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(4.dp),
                                onClick = {
                                    navController.navigate("slot$slotSuffix/backups/${backupsViewModel.currentBackup!!}/restore")
                                }
                            ) {
                                Text(stringResource(R.string.restore))
                            }
                        } else if (currentBackup.type == "ak3") {
                            OutlinedButton(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                shape = RoundedCornerShape(4.dp),
                                onClick = {
                                    slotViewModel.flashAk3(context, backupsViewModel.currentBackup!!, currentBackup.filename!!)
                                    navController.navigate("slot$slotSuffix/backups/${backupsViewModel.currentBackup!!}/flash/ak3") {
                                        popUpTo("slot{slotSuffix}")
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.flash))
                            }
                        }
                    }
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        onClick = { backupsViewModel.delete(context) { navController.popBackStack() } }
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        } else {
            DataCard(stringResource(R.string.backups))
            val backups = backupsViewModel.backups.filter { it.value.bootSha1.equals(slotViewModel.sha1) || it.value.type == "ak3" }
            if (backups.isNotEmpty()) {
                for (id in backups.keys.sortedByDescending { it }) {
                    Spacer(Modifier.height(16.dp))
                    DataCard(
                        title = id,
                        button = {
                            AnimatedVisibility(!slotViewModel.isRefreshing) {
                                ViewButton(onClick = {
                                    navController.navigate("slot$slotSuffix/backups/$id")
                                })
                            }
                        }
                    ) {
                        DataRow(stringResource(R.string.kernel_version), backups[id]!!.kernelVersion)
                    }
                }
            } else {
                Spacer(Modifier.height(32.dp))
                Text(
                    stringResource(R.string.no_backups_found),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    } else if (navController.currentDestination!!.route!! == "slot{slotSuffix}/backups/{backupId}/restore") {
        DataCard (stringResource(R.string.restore))
        Spacer(Modifier.height(5.dp))
        val disabledColor = ButtonDefaults.buttonColors(
            Color.Transparent,
            MaterialTheme.colorScheme.onSurface
        )
        val currentBackup = backupsViewModel.backups.getValue(backupsViewModel.currentBackup!!)
        if (currentBackup.hashes != null) {
            for (partitionName in PartitionUtil.PartitionNames) {
                val hash = currentBackup.hashes.get(partitionName)
                if (hash != null) {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (backupsViewModel.backupPartitions[partitionName] == true) 1.0f else 0.5f),
                        shape = RoundedCornerShape(4.dp),
                        colors = if (backupsViewModel.backupPartitions[partitionName] == true) ButtonDefaults.outlinedButtonColors() else disabledColor,
                        enabled = backupsViewModel.backupPartitions[partitionName] != null,
                        onClick = {
                            backupsViewModel.backupPartitions[partitionName] = !backupsViewModel.backupPartitions[partitionName]!!
                        },
                    ) {
                        Box(Modifier.fillMaxWidth()) {
                            Checkbox(backupsViewModel.backupPartitions[partitionName] == true, null,
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .offset(x = -(16.dp)))
                            Text(partitionName, Modifier.align(Alignment.Center))
                        }
                    }
                }
            }
        } else {
            Text(
                stringResource(R.string.partition_selection_unavailable),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontStyle = FontStyle.Italic
            )
            Spacer(Modifier.height(5.dp))
        }
        // TODO: disable button if no partitions are selected
        // TODO: disable button if any partitions are unavailable
        OutlinedButton(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            onClick = {
                backupsViewModel.restore(context, slotSuffix)
                navController.navigate("slot$slotSuffix/backups/${backupsViewModel.currentBackup!!}/restore/restore") {
                    popUpTo("slot{slotSuffix}")
                }
            }
        ) {
            Text(stringResource(R.string.restore))
        }
    } else {
        FlashList(
            stringResource(R.string.restore),
            backupsViewModel.restoreOutput
        ) {
            AnimatedVisibility(!backupsViewModel.isRefreshing && backupsViewModel.wasRestored != null) {
                Column {
                    if (backupsViewModel.wasRestored != false) {
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
