package com.lumoos.medidorled

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import com.lumoos.medidorled.ui.LedMeasurementController
import com.lumoos.medidorled.ui.MeasurementScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val controller = remember { LedMeasurementController() }
                    MeasurementScreen(controller)
                }
            }
        }
    }
}
