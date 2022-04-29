package com.github.capntrips.kernelflasher.ui.screens.slot

import com.github.capntrips.kernelflasher.ui.state.slot.SlotStateInterface
import kotlinx.coroutines.flow.StateFlow

interface SlotViewModelInterface {
    val uiState: StateFlow<SlotStateInterface>
}
