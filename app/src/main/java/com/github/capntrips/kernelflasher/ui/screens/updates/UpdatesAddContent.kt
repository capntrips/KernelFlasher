package com.github.capntrips.kernelflasher.ui.screens.updates

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.github.capntrips.kernelflasher.R
import kotlinx.serialization.ExperimentalSerializationApi

@Suppress("UnusedReceiverParameter")
@ExperimentalMaterial3Api
@ExperimentalSerializationApi
@Composable
fun ColumnScope.UpdatesAddContent(
    viewModel: UpdatesViewModel,
    navController: NavController
) {
    @Suppress("UNUSED_VARIABLE") val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    OutlinedTextField(
        value = url,
        onValueChange = { url = it },
        label = { Text(stringResource(R.string.url)) },
        modifier = Modifier
            .fillMaxWidth()
    )
    Spacer(Modifier.height(5.dp))
    OutlinedButton(
        modifier = Modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        onClick = { viewModel.add(url) { navController.navigate("updates/view/$it") { popUpTo("updates") } } }
    ) {
        Text(stringResource(R.string.add))
    }
}