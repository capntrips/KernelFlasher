package com.github.capntrips.kernelflasher.ui.screens.slot

import androidx.lifecycle.ViewModel
import com.github.capntrips.kernelflasher.ui.state.slot.SlotStateInterface
import com.github.capntrips.kernelflasher.ui.state.slot.SlotStatePreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SlotViewModelPreview : ViewModel(), SlotViewModelInterface {
    private var _uiState: MutableStateFlow<SlotStateInterface> = MutableStateFlow(SlotStatePreview(MutableStateFlow(false), true))
    override val uiState: StateFlow<SlotStateInterface>
        get() = _uiState.asStateFlow()
}
