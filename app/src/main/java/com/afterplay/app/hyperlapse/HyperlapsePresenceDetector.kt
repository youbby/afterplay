package com.afterplay.app.hyperlapse

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions

/** The bundled detector analyzes only sampled cuts, on the camera worker, without uploading images. */
internal class HyperlapsePresenceDetector : AutoCloseable {
    private val detector = FaceDetection.getClient(FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .build())

    fun isPresent(bitmap: Bitmap): Boolean = Tasks.await(detector.process(InputImage.fromBitmap(bitmap, 0))).isNotEmpty()

    override fun close() = detector.close()
}
