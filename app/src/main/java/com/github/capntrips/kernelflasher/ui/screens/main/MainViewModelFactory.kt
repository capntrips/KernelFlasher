package com.github.capntrips.kernelflasher.ui.screens.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController

class MainViewModelFactory(private val context: Context, private val navController: NavController) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(context, navController) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}