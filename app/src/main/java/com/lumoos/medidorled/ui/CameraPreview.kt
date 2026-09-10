package com.lumoos.medidorled.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.lumoos.medidorled.camera.DisplayRegion
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    analyzer: ImageAnalysis.Analyzer,
    displayMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // The analyzer instance remains stable when the mode changes.
    DisposableEffect(analyzer, lifecycleOwner) {
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var disposed = false
        var boundPreview: Preview? = null
        var boundAnalysis: ImageAnalysis? = null
        providerFuture.addListener({
            if (!disposed) {
                previewView.doOnLayout {
                    if (!disposed) {
                        runCatching {
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                                .build()
                                .also { it.setAnalyzer(analysisExecutor, analyzer) }
                            // A shared viewport maps cropRect to the same visible sensor area.
                            val group = UseCaseGroup.Builder()
                                .addUseCase(preview)
                                .addUseCase(analysis)
                                .setViewPort(requireNotNull(previewView.viewPort))
                                .build()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                            boundPreview = preview
                            boundAnalysis = analysis
                        }.onFailure { android.util.Log.e("LumoosCamera", "No se pudo iniciar la cámara", it) }
                    }
                }
            }
        }, mainExecutor)
        onDispose {
            disposed = true
            boundAnalysis?.clearAnalyzer()
            if (providerFuture.isDone) {
                runCatching {
                    val provider = providerFuture.get()
                    boundPreview?.let { provider.unbind(it) }
                    boundAnalysis?.let { provider.unbind(it) }
                }
            }
            analysisExecutor.shutdown()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        if (displayMode) {
            Canvas(Modifier.fillMaxSize()) {
                fun square(fraction: Float, color: Color, strokeWidth: Float) {
                    val side = size.minDimension * fraction
                    drawRect(color, Offset((size.width-side)/2, (size.height-side)/2), Size(side,side), style = Stroke(strokeWidth))
                }
                square(DisplayRegion.OUTER_FRACTION, Color.White.copy(alpha = 0.45f), 1.dp.toPx())
                square(DisplayRegion.CENTER_FRACTION, Color.Yellow, 1.dp.toPx())
            }
        } else {
            Box(Modifier.size(138.dp).border(1.dp, Color.White.copy(alpha = 0.45f)))
            Box(Modifier.size(74.dp).border(2.dp, Color.White))
        }
    }
}
