package com.weili.overtimetracker.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Size
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PermissionStatus(
    val cameraGranted: Boolean,
    val locationGranted: Boolean,
)

private data class LocationUiState(
    val coordinates: GeoCoordinates? = null,
    val servicesEnabled: Boolean = true,
)

@Composable
fun WatermarkCameraDialog(
    onDismiss: () -> Unit,
    onPhotoCaptured: (android.net.Uri) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var permissionStatus by remember { mutableStateOf(readPermissionStatus(context)) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isCapturing by remember { mutableStateOf(false) }
    var activeCaptureId by remember { mutableStateOf<Int?>(null) }
    var nextCaptureId by remember { mutableIntStateOf(0) }
    var cameraRestartKey by remember { mutableIntStateOf(0) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var resumeCount by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        permissionStatus = readPermissionStatus(context)
    }
    val locationState by rememberLocationState(permissionStatus.locationGranted, resumeCount)
    var currentTime by remember { mutableStateOf(LocalDateTime.now()) }

    LaunchedEffect(Unit) {
        if (!permissionStatus.cameraGranted || !permissionStatus.locationGranted) {
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
        while (true) {
            currentTime = LocalDateTime.now()
            delay(1_000)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionStatus = readPermissionStatus(context)
                resumeCount += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val closeCamera = {
        if (!isCapturing) onDismiss()
    }
    BackHandler(enabled = true, onBack = closeCamera)

    Dialog(
        onDismissRequest = closeCamera,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (permissionStatus.cameraGranted) {
                CameraPreview(
                    restartKey = cameraRestartKey,
                    onImageCaptureChanged = { imageCapture = it },
                    onCameraError = { cameraError = it },
                )
            }

            Surface(
                color = Color.Black.copy(alpha = 0.48f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .align(Alignment.BottomCenter),
            ) {}

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.58f),
                ) {
                    IconButton(onClick = closeCamera, enabled = !isCapturing) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭相机", tint = Color.White)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text("水印相机", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }

            if (permissionStatus.cameraGranted && permissionStatus.locationGranted) {
                WatermarkPreview(
                    coordinates = locationState.coordinates,
                    currentTime = currentTime,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, bottom = 18.dp),
                )
            }

            if (!permissionStatus.cameraGranted || !permissionStatus.locationGranted) {
                PermissionNotice(
                    cameraMissing = !permissionStatus.cameraGranted,
                    locationMissing = !permissionStatus.locationGranted,
                    onRequestPermission = { permissionLauncher.launch(REQUIRED_PERMISSIONS) },
                    onOpenAppSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                "package:${context.packageName}".toUri(),
                            ),
                        )
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
            } else if (!locationState.servicesEnabled) {
                LocationServicesNotice(
                    onOpenSettings = {
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            cameraError?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 64.dp, start = 16.dp, end = 16.dp),
                ) {
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = if (locationState.coordinates != null && imageCapture != null) {
                    Color.White
                } else {
                    Color.White.copy(alpha = 0.42f)
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 18.dp),
            ) {
                IconButton(
                    onClick = {
                        val capture = imageCapture ?: return@IconButton
                        val coordinates = locationState.coordinates ?: return@IconButton
                        val snapshot = WatermarkSnapshot(coordinates, LocalDateTime.now())
                        val rawFile = WatermarkPhotoProcessor.createRawCaptureFile(context)
                        val captureId = nextCaptureId + 1
                        nextCaptureId = captureId
                        activeCaptureId = captureId
                        isCapturing = true
                        cameraError = null
                        val options = ImageCapture.OutputFileOptions.Builder(rawFile).build()
                        val timeoutJob = scope.launch {
                            delay(CAPTURE_TIMEOUT_MS)
                            if (activeCaptureId == captureId) {
                                activeCaptureId = null
                                isCapturing = false
                                rawFile.delete()
                                cameraError = "相机响应超时，已自动恢复，请重试"
                                cameraRestartKey += 1
                            }
                        }
                        runCatching {
                            capture.takePicture(
                                options,
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageSavedCallback {
                                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                        if (activeCaptureId != captureId) {
                                            rawFile.delete()
                                            return
                                        }
                                        timeoutJob.cancel()
                                        scope.launch {
                                            val result = withContext(Dispatchers.IO) {
                                                runCatching {
                                                    WatermarkPhotoProcessor.addWatermark(
                                                        context,
                                                        rawFile,
                                                        snapshot,
                                                    )
                                                }
                                            }
                                            result.onSuccess { photo ->
                                                activeCaptureId = null
                                                onPhotoCaptured(android.net.Uri.fromFile(photo))
                                            }.onFailure {
                                                activeCaptureId = null
                                                rawFile.delete()
                                                cameraError = "水印照片保存失败，请重试"
                                                isCapturing = false
                                            }
                                        }
                                    }

                                    override fun onError(exception: ImageCaptureException) {
                                        if (activeCaptureId != captureId) return
                                        timeoutJob.cancel()
                                        activeCaptureId = null
                                        rawFile.delete()
                                        cameraError = "拍照失败，请重试"
                                        isCapturing = false
                                    }
                                },
                            )
                        }.onFailure {
                            timeoutJob.cancel()
                            activeCaptureId = null
                            rawFile.delete()
                            cameraError = "无法开始拍照，请重试"
                            isCapturing = false
                        }
                    },
                    enabled = !isCapturing && imageCapture != null && locationState.coordinates != null,
                    modifier = Modifier.size(72.dp),
                ) {
                    if (isCapturing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(30.dp),
                            color = Color.Black,
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.CameraAlt,
                            contentDescription = "拍摄水印照片",
                            tint = Color.Black,
                            modifier = Modifier.size(32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatermarkPreview(
    coordinates: GeoCoordinates?,
    currentTime: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.68f),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.widthIn(max = 260.dp),
    ) {
        Row {
            Spacer(
                Modifier
                    .width(4.dp)
                    .height(if (coordinates == null) 48.dp else 104.dp)
                    .background(Color(0xFFFFC107)),
            )
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (coordinates == null) {
                    Text("正在获取经纬度…", color = Color.White, fontSize = 14.sp)
                } else {
                    WatermarkFormatter.lines(WatermarkSnapshot(coordinates, currentTime)).forEach { line ->
                        Text(line, color = Color.White, fontSize = 13.sp, lineHeight = 17.sp)
                    }
                    Text(
                        WatermarkFormatter.accuracyLabel(coordinates),
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionNotice(
    cameraMissing: Boolean,
    locationMissing: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val missing = buildList {
        if (cameraMissing) add("相机")
        if (locationMissing) add("位置")
    }.joinToString("和")
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.padding(24.dp).widthIn(max = 340.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.LocationOff, contentDescription = null)
            Text("需要${missing}权限才能拍摄带经纬度的水印照片")
            Button(onClick = onRequestPermission) { Text("授权并继续") }
            TextButton(onClick = onOpenAppSettings) { Text("打开应用设置") }
        }
    }
}

@Composable
private fun LocationServicesNotice(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.padding(24.dp).widthIn(max = 340.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.LocationOff, contentDescription = null)
            Text("系统定位服务未开启，暂时无法生成经纬度水印")
            Button(onClick = onOpenSettings) { Text("打开定位设置") }
        }
    }
}

@Composable
private fun CameraPreview(
    restartKey: Int,
    onImageCaptureChanged: (ImageCapture?) -> Unit,
    onCameraError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val currentReadyCallback by rememberUpdatedState(onImageCaptureChanged)
    val currentErrorCallback by rememberUpdatedState(onCameraError)
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val preview = remember(restartKey) { Preview.Builder().build() }
    val imageCapture = remember(restartKey) {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(1_920, 1_080),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER,
                        ),
                    )
                    .build(),
            )
            .build()
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize(),
    )

    LaunchedEffect(configuration.orientation, restartKey) {
        previewView.post {
            val rotation = previewView.display?.rotation ?: Surface.ROTATION_0
            preview.targetRotation = rotation
            imageCapture.targetRotation = rotation
        }
    }

    DisposableEffect(context, lifecycleOwner, previewView, restartKey) {
        val future = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        var disposed = false
        future.addListener(
            {
                if (disposed) return@addListener
                runCatching {
                    val provider = future.get()
                    preview.surfaceProvider = previewView.surfaceProvider
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                    currentReadyCallback(imageCapture)
                }.onFailure {
                    currentReadyCallback(null)
                    currentErrorCallback("无法启动相机，请确认设备相机可用")
                }
            },
            mainExecutor,
        )
        onDispose {
            disposed = true
            currentReadyCallback(null)
            if (future.isDone) runCatching { future.get().unbindAll() }
        }
    }

}

@SuppressLint("MissingPermission")
@Composable
private fun rememberLocationState(enabled: Boolean, refreshKey: Int): State<LocationUiState> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(LocationUiState()) }
    DisposableEffect(context, enabled, refreshKey) {
        if (!enabled) {
            state.value = LocationUiState()
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        @Suppress("OVERRIDE_DEPRECATION")
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                state.value = LocationUiState(
                    coordinates = GeoCoordinates(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy,
                    ),
                    servicesEnabled = true,
                )
            }

            override fun onProviderEnabled(provider: String) {
                state.value = state.value.copy(servicesEnabled = true)
            }

            override fun onProviderDisabled(provider: String) {
                state.value = state.value.copy(servicesEnabled = manager.areLocationServicesEnabled())
            }

            @Deprecated("Deprecated in Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val providers = buildList {
            if (hasFineLocation && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
        }
        state.value = LocationUiState(servicesEnabled = manager.areLocationServicesEnabled())
        val freshestLastLocation = providers.mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }.maxByOrNull(Location::getTime)
        if (freshestLastLocation != null &&
            System.currentTimeMillis() - freshestLastLocation.time <= LAST_LOCATION_MAX_AGE_MS
        ) {
            listener.onLocationChanged(freshestLastLocation)
        }
        providers.forEach { provider ->
            runCatching {
                manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
            }
        }
        onDispose { runCatching { manager.removeUpdates(listener) } }
    }
    return state
}

private fun readPermissionStatus(context: Context) = PermissionStatus(
    cameraGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED,
    locationGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED,
)

private fun LocationManager.areLocationServicesEnabled(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        isLocationEnabled
    } else {
        isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

private val REQUIRED_PERMISSIONS = arrayOf(
    Manifest.permission.CAMERA,
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

private const val LAST_LOCATION_MAX_AGE_MS = 5 * 60 * 1_000L
private const val CAPTURE_TIMEOUT_MS = 15_000L
