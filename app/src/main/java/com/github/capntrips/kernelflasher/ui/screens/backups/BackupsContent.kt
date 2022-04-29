package com.github.capntrips.kernelflasher.ui.screens.backups

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.ui.components.DataCard
import com.github.capntrips.kernelflasher.ui.components.DataRow
import com.github.capntrips.kernelflasher.ui.components.ViewButton
import com.github.capntrips.kernelflasher.ui.theme.KernelFlasherTheme

@ExperimentalMaterial3Api
@Composable
fun BackupsContent(viewModel: BackupsViewModelInterface, navController: NavController) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by uiState.isRefreshing.collectAsState()
    val context = LocalContext.current
    Column {
        if (uiState.currentBackup != null && uiState.backups.containsKey(uiState.currentBackup)) {
            DataCard (uiState.currentBackup!!) {
                val props = uiState.backups.getValue(uiState.currentBackup!!)
                DataRow(stringResource(R.string.boot_sha1), props.getProperty("sha1").substring(0, 8))
                DataRow(stringResource(R.string.kernel_version), props.getProperty("kernel"))
            }
            AnimatedVisibility(!isRefreshing) {
                Column {
                    Spacer(Modifier.height(5.dp))
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(4.dp),
                        onClick = { uiState.delete(context) { navController.popBackStack() } }
                    ) {
                        Text(stringResource(R.string.delete))
                    }
                }
            }
        } else {
            DataCard(stringResource(R.string.backups))
            if (uiState.backups.isNotEmpty()) {
                for ((id, props) in uiState.backups) {
                    Spacer(Modifier.height(16.dp))
                    DataCard(
                        title = id,
                        button = {
                            AnimatedVisibility(!isRefreshing) {
                                Column {
                                    ViewButton(onClick = {
                                        navController.navigate("backups/$id")
                                    })
                                }
                            }
                        }
                    ) {
                        DataRow(stringResource(R.string.boot_sha1), props.getProperty("sha1").substring(0, 8))
                        DataRow(stringResource(R.string.kernel_version), props.getProperty("kernel"))
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
}

@ExperimentalMaterial3Api
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun BackupsContentPreviewDark() {
    BackupsContentPreviewLight()
}

@ExperimentalMaterial3Api
@Preview(showBackground = true)
@Composable
fun BackupsContentPreviewLight() {
    KernelFlasherTheme {
        Scaffold {
            val viewModel: BackupsViewModelPreview = viewModel()
            val navController = rememberNavController()
            BackupsContent(viewModel, navController)
        }
    }
}
