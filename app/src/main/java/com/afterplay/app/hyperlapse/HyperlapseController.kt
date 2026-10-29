package com.afterplay.app.hyperlapse

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.afterplay.app.data.PomodoroPhase
import com.afterplay.app.data.PomodoroRound
import com.afterplay.app.data.PomodoroSession
import com.afterplay.app.playback.PomodoroTimer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

data class HyperlapseState(
    val enabled: Boolean = false,
    val recording: Boolean = false,
    val saving: Boolean = false,
    val progress: Int = 0,
    val savedUri: Uri? = null,
    val message: String? = null,
)

/** Camera ownership belongs to the foreground service, independently of Activity recreation. */
class HyperlapseController(private val context: Context, private val timer: PomodoroTimer) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val worker = Executors.newSingleThreadExecutor()
    private val dispatcher = worker.asCoroutineDispatcher()
    private val mutableState = MutableStateFlow(HyperlapseState())
    val state = mutableState.asStateFlow()
    private var owner: LifecycleOwner? = null
    private var stopService: (() -> Unit)? = null
    private var updateNotification: ((String, Boolean) -> Unit)? = null
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var binding = false
    private var spool: HyperlapseFrames? = null
    @Volatile private var captureSpool: HyperlapseFrames? = null
    private var presenceDetector: HyperlapsePresenceDetector? = null // Accessed only on worker.
    private var rotation = Surface.ROTATION_90
    private var before = timer.state.value
    private val root = File(context.filesDir, "hyperlapse")

    init {
        scope.launch {
            // An interrupted capture is saved as a partial clip, never silently resumed.
            val recovered = withContext(dispatcher) { root.listFiles()?.filter { it.isDirectory }.orEmpty() }
            if (recovered.isNotEmpty()) {
                mutableState.value = state.value.copy(saving = true)
                for (directory in recovered) export(HyperlapseFrames(directory))
                mutableState.value = state.value.copy(saving = false, enabled = false)
            }
            timer.state.collect { after ->
                val previous = before
                before = after
                spool?.let { frames ->
                    after.roundResults.forEachIndexed { index, result ->
                        if (result == PomodoroRound.COMPLETED && previous.roundResults.getOrNull(index) != result)
                            worker.execute {
                                try { frames.complete(index) }
                                catch (error: Exception) { scope.launch { fail("회차 기록 저장에 실패했어요. 수집된 영상은 보관합니다.") } }
                            }
                    }
                    if (after.finished || after.phase == PomodoroPhase.LONG_BREAK) finish()
                }
                syncCamera(after)
            }
        }
    }

    fun arm(displayRotation: Int) {
        if (state.value.saving || timer.state.value.running || spool != null) return
        rotation = displayRotation
        mutableState.value = state.value.copy(enabled = true, message = "하이퍼랩스가 켜졌어요. 집중 시작부터 촬영합니다.")
    }

    fun disable() {
        mutableState.value = state.value.copy(enabled = false)
        finish()
    }

    /** Called by a visible Activity's start action, before the timer is allowed to run. */
    fun prepareStart(): Boolean {
        if (!state.value.enabled) return true
        if (state.value.saving) { message("영상 저장이 끝난 뒤 집중을 시작해 주세요."); return false }
        val current = timer.state.value
        if (!current.finished && current.phase != PomodoroPhase.FOCUS) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            mutableState.value = state.value.copy(enabled = false, message = "카메라 권한을 허용한 뒤 하이퍼랩스를 다시 켜 주세요.")
            return false
        }
        if (spool == null) {
            try {
                spool = HyperlapseFrames(File(root, "${System.currentTimeMillis()}-${UUID.randomUUID()}"))
                ContextCompat.startForegroundService(context, Intent(context, HyperlapseService::class.java))
            } catch (error: Exception) {
                spool?.clear(); spool = null
                mutableState.value = state.value.copy(enabled = false, message = "촬영을 시작하지 못했어요. 앱 화면에서 다시 시도해 주세요.")
                return false
            }
        }
        return true
    }

    internal fun attach(serviceOwner: LifecycleOwner, stop: () -> Unit, notify: (String, Boolean) -> Unit) {
        owner = serviceOwner
        stopService = stop
        updateNotification = notify
        syncCamera(timer.state.value)
    }

    internal fun detach(serviceOwner: LifecycleOwner) {
        if (owner !== serviceOwner) return
        closeCamera()
        owner = null; stopService = null; updateNotification = null
        if (spool != null) {
            mutableState.value = state.value.copy(enabled = false)
            finish()
        }
    }

    private fun syncCamera(session: PomodoroSession) {
        val active = state.value.enabled && spool != null && !state.value.saving &&
            session.running && !session.preparing && !session.finished && session.phase == PomodoroPhase.FOCUS
        if (active && owner != null) {
            captureSpool = spool
            mutableState.value = state.value.copy(recording = true)
            updateNotification?.invoke("집중 ${session.round}회차 · 무음 하이퍼랩스 촬영 중", false)
            if (analysis == null && !binding) bindCamera()
        } else {
            closeCamera()
            mutableState.value = state.value.copy(recording = false)
            if (spool != null && !state.value.saving) updateNotification?.invoke(
                if (session.preparing) "10초 준비 중 · 집중 시작부터 촬영합니다"
                else "휴식·일시정지 중 · 다음 집중 때 촬영을 이어갑니다", false)
        }
    }

    private fun bindCamera() {
        binding = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            binding = false
            val lifecycleOwner = owner ?: return@addListener
            if (captureSpool == null || analysis != null) return@addListener
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val stream = ImageAnalysis.Builder().setTargetResolution(android.util.Size(960, 720))
                    .setTargetRotation(rotation).setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                stream.setAnalyzer(worker) { image ->
                    try {
                        val frames = captureSpool ?: return@setAnalyzer
                        val now = System.currentTimeMillis()
                        val session = timer.state.value.advance(now)
                        if (session.running && !session.preparing && !session.finished && session.phase == PomodoroPhase.FOCUS) {
                            val slot = HyperlapsePlan.slot(session.durationMs, session.remainingMs)
                            if (!frames.hasSlot(session.roundIndex, slot)) {
                                val detector = presenceDetector ?: HyperlapsePresenceDetector().also { presenceDetector = it }
                                frames.capture(image, session.roundIndex, slot, now, detector::isPresent)
                            }
                        }
                    } catch (error: Exception) {
                        scope.launch { fail("촬영이 중단됐어요. 수집된 영상은 저장합니다.") }
                    } finally { image.close() }
                }
                analysis = stream
                val camera = cameraProvider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, stream)
                camera.cameraInfo.cameraState.observe(lifecycleOwner) { status ->
                    if (status.error != null && analysis === stream) fail("전면 카메라를 사용할 수 없어요. 수집된 영상은 저장합니다.")
                }
            } catch (error: Exception) { fail("전면 카메라를 열지 못했어요. 카메라 사용 상태를 확인해 주세요.") }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun closeCamera() {
        captureSpool = null
        analysis?.let { stream -> stream.clearAnalyzer(); provider?.unbind(stream) }
        analysis = null
    }

    private fun fail(text: String) {
        mutableState.value = state.value.copy(enabled = false, message = text)
        finish()
    }

    /** Reset, disabling and skipping the final focus also preserve the frames already collected. */
    fun finish() {
        val frames = spool ?: return
        spool = null
        closeCamera()
        worker.execute { presenceDetector?.close(); presenceDetector = null }
        mutableState.value = state.value.copy(recording = false, saving = true, progress = 0)
        updateNotification?.invoke("하이퍼랩스 영상 저장 중", true)
        scope.launch {
            try { export(frames) }
            finally {
                mutableState.value = state.value.copy(saving = false)
                val stop = stopService
                stopService = null
                owner = null
                updateNotification = null
                stop?.invoke()
            }
        }
    }

    private suspend fun export(frames: HyperlapseFrames) {
        try {
            val uri = withContext(dispatcher) {
                val sources = frames.exportFrames()
                if (sources.isEmpty()) { frames.clear(); return@withContext null }
                val output = File(frames.directory, "output.mp4")
                HyperlapseEncoder.encode(sources, output) { progress ->
                    mutableState.value = state.value.copy(progress = progress)
                }
                val saved = publish(output, frames.directory.name.substringBefore('-').toLong())
                frames.clear()
                saved
            }
            if (uri != null) mutableState.value = state.value.copy(savedUri = uri,
                message = "하이퍼랩스 영상을 갤러리의 AfterPlay 폴더에 저장했어요.")
        } catch (error: Exception) {
            mutableState.value = state.value.copy(enabled = false,
                message = "영상 저장에 실패했어요. 수집한 장면은 보관하며 다음 앱 실행 때 다시 저장합니다.")
        }
    }

    private fun publish(file: File, startedAt: Long): Uri {
        val resolver = context.contentResolver
        val name = "AfterPlay_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(startedAt))}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AfterPlay")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            } else {
                val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "AfterPlay")
                check(directory.isDirectory || directory.mkdirs())
                put(MediaStore.Video.Media.DATA, File(directory, name).absolutePath)
            }
        }
        val uri = requireNotNull(resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values))
        try {
            requireNotNull(resolver.openOutputStream(uri)).use { output -> file.inputStream().use { it.copyTo(output) } }
            if (Build.VERSION.SDK_INT >= 29) resolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (error: Exception) { resolver.delete(uri, null, null); throw error }
    }

    private fun message(text: String) { mutableState.value = state.value.copy(message = text) }
    fun dismissMessage() { mutableState.value = state.value.copy(message = null) }
}
