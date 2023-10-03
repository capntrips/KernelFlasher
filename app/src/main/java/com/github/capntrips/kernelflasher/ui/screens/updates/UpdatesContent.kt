package com.github.capntrips.kernelflasher.ui.screens.updates

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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.common.types.room.updates.DateSerializer
import com.github.capntrips.kernelflasher.ui.components.DataCard
import com.github.capntrips.kernelflasher.ui.components.DataRow
import com.github.capntrips.kernelflasher.ui.components.ViewButton

@ExperimentalMaterial3Api
@Composable
fun ColumnScope.UpdatesContent(
    viewModel: UpdatesViewModel,
    navController: NavController
) {
    @Suppress("UNUSED_VARIABLE") val context = LocalContext.current
    DataCard(stringResource(R.string.updates))
    if (viewModel.updates.isNotEmpty()) {
        for (update in viewModel.updates.sortedByDescending { it.kernelDate }) {
            Spacer(Modifier.height(16.dp))
            DataCard(
                title = update.kernelName,
                button = {
                    AnimatedVisibility(!viewModel.isRefreshing) {
                        Column {
                            ViewButton(onClick = {
                                navController.navigate("updates/view/${update.id}")
                            })
                        }
                    }
                }
            ) {
                val cardWidth = remember { mutableIntStateOf(0) }
                DataRow(stringResource(R.string.version), update.kernelVersion, mutableMaxWidth = cardWidth)
                DataRow(stringResource(R.string.date_released), DateSerializer.formatter.format(update.kernelDate), mutableMaxWidth = cardWidth)
                DataRow(
                    label = stringResource(R.string.last_updated),
                    value = UpdatesViewModel.lastUpdatedFormatter.format(update.lastUpdated!!),
                    labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.33f),
                    labelStyle = MaterialTheme.typography.labelMedium.copy(
                        fontStyle = FontStyle.Italic
                    ),
                    valueColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.33f),
                    valueStyle = MaterialTheme.typography.titleSmall.copy(
                        fontStyle = FontStyle.Italic
                    ),
                    mutableMaxWidth = cardWidth
                )
            }
        }
    }
    AnimatedVisibility(!viewModel.isRefreshing) {
        Column {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(4.dp),
                onClick = { navController.navigate("updates/add") }
            ) {
                Text(stringResource(R.string.add))
            }
        }
    }
}
