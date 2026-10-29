package com.afterplay.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.Surface as AndroidSurface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.afterplay.app.PlayerViewModel

@Composable
internal fun HyperlapseButton(model: PlayerViewModel) {
    val context = LocalContext.current
    val state by model.hyperlapse.state.collectAsStateWithLifecycle()
    val timer by model.pomodoro.collectAsStateWithLifecycle()
    var preview by rememberSaveable { mutableStateOf(false) }
    val permissions = remember { buildList {
        add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }.toTypedArray() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (permissions.all { result[it] == true }) preview = true
        else model.message.value = "하이퍼랩스를 켜려면 카메라${if (Build.VERSION.SDK_INT <= 28) "와 저장 공간" else ""} 권한이 필요해요."
    }
    val label = when {
        state.saving -> "하이퍼랩스 저장 중 ${state.progress}%"
        state.recording -> "하이퍼랩스 촬영 중 · 끄기"
        state.enabled -> "하이퍼랩스 활성화 · 끄기"
        else -> "하이퍼랩스 켜기"
    }
    IconButton(onClick = {
        if (state.enabled) model.hyperlapse.disable()
        else if (timer.running) model.message.value = "집중을 일시정지한 뒤 하이퍼랩스를 켜 주세요."
        else if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) preview = true
        else permission.launch(permissions)
    }, enabled = !state.saving, modifier = Modifier.size(40.dp).testTag("hyperlapse_toggle").semantics {
        stateDescription = when { state.saving -> "저장 중"; state.recording -> "촬영 중"; state.enabled -> "활성화"; else -> "꺼짐" }
    }) {
        if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Icon(if (state.enabled) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, label,
            tint = if (state.recording) Color(0xFFD32F2F) else if (state.enabled) Accent else Muted,
            modifier = Modifier.size(22.dp))
    }
    if (preview) HyperlapsePreviewDialog(onCancel = { preview = false }, onConfirm = { rotation ->
        model.hyperlapse.arm(rotation)
        preview = false
    })
}

@Composable
internal fun HyperlapsePreviewDialog(onCancel: () -> Unit, onConfirm: (Int) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycle, previewView) {
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var useCase: Preview? = null
        val future = ProcessCameraProvider.getInstance(context)
        val observer = androidx.lifecycle.Observer<PreviewView.StreamState> { ready = it == PreviewView.StreamState.STREAMING }
        previewView.previewStreamState.observe(lifecycle, observer)
        future.addListener({
            if (!disposed) try {
                provider = future.get()
                val preview = Preview.Builder().setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
                    .setTargetRotation(previewView.display?.rotation ?: AndroidSurface.ROTATION_90).build()
                useCase = preview
                preview.setSurfaceProvider(previewView.surfaceProvider)
                provider!!.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
            } catch (failure: Exception) { error = "전면 카메라를 열 수 없어요. 권한과 카메라 사용 상태를 확인해 주세요." }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed = true
            previewView.previewStreamState.removeObserver(observer)
            useCase?.let { provider?.unbind(it) }
        }
    }
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.9f).fillMaxHeight(.88f).testTag("hyperlapse_preview_dialog"),
            shape = RoundedCornerShape(24.dp), color = Surface) {
            BoxWithConstraints(Modifier.padding(16.dp)) {
                val square = minOf(maxHeight, maxWidth * .52f)
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(square).clipToBounds().background(Color.Black).testTag("hyperlapse_preview"), contentAlignment = Alignment.Center) {
                        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().clipToBounds())
                        if (!ready && error == null) CircularProgressIndicator()
                    }
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                        Text("하이퍼랩스", style = MaterialTheme.typography.titleLarge)
                        Text(error ?: "전면 카메라 · 1:1 · 무음\n집중 1회당 15초 + 마지막 집계 3초\n각 촬영 컷을 기기 안에서 분석합니다.\n얼굴 감지: 집중 / 미감지: 이탈\n준비·휴식 중에는 촬영하지 않습니다.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (error != null) MaterialTheme.colorScheme.error else Muted)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onCancel, modifier = Modifier.testTag("hyperlapse_cancel")) { Text("취소") }
                            Button(onClick = { onConfirm(previewView.display?.rotation ?: AndroidSurface.ROTATION_90) }, enabled = ready && error == null,
                                modifier = Modifier.testTag("hyperlapse_confirm")) { Text("OK") }
                        }
                    }
                }
            }
        }
    }
}
