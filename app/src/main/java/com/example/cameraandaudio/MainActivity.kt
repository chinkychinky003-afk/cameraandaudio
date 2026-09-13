package com.example.cameraandaudio

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.cameraandaudio.ui.theme.CameraAndAudioManualControlTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private var cameraControl: androidx.camera.core.CameraControl? = null
    private var camera2CameraControl: Camera2CameraControl? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecordingAudio by mutableStateOf(false)
    private var audioFilePath: String? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!hasPermissions()) {
            permissionLauncher.launch(requiredPermissions)
        }

        setContent {
            CameraAndAudioManualControlTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraAndAudioScreen(
                        onCameraReady = { control, camera2Control ->
                            cameraControl = control
                            camera2CameraControl = camera2Control
                        },
                        isRecordingAudio = isRecordingAudio,
                        onToggleAudioRecording = { toggleAudioRecording() },
                        onZoomChange = { ratio -> cameraControl?.setZoomRatio(ratio) },
                        onExposureChange = { index -> cameraControl?.setExposureCompensationIndex(index) },
                        onTorchToggle = { enabled -> cameraControl?.enableTorch(enabled) },
                        onIsoChange = { iso -> setManualIso(iso) }
                    )
                }
            }
        }
    }

    private fun hasPermissions(): Boolean = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun setManualIso(iso: Int) {
        val control = camera2CameraControl ?: return
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(
                android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE,
                android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE_OFF
            )
            .setCaptureRequestOption(
                android.hardware.camera2.CaptureRequest.SENSOR_SENSITIVITY,
                iso
            )
            .build()
        control.setCaptureRequestOptions(options)
    }

    private fun toggleAudioRecording() {
        if (isRecordingAudio) {
            stopAudioRecording()
        } else {
            startAudioRecording()
        }
    }

    private fun startAudioRecording() {
        val outputDir = getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: filesDir
        val fileName = "AUDIO_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
        val outputFile = File(outputDir, fileName)
        audioFilePath = outputFile.absolutePath

        mediaRecorder = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFilePath)
            prepare()
            start()
        }
        isRecordingAudio = true
    }

    private fun stopAudioRecording() {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            // Recording was too short or failed; ignore for now.
        }
        mediaRecorder?.release()
        mediaRecorder = null
        isRecordingAudio = false
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecordingAudio) stopAudioRecording()
    }
}

@Composable
fun CameraAndAudioScreen(
    onCameraReady: (androidx.camera.core.CameraControl, Camera2CameraControl) -> Unit,
    isRecordingAudio: Boolean,
    onToggleAudioRecording: () -> Unit,
    onZoomChange: (Float) -> Unit,
    onExposureChange: (Int) -> Unit,
    onTorchToggle: (Boolean) -> Unit,
    onIsoChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    var zoom by remember { mutableStateOf(1f) }
    var exposure by remember { mutableStateOf(0f) }
    var iso by remember { mutableStateOf(400f) }
    var torchOn by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val imageCapture = ImageCapture.Builder().build()
                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, cameraSelector, preview, imageCapture
                        )

                        @androidx.camera.camera2.interop.ExperimentalCamera2Interop
                        val camera2Control = Camera2CameraControl.from(camera.cameraControl)
                        onCameraReady(camera.cameraControl, camera2Control)
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colors.surface)
                .padding(16.dp)
        ) {
            Text("Zoom: ${"%.1f".format(zoom)}x", style = MaterialTheme.typography.body2)
            Slider(
                value = zoom,
                onValueChange = {
                    zoom = it
                    onZoomChange(it)
                },
                valueRange = 1f..10f
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("Exposure compensation: ${exposure.toInt()}", style = MaterialTheme.typography.body2)
            Slider(
                value = exposure,
                onValueChange = {
                    exposure = it
                    onExposureChange(it.toInt())
                },
                valueRange = -4f..4f
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text("ISO (manual): ${iso.toInt()}", style = MaterialTheme.typography.body2)
            Slider(
                value = iso,
                onValueChange = {
                    iso = it
                    onIsoChange(it.toInt())
                },
                valueRange = 100f..1600f
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Flash", style = MaterialTheme.typography.body2)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = torchOn,
                        onCheckedChange = {
                            torchOn = it
                            onTorchToggle(it)
                        }
                    )
                }

                Button(onClick = onToggleAudioRecording) {
                    Text(if (isRecordingAudio) "Stop Audio" else "Record Audio")
                }
            }
        }
    }
}
