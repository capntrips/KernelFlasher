package com.github.capntrips.kernelflasher.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.ui.screens.main.MainContent
import com.github.capntrips.kernelflasher.ui.screens.main.MainViewModelInterface
import com.github.capntrips.kernelflasher.ui.screens.main.MainViewModelPreview
import com.github.capntrips.kernelflasher.ui.theme.KernelFlasherTheme
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

@ExperimentalMaterial3Api
@Composable
fun RefreshableScreen(
    viewModel: MainViewModelInterface,
    navController: NavController,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        WindowInsets.statusBars
                            .only(WindowInsetsSides.Top)
                            .asPaddingValues()
                    )) {
                if (navController.previousBackStackEntry != null) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.padding(16.dp, 8.dp, 0.dp, 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp)) {
                    Text(
                        modifier = Modifier.align(Alignment.Center),
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
            }
        }
    ) { paddingValues ->
        val context = LocalContext.current
        val isRefreshing by viewModel.isRefreshing.collectAsState()
        SwipeRefresh(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            state = rememberSwipeRefreshState(isRefreshing),
            onRefresh = { viewModel.refresh(context) },
            indicator = { state, trigger ->
                SwipeRefreshIndicator(
                    state = state,
                    refreshTriggerDistance = trigger,
                    backgroundColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.primaryContainer,
                    scale = true
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp, 0.dp, 16.dp, 16.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                content = content
            )
        }
    }
}

@ExperimentalMaterial3Api
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
fun MainScreenPreviewDark() {
    MainScreenPreviewLight()
}

@ExperimentalMaterial3Api
@Preview(showBackground = true)
@Composable
fun MainScreenPreviewLight() {
    KernelFlasherTheme {
        val context = LocalContext.current
        val navController = rememberNavController()
        val viewModel = MainViewModelPreview(context, navController)
        RefreshableScreen(viewModel, navController) {
            MainContent(viewModel, navController)
        }
    }
}
