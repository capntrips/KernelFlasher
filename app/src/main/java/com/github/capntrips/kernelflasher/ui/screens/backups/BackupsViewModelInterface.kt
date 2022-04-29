package com.github.capntrips.kernelflasher.ui.screens.backups

import com.github.capntrips.kernelflasher.ui.state.backups.BackupsStateInterface
import kotlinx.coroutines.flow.StateFlow

interface BackupsViewModelInterface {
    val uiState: StateFlow<BackupsStateInterface>
}
