package com.github.capntrips.kernelflasher.ui.screens.backups

import androidx.lifecycle.ViewModel
import com.github.capntrips.kernelflasher.ui.state.backups.BackupsStateInterface
import com.github.capntrips.kernelflasher.ui.state.backups.BackupsStatePreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BackupsViewModelPreview : ViewModel(), BackupsViewModelInterface {
    private var _uiState: MutableStateFlow<BackupsStateInterface> = MutableStateFlow(BackupsStatePreview(MutableStateFlow(false)))
    override val uiState: StateFlow<BackupsStateInterface>
        get() = _uiState.asStateFlow()
}
