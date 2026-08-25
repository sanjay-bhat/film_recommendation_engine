package com.sanjaybhat.filmrecommend.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjaybhat.filmrecommend.tv.ui.MainViewModel
import com.sanjaybhat.filmrecommend.tv.ui.TVBrowseScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: MainViewModel = viewModel()
            TVBrowseScreen(vm)
        }
    }
}
