package com.github.capntrips.kernelflasher.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.capntrips.kernelflasher.MainActivity
import com.github.capntrips.kernelflasher.R
import com.github.capntrips.kernelflasher.ui.state.slot.SlotStateInterface

@ExperimentalAnimationApi
@ExperimentalMaterial3Api
@Composable
fun FlashButton(slot: SlotStateInterface) {
    val mainActivity = LocalContext.current as MainActivity
    val result = remember { mutableStateOf<Uri?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        result.value = it
        if (it == null) {
            mainActivity.isAwaitingResult = false
        }
    }
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        onClick = {
            mainActivity.isAwaitingResult = true
            launcher.launch("*/*")
        }
    ) {
        Text(stringResource(R.string.flash))
    }
    result.value?.let {uri ->
        if (mainActivity.isAwaitingResult) {
            slot.flash(mainActivity, uri)
        }
        mainActivity.isAwaitingResult = false
    }
}
