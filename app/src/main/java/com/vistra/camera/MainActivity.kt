package com.vistra.camera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.vistra.camera.rtsp.RtspServer
import com.vistra.camera.rtsp.H264Encoder

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            setContent { VistraApp() }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val camera =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

        val audio =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (camera && audio) {
            setContent { VistraApp() }
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }
    }
}

@Composable
fun VistraApp() {

    val rtspServer = remember { RtspServer(8554) }
    var streaming by remember { mutableStateOf(false) }

    var cameraFacing by remember {
        mutableStateOf(CameraSelector.LENS_FACING_BACK)
    }

    var flashMode by remember {
        mutableStateOf("AUTO")
    }

    var zoom by remember {
        mutableFloatStateOf(1f)
    }

    var exposure by remember {
        mutableFloatStateOf(0f)
    }

    var camera by remember {
        mutableStateOf<Camera?>(null)
    }

    val h264Encoder = remember { H264Encoder() }
    var encoderSurface by remember { mutableStateOf<Surface?>(null) }

LaunchedEffect(Unit) {
    encoderSurface = h264Encoder.start()
}

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF080B10)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {

                Text(
                    text = "VISTRA",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "INTELLIGENT NETWORK CAMERA",
                    color = Color(0xFF7D8A99),
                    style = MaterialTheme.typography.labelSmall
                )

                Spacer(Modifier.height(14.dp))

                CameraPreview(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    lensFacing = cameraFacing,
                    onCameraReady = {
                        camera = it
                    },
                    encoderSurface = encoderSurface
                )

                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            cameraFacing =
                                if (cameraFacing ==
                                    CameraSelector.LENS_FACING_BACK
                                ) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                        }
                    ) {
                        Text(
                            if (cameraFacing ==
                                CameraSelector.LENS_FACING_BACK
                            ) "FRONT"
                            else "BACK"
                        )
                    }

                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            flashMode =
                                when (flashMode) {
                                    "AUTO" -> "ON"
                                    "ON" -> "OFF"
                                    else -> "AUTO"
                                }
                        },
                        enabled = camera?.cameraInfo?.hasFlashUnit() == true
                    ) {
                        Text("FLASH $flashMode")
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    "ZOOM  ${"%.1fx".format(zoom)}",
                    color = Color.White
                )

                Slider(
                    value = zoom,
                    onValueChange = {
                        zoom = it
                        camera?.cameraControl?.setZoomRatio(it)
                    },
                    valueRange = 1f..8f,
                    enabled = camera != null
                )

                Text(
                    "EXPOSURE  ${if (exposure > 0) "+" else ""}${exposure.toInt()}",
                    color = Color.White
                )

                Slider(
                    value = exposure,
                    onValueChange = {
                        exposure = it
                        camera?.cameraControl?.setExposureCompensationIndex(
                            it.toInt()
                        )
                    },
                    valueRange = -3f..3f,
                    steps = 5,
                    enabled = camera != null
                )

                Spacer(Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InfoCard(
                        Modifier.weight(1f),
                        "RTSP",
                        "STOPPED"
                    )

                    InfoCard(
                        Modifier.weight(1f),
                        "ONVIF",
                        "READY"
                    )
                }

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (streaming) {
                            rtspServer.stop()
                            streaming = false
                        } else {
                            rtspServer.start()
                            streaming = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(if (streaming) "STOP STREAM" else "START STREAM")
                }
            }
        }
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    lensFacing: Int,
    onCameraReady: (Camera) -> Unit, encoderSurface: Surface? = null
) {

    val context = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = {

            PreviewView(context).apply {

                scaleType =
                    PreviewView.ScaleType.FILL_CENTER

                val providerFuture =
                    ProcessCameraProvider.getInstance(context)

                providerFuture.addListener({

                    val provider =
                        providerFuture.get()

                    val preview =
                        Preview.Builder().build()

                    preview.surfaceProvider =
                        surfaceProvider

                    val selector =
                        CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()

                    try {

                        provider.unbindAll()

                        val camera =
                            provider.bindToLifecycle(
                                context as androidx.lifecycle.LifecycleOwner,
                                selector,
                                preview,
                            )

                        onCameraReady(camera)

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                }, ContextCompat.getMainExecutor(context))
            }
        },
        update = {

            val providerFuture =
                ProcessCameraProvider.getInstance(context)

            providerFuture.addListener({

                val provider =
                    providerFuture.get()

                val preview =
                    Preview.Builder().build()

                preview.surfaceProvider =
                    it.surfaceProvider

                val selector =
                    CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                try {

                    provider.unbindAll()

                    val camera =
                        provider.bindToLifecycle(
                            context as androidx.lifecycle.LifecycleOwner,
                            selector,
                            preview
                        )

                    onCameraReady(camera)

                } catch (e: Exception) {
                    e.printStackTrace()
                }

            }, ContextCompat.getMainExecutor(context))
        }
    )
}

@Composable
fun InfoCard(
    modifier: Modifier,
    title: String,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF151B23)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                title,
                color = Color(0xFF7D8A99),
                style = MaterialTheme.typography.labelSmall
            )

            Spacer(Modifier.height(4.dp))

            Text(
                value,
                color = Color.White
            )
        }
    }
}
