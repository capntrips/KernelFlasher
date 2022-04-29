package com.github.capntrips.kernelflasher.ui.screens.backups

import androidx.lifecycle.ViewModel
import com.github.capntrips.kernelflasher.ui.state.backups.BackupsState
import kotlinx.coroutines.flow.StateFlow

class BackupsViewModel constructor(
    override val uiState: StateFlow<BackupsState>
) : ViewModel(),
    BackupsViewModelInterface
