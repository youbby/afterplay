package com.afterplay.app.hyperlapse

import android.graphics.*
import androidx.camera.core.ImageProxy
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Frame spools are private and survive process death until a video has been safely published. */
internal class HyperlapseFrames(val directory: File) {
    init { check(directory.isDirectory || directory.mkdirs()) }

    fun hasSlot(round: Int, slot: Int) = file(round, slot).isFile
    private fun file(round: Int, slot: Int) = File(directory, "${round}_${slot.toString().padStart(3, '0')}.jpg")

    fun capture(image: ImageProxy, round: Int, slot: Int, wallTime: Long, isPresent: (Bitmap) -> Boolean) {
        val original = image.toBitmap()
        val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
        val side = minOf(rotated.width, rotated.height)
        val output = Bitmap.createBitmap(HyperlapsePlan.SIZE, HyperlapsePlan.SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.save()
        canvas.scale(-1f, 1f, output.width / 2f, output.height / 2f) // Match the front-camera preview.
        canvas.drawBitmap(rotated, Rect((rotated.width - side) / 2, (rotated.height - side) / 2,
            (rotated.width + side) / 2, (rotated.height + side) / 2), Rect(0, 0, output.width, output.height), paint)
        canvas.restore()
        val destination = file(round, slot)
        val temporary = File(directory, destination.name + ".tmp")
        val presence = File(directory, destination.nameWithoutExtension + ".presence")
        try {
            // Analyze the same square crop that will appear in the video, before adding text.
            val focused = isPresent(output)
            paint.color = Color.argb(145, 0, 0, 0)
            canvas.drawRect(0f, output.height - 54f, output.width.toFloat(), output.height.toFloat(), paint)
            paint.color = Color.WHITE
            paint.textSize = 25f
            paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            canvas.drawText(SimpleDateFormat("yyyy.MM.dd  HH:mm:ss", Locale.US).format(Date(wallTime)),
                18f, output.height - 18f, paint)
            temporary.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
            // Publish the count before the JPEG. Recovery ignores any metadata without a JPEG.
            presence.writeText(if (focused) "focused" else "away")
            check(temporary.renameTo(destination))
        } finally {
            if (!destination.isFile) presence.delete()
            temporary.delete()
            output.recycle()
            if (rotated !== original) rotated.recycle()
            original.recycle()
        }
    }

    fun complete(round: Int) { File(directory, "complete-$round").writeText("15s") }

    fun videoFrames(): List<File> = (0 until 4).flatMap { round ->
        val available = (0 until HyperlapsePlan.FRAMES_PER_ROUND).filter { hasSlot(round, it) }
        HyperlapsePlan.sourceSlots(available, File(directory, "complete-$round").exists()).map { file(round, it) }
    }.also { check(it.size <= HyperlapsePlan.MAX_FRAMES) }

    fun presence(): HyperlapsePresence = HyperlapsePresence.from((0 until 4).flatMap { round ->
        (0 until HyperlapsePlan.FRAMES_PER_ROUND).filter { hasSlot(round, it) }.map { slot ->
            val record = File(directory, file(round, slot).nameWithoutExtension + ".presence")
            when (if (record.isFile) record.readText() else null) {
                "focused" -> true
                "away" -> false
                else -> null // Legacy/interrupted records must never be silently counted as away.
            }
        }
    })

    fun exportFrames(): List<File> {
        val frames = videoFrames()
        if (frames.isEmpty()) return emptyList()
        val summary = presence()
        val ending = File(directory, "summary.jpg")
        val bitmap = Bitmap.createBitmap(HyperlapsePlan.SIZE, HyperlapsePlan.SIZE, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(19, 27, 36))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                color = Color.WHITE
            }
            paint.textSize = 30f
            canvas.drawText("집중 기록", 360f, 240f, paint)
            paint.color = Color.rgb(124, 224, 196)
            paint.textSize = 42f
            val percentages = if (summary.unknown == summary.total) "--% 집중 / --% 이탈"
                else String.format(Locale.KOREA, "%02d%% 집중 / %02d%% 이탈", summary.focusedPercent, summary.awayPercent)
            canvas.drawText(percentages, 360f, 340f, paint)
            paint.color = Color.WHITE
            paint.textSize = 27f
            canvas.drawText("전체 ${summary.total}컷 중 이탈 ${summary.away}컷", 360f, 410f, paint)
            paint.textSize = 21f
            paint.color = Color.LTGRAY
            canvas.drawText("얼굴 감지 기준 · 기기 내 분석", 360f, 480f, paint)
            if (summary.unknown > 0) canvas.drawText("감지 기록 없음 ${summary.unknown}컷 포함", 360f, 520f, paint)
            ending.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it)) }
        } finally { bitmap.recycle() }
        return frames + List(HyperlapsePlan.SUMMARY_FRAMES) { ending }
    }

    fun clear() {
        // Only files directly inside this app-created spool; no recursive deletion or followed paths.
        directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        directory.delete()
    }
}
