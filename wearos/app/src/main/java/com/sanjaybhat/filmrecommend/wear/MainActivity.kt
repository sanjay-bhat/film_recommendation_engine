package com.sanjaybhat.filmrecommend.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjaybhat.filmrecommend.wear.ui.WearBrowseScreen
import com.sanjaybhat.filmrecommend.wear.ui.WearViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val vm: WearViewModel = viewModel()
            WearBrowseScreen(vm)
        }
    }
}
