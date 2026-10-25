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

    fun capture(image: ImageProxy, round: Int, slot: Int, wallTime: Long) {
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
        paint.color = Color.argb(145, 0, 0, 0)
        canvas.drawRect(0f, output.height - 54f, output.width.toFloat(), output.height.toFloat(), paint)
        paint.color = Color.WHITE
        paint.textSize = 25f
        paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        canvas.drawText(SimpleDateFormat("yyyy.MM.dd  HH:mm:ss", Locale.US).format(Date(wallTime)),
            18f, output.height - 18f, paint)
        val destination = file(round, slot)
        val temporary = File(directory, destination.name + ".tmp")
        try {
            temporary.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 88, it)) }
            check(temporary.renameTo(destination))
        } finally {
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

    fun clear() {
        // Only files directly inside this app-created spool; no recursive deletion or followed paths.
        directory.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
        directory.delete()
    }
}
