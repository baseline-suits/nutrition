package de.baseline.nutrition

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import de.baseline.nutrition.ui.BaselineApp

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as BaselineApplication).container
        setContent {
            BaselineApp(container = container)
        }
    }
}
