package com.example

import java.util.Locale
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.LivePhotoApp
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.LivePhotoViewModel

class MainActivity : ComponentActivity() {
  override fun attachBaseContext(newBase: Context) {
      val prefs = newBase.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)
      val lang = prefs.getString("language", "system") ?: "system"
      val context = if (lang != "system") {
          val locale = when (lang) {
              "en" -> Locale("en")
              "ja" -> Locale("ja")
              "zh-TW" -> Locale("zh", "TW")
              "zh-CN" -> Locale("zh", "CN")
              else -> Locale.getDefault()
          }
          Locale.setDefault(locale)
          val config = Configuration(newBase.resources.configuration)
          config.setLocale(locale)
          newBase.createConfigurationContext(config)
      } else {
          newBase
      }
      super.attachBaseContext(context)
  }

  private val viewModel: LivePhotoViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode =
            android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        window.isStatusBarContrastEnforced = false
        window.isNavigationBarContrastEnforced = false
    }
    window.statusBarColor = android.graphics.Color.TRANSPARENT
    window.navigationBarColor = android.graphics.Color.TRANSPARENT
    setContent {
      val themeMode by viewModel.themeMode.collectAsState()
      val dynamicColor by viewModel.dynamicColor.collectAsState()
      val themeColor by viewModel.themeColor.collectAsState()
      
      val isDarkTheme = when (themeMode) {
          1 -> false
          2 -> true
          else -> isSystemInDarkTheme()
      }

      MyApplicationTheme(
        darkTheme = isDarkTheme,
        dynamicColor = dynamicColor,
        themeColor = themeColor
      ) {
        LivePhotoApp(viewModel = viewModel)
      }
    }
  }
}
