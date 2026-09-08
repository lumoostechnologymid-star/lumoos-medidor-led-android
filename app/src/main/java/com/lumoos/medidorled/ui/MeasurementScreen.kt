package com.lumoos.medidorled.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.lumoos.medidorled.camera.LedFrameAnalyzer
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementScreen(controller: LedMeasurementController) {
    val state = controller.state
    val context = LocalContext.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraGranted = granted }

    LaunchedEffect(Unit) {
        if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val analyzer: ImageAnalysis.Analyzer = remember(controller) {
        LedFrameAnalyzer { sample ->
            mainExecutor.execute { controller.onFrame(sample) }
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Lumoos Medidor LED") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Medición por pulsos del LED",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "1 vuelta = encendido → apagado → encendido. El primer encendido inicia el cronómetro.",
                style = MaterialTheme.typography.bodyMedium
            )

            if (cameraGranted) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.height(330.dp)) {
                        CameraPreview(analyzer = analyzer, modifier = Modifier.fillMaxSize())
                    }
                }
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Se necesita permiso de cámara para detectar el LED.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Dar permiso")
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = state.khText,
                            onValueChange = controller::setKh,
                            label = { Text("KH") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            enabled = !state.locked,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(
                            value = state.targetRevolutions.toString(),
                            onValueChange = { it.toIntOrNull()?.let(controller::setTargetRevolutions) },
                            label = { Text("Vueltas") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            enabled = !state.locked,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 3, 5, 10).forEach { option ->
                            FilterChip(
                                selected = state.targetRevolutions == option,
                                onClick = { controller.setTargetRevolutions(option) },
                                enabled = !state.locked,
                                label = { Text(option.toString()) }
                            )
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Detección del LED", fontWeight = FontWeight.SemiBold)
                    Text("Color / tipo del LED")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.ledColorMode == LedColorMode.YELLOW,
                            onClick = { controller.setLedColorMode(LedColorMode.YELLOW) },
                            enabled = !state.locked && !state.calibrating,
                            label = { Text("Amarillo") }
                        )
                        FilterChip(
                            selected = state.ledColorMode == LedColorMode.RED,
                            onClick = { controller.setLedColorMode(LedColorMode.RED) },
                            enabled = !state.locked && !state.calibrating,
                            label = { Text("Rojo") }
                        )
                        FilterChip(
                            selected = state.ledColorMode == LedColorMode.INFRARED,
                            onClick = { controller.setLedColorMode(LedColorMode.INFRARED) },
                            enabled = !state.locked && !state.calibrating,
                            label = { Text("Infrarrojo") }
                        )
                    }

                    Text("Filtro solar: ACTIVO · compara el centro con la luz alrededor")
                    Text("Brillo del centro: ${state.brightness.toInt()} / 255")
                    Text("Señal filtrada: ${state.signal.toInt()} / 255")
                    LinearProgressIndicator(
                        progress = { (state.signal / 255f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Umbral de encendido: ${state.threshold.toInt()}")
                    Slider(
                        value = state.threshold,
                        onValueChange = controller::setThreshold,
                        valueRange = 80f..220f,
                        enabled = !state.locked && !state.calibrating
                    )
                    if (state.calibrating) {
                        LinearProgressIndicator(
                            progress = { state.calibrationProgress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    OutlinedButton(
                        onClick = controller::startCalibration,
                        enabled = !state.locked && !state.calibrating
                    ) {
                        Text(if (state.calibrating) "Calibrando…" else "Calibrar automáticamente (3 s)")
                    }
                    if (state.ledColorMode == LedColorMode.INFRARED) {
                        Text(
                            "Nota: algunos teléfonos tienen un filtro físico que reduce la luz infrarroja; si la señal casi no cambia, prueba acercando la cámara.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Estado LED")
                        Text(
                            when {
                                !state.ledKnown -> "Detectando…"
                                state.ledOn -> "● PRENDIDO"
                                else -> "○ APAGADO"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                    MetricRow("Vueltas", "${state.revolutions} / ${state.targetRevolutions}")
                    MetricRow("Tiempo total", formatSeconds(state.elapsedSeconds))
                    MetricRow("Última vuelta", formatSeconds(state.lastRevolutionSeconds))
                    Text(state.status, style = MaterialTheme.typography.bodyMedium)
                    state.message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = controller::armMeasurement,
                            enabled = cameraGranted && !state.locked && !state.calibrating
                        ) {
                            Text("Iniciar medición")
                        }
                        OutlinedButton(onClick = controller::reset) {
                            Text("Reiniciar")
                        }
                    }
                }
            }

            state.resultKw?.let { result ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("RESULTADO", style = MaterialTheme.typography.labelLarge)
                        Text(
                            String.format(Locale.US, "%.4f kW", result),
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "kW = (${state.revolutions} × ${state.khText} × 3.6) / ${String.format(Locale.US, "%.3f", state.elapsedSeconds)} s"
                        )
                    }
                }
            }

            Text(
                "Consejo: coloca el LED dentro del recuadro pequeño y procura que el recuadro grande vea el entorno inmediato. El filtro solar usa esa diferencia para evitar contar cambios generales de luz como pulsos.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatSeconds(seconds: Double): String =
    String.format(Locale.US, "%.3f s", seconds)
