package com.github.capntrips.kernelflasher.ui.screens.slot

import androidx.lifecycle.ViewModel
import com.github.capntrips.kernelflasher.ui.state.slot.SlotStateInterface
import kotlinx.coroutines.flow.StateFlow

class SlotViewModel constructor(
    override val uiState: StateFlow<SlotStateInterface>
) : ViewModel(),
    SlotViewModelInterface
