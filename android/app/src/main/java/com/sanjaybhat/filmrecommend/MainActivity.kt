package com.sanjaybhat.filmrecommend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sanjaybhat.filmrecommend.ui.MainViewModel
import com.sanjaybhat.filmrecommend.ui.MovieSearchScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            MovieSearchScreen(viewModel)
        }
    }
}
