package com.sextant.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sextant.app.ui.SextantApp
import com.sextant.app.ui.theme.SextantTheme

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val repository = (application as SextantApplication).repository
    setContent {
      SextantTheme {
        SextantApp(repository)
      }
    }
  }
}
