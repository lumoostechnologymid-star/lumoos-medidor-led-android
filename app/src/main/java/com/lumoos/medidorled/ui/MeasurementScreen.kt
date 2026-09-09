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
import androidx.compose.material3.Surface
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
        topBar = { TopAppBar(title = { Text("Lumoos Medidor LED") }) },
        bottomBar = {
            MeasurementBottomControls(
                state = state,
                cameraGranted = cameraGranted,
                onStart = controller::armMeasurement,
                onReset = controller::reset,
                onCalibrate = controller::startCalibration
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // La cámara queda fuera del área desplazable para que nunca cambie de posición
            // al buscar o tocar los controles de medición.
            if (cameraGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Box(modifier = Modifier.height(270.dp)) {
                        CameraPreview(analyzer = analyzer, modifier = Modifier.fillMaxSize())
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Se necesita permiso de cámara para detectar el LED.")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Dar permiso")
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LedStatusCard(state)
                MeasurementSettingsCard(state, controller)
                DetectionCard(state, controller)

                state.resultKw?.let { result ->
                    ResultCard(state = state, result = result)
                }

                Text(
                    "La cámara permanece fija arriba. Puedes revisar o modificar los controles sin mover el encuadre del LED; el botón Iniciar medición siempre queda abajo.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LedStatusCard(state: MeasurementUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Estado del LED", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
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
            MetricRow("Señal actual", String.format(Locale.US, "%.1f", state.signal))
            MetricRow("ENCENDIDO desde", String.format(Locale.US, "%.1f", state.onThreshold))
            MetricRow("APAGADO debajo de", String.format(Locale.US, "%.1f", state.offThreshold))
            MetricRow("Vueltas", "${state.revolutions} / ${state.targetRevolutions}")
            MetricRow("Tiempo total", formatSeconds(state.elapsedSeconds))
            MetricRow("Última vuelta", formatSeconds(state.lastRevolutionSeconds))
            Text(state.status, style = MaterialTheme.typography.bodyMedium)
            state.message?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun MeasurementSettingsCard(
    state: MeasurementUiState,
    controller: LedMeasurementController
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Configuración de medición", fontWeight = FontWeight.SemiBold)

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
        }
    }
}

@Composable
private fun DetectionCard(
    state: MeasurementUiState,
    controller: LedMeasurementController
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Detección del LED", fontWeight = FontWeight.SemiBold)
            Text("Filtro solar: ACTIVO · compara el centro con la luz alrededor")
            MetricRow("Brillo del centro", String.format(Locale.US, "%.1f", state.brightness))
            MetricRow("Señal actual", String.format(Locale.US, "%.1f", state.signal))
            MetricRow("ENCENDIDO desde", String.format(Locale.US, "%.1f", state.onThreshold))
            MetricRow("APAGADO debajo de", String.format(Locale.US, "%.1f", state.offThreshold))

            LinearProgressIndicator(
                progress = { (state.signal / 255f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Umbral central: ${String.format(Locale.US, "%.1f", state.threshold)}")
            Slider(
                value = state.threshold,
                onValueChange = controller::setThreshold,
                valueRange = 0f..255f,
                steps = 254,
                enabled = !state.locked && !state.calibrating
            )

            if (state.calibrating) {
                LinearProgressIndicator(
                    progress = { state.calibrationProgress },
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Calibrando…", style = MaterialTheme.typography.bodySmall)
            }

            Text(
                "Ajusta el umbral entre la señal apagada y la señal prendida. La calibración automática también puede hacerlo por ti.",
                style = MaterialTheme.typography.bodySmall
            )

            if (state.ledColorMode == LedColorMode.INFRARED) {
                Text(
                    "Nota: algunos teléfonos tienen un filtro físico que reduce la luz infrarroja; si la señal casi no cambia, prueba acercando la cámara.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun MeasurementBottomControls(
    state: MeasurementUiState,
    cameraGranted: Boolean,
    onStart: () -> Unit,
    onReset: () -> Unit,
    onCalibrate: () -> Unit
) {
    Surface(tonalElevation = 4.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onStart,
                enabled = cameraGranted && !state.locked && !state.calibrating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    when {
                        state.measuring -> "Medición en curso"
                        state.armed -> "Esperando pulso del LED"
                        else -> "▶  Iniciar medición"
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reiniciar")
                }
                OutlinedButton(
                    onClick = onCalibrate,
                    enabled = !state.locked && !state.calibrating,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (state.calibrating) "Calibrando…" else "Calibrar")
                }
            }
        }
    }
}

@Composable
private fun ResultCard(state: MeasurementUiState, result: Double) {
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

@Composable
private fun MetricRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatSeconds(seconds: Double): String =
    String.format(Locale.US, "%.3f s", seconds)
