package com.github.capntrips.kernelflasher.ui.screens.backups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.common.PartitionUtil
import com.github.capntrips.kernelflasher.ui.components.DataCard
import com.github.capntrips.kernelflasher.ui.components.DataRow
import com.github.capntrips.kernelflasher.ui.components.DataSet
import com.github.capntrips.kernelflasher.ui.components.ViewButton

@ExperimentalMaterial3Api
@Composable
fun ColumnScope.BackupsContent(
    viewModel: BackupsViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    if (viewModel.currentBackup != null && viewModel.backups.containsKey(viewModel.currentBackup)) {
        DataCard (viewModel.currentBackup!!) {
            val cardWidth = remember { mutableIntStateOf(0) }
            val currentBackup = viewModel.backups.getValue(viewModel.currentBackup!!)
            DataRow(stringResource(R.string.backup_type), currentBackup.type, mutableMaxWidth = cardWidth)
            DataRow(stringResource(R.string.kernel_version), currentBackup.kernelVersion, mutableMaxWidth = cardWidth, clickable = true)
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
                    val hashWidth = remember { mutableIntStateOf(0) }
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
        AnimatedVisibility(!viewModel.isRefreshing) {
            Column {
                Spacer(Modifier.height(5.dp))
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    onClick = { viewModel.delete(context) { navController.popBackStack() } }
                ) {
                    Text(stringResource(R.string.delete))
                }
            }
        }
    } else {
        DataCard(stringResource(R.string.backups))
        AnimatedVisibility(viewModel.needsMigration) {
            Column {
                Spacer(Modifier.height(5.dp))
                OutlinedButton(
                    modifier = Modifier
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(4.dp),
                    onClick = { viewModel.migrate(context) }
                ) {
                    Text(stringResource(R.string.migrate))
                }
            }
        }
        if (viewModel.backups.isNotEmpty()) {
            for (id in viewModel.backups.keys.sortedByDescending { it }) {
                val currentBackup = viewModel.backups[id]!!
                Spacer(Modifier.height(16.dp))
                DataCard(
                    title = id,
                    button = {
                        AnimatedVisibility(!viewModel.isRefreshing) {
                            Column {
                                ViewButton(onClick = {
                                    navController.navigate("backups/$id")
                                })
                            }
                        }
                    }
                ) {
                    val cardWidth = remember { mutableIntStateOf(0) }
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
                    }
                    DataRow(stringResource(R.string.kernel_version), currentBackup.kernelVersion, mutableMaxWidth = cardWidth, clickable = true)
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
}
