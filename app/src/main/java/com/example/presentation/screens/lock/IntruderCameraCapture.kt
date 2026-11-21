package com.example.presentation.screens.lock

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleOwner
import com.example.domain.model.SecuritySettings
import com.example.presentation.screens.intruders.IntruderLogStore
import com.example.presentation.screens.intruders.IntruderMediaType
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates silent front-camera intruder photo and video evidence capture using CameraX.
 *
 * - Attempts 1–2 (or when intruderVideoEnabled is off) capture a fast still JPEG photo.
 * - Attempt 3+ with intruderVideoEnabled enabled records a silent front-camera MP4 clip
 *   (default 8 seconds) without requiring RECORD_AUDIO permission, preventing camera conflicts
 *   by guarding active recordings with an AtomicBoolean.
 */
object IntruderCameraCapture {
    private const val TAG = "IntruderCameraCapture"
    const val DEFAULT_VIDEO_DURATION_MS = 8_000L

    private val isVideoRecording = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeRecording: Recording? = null

    @Volatile
    private var activeStopRunnable: Runnable? = null

    fun isRecordingVideo(): Boolean = isVideoRecording.get()

    /**
     * Stops any in-progress intruder video cleanly so CameraX finalizes and saves the clip.
     */
    fun stopActiveVideoIfRunning() {
        mainHandler.post {
            activeStopRunnable?.let { mainHandler.removeCallbacks(it) }
            activeStopRunnable = null
            try {
                activeRecording?.stop()
            } catch (_: Exception) {
                // Recording may already be finalized
            }
            activeRecording = null
        }
    }

    /**
     * Dispatches either a photo or a silent front-camera video based on failed attempt count
     * and the user's [SecuritySettings.intruderVideoEnabled] preference.
     */
    fun captureEvidence(
        activity: FragmentActivity,
        settings: SecuritySettings,
        targetPackageName: String = "",
        targetAppLabel: String = "Locked App",
        authMethodLabel: String = "PIN",
        failedAttemptNumber: Int = 1,
        isUninstallProtection: Boolean = false
    ) {
        if (failedAttemptNumber >= 3 && settings.intruderVideoEnabled) {
            recordIntruderVideo(
                activity = activity,
                targetPackageName = targetPackageName,
                targetAppLabel = targetAppLabel,
                authMethodLabel = authMethodLabel,
                failedAttemptNumber = failedAttemptNumber,
                isUninstallProtection = isUninstallProtection
            )
        } else {
            takeIntruderSelfie(
                activity = activity,
                targetPackageName = targetPackageName,
                targetAppLabel = targetAppLabel,
                authMethodLabel = authMethodLabel,
                failedAttemptNumber = failedAttemptNumber,
                isUninstallProtection = isUninstallProtection
            )
        }
    }

    fun takeIntruderSelfie(
        activity: FragmentActivity,
        targetPackageName: String = "",
        targetAppLabel: String = "Locked App",
        authMethodLabel: String = "PIN",
        failedAttemptNumber: Int = 1,
        isUninstallProtection: Boolean = false
    ) {
        if (isVideoRecording.get()) {
            // Do not interrupt an active intruder video recording with a still capture
            return
        }
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            try {
                if (isVideoRecording.get() || activity.isFinishing || activity.isDestroyed) return@addListener
                val cameraProvider = cameraProviderFuture.get()
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(activity as LifecycleOwner, cameraSelector, imageCapture)

                val dir = IntruderLogStore.getIntruderDir(activity)
                val captureTimestamp = System.currentTimeMillis()
                val file = File(dir, "intruder_${captureTimestamp}.jpg")
                val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

                mainHandler.postDelayed({
                    try {
                        if (isVideoRecording.get() || activity.isFinishing || activity.isDestroyed) {
                            if (!isVideoRecording.get()) cameraProvider.unbindAll()
                            return@postDelayed
                        }
                        imageCapture.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(activity),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    IntruderLogStore.saveMetadata(
                                        photoFile = file,
                                        targetPackageName = targetPackageName,
                                        targetAppLabel = targetAppLabel,
                                        authMethodLabel = authMethodLabel,
                                        failedAttemptNumber = failedAttemptNumber,
                                        isUninstallProtection = isUninstallProtection,
                                        timestampMillis = captureTimestamp,
                                        mediaType = IntruderMediaType.PHOTO
                                    )
                                    if (!isVideoRecording.get()) {
                                        cameraProvider.unbindAll()
                                    }
                                }

                                override fun onError(exc: ImageCaptureException) {
                                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                                    if (!isVideoRecording.get()) {
                                        cameraProvider.unbindAll()
                                    }
                                }
                            }
                        )
                    } catch (exc: Exception) {
                        Log.e(TAG, "Error capturing intruder selfie: ${exc.message}", exc)
                        if (!isVideoRecording.get()) {
                            cameraProvider.unbindAll()
                        }
                    }
                }, 650)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(activity))
    }

    fun recordIntruderVideo(
        activity: FragmentActivity,
        targetPackageName: String = "",
        targetAppLabel: String = "Locked App",
        authMethodLabel: String = "PIN",
        failedAttemptNumber: Int = 3,
        isUninstallProtection: Boolean = false,
        durationMillis: Long = DEFAULT_VIDEO_DURATION_MS
    ) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Prevent overlapping recordings if intruder taps wrong inputs rapidly while recording
        if (!isVideoRecording.compareAndSet(false, true)) {
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
        cameraProviderFuture.addListener({
            try {
                if (activity.isFinishing || activity.isDestroyed) {
                    isVideoRecording.set(false)
                    return@addListener
                }
                val cameraProvider = cameraProviderFuture.get()
                val qualitySelector = QualitySelector.from(
                    Quality.SD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                )
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    activity as LifecycleOwner,
                    cameraSelector,
                    videoCapture
                )

                val dir = IntruderLogStore.getIntruderDir(activity)
                val captureTimestamp = System.currentTimeMillis()
                val videoFile = File(dir, "intruder_video_${captureTimestamp}.mp4")
                val fileOutputOptions = FileOutputOptions.Builder(videoFile).build()

                val executor = ContextCompat.getMainExecutor(activity)
                var recordingStartedAt = captureTimestamp

                val recording = videoCapture.output
                    .prepareRecording(activity, fileOutputOptions)
                    .start(executor) { event ->
                        when (event) {
                            is VideoRecordEvent.Start -> {
                                recordingStartedAt = System.currentTimeMillis()
                                Log.d(TAG, "Silent intruder video recording started: ${videoFile.name}")
                            }

                            is VideoRecordEvent.Finalize -> {
                                activeStopRunnable?.let { mainHandler.removeCallbacks(it) }
                                activeStopRunnable = null
                                activeRecording = null
                                isVideoRecording.set(false)

                                val elapsedSec = ((System.currentTimeMillis() - recordingStartedAt) / 1000L)
                                    .toInt()
                                    .coerceIn(1, (durationMillis / 1000L).toInt().coerceAtLeast(1))

                                val hasUsableFile = videoFile.exists() && videoFile.length() > 1024L
                                if ((!event.hasError() || event.error == VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE) && hasUsableFile) {
                                    IntruderLogStore.saveMetadata(
                                        photoFile = videoFile,
                                        targetPackageName = targetPackageName,
                                        targetAppLabel = targetAppLabel,
                                        authMethodLabel = authMethodLabel,
                                        failedAttemptNumber = failedAttemptNumber,
                                        isUninstallProtection = isUninstallProtection,
                                        timestampMillis = captureTimestamp,
                                        mediaType = IntruderMediaType.VIDEO,
                                        durationSeconds = elapsedSec
                                    )
                                    Log.d(TAG, "Intruder video saved (${elapsedSec}s): ${videoFile.name}")
                                } else {
                                    Log.e(TAG, "Intruder video failed (error=${event.error})", event.cause)
                                    try {
                                        videoFile.delete()
                                    } catch (_: Exception) {
                                    }
                                }

                                try {
                                    cameraProvider.unbindAll()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }

                activeRecording = recording

                val stopRunnable = Runnable {
                    try {
                        activeRecording?.stop()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error stopping intruder video: ${e.message}")
                        isVideoRecording.set(false)
                    }
                }
                activeStopRunnable = stopRunnable
                mainHandler.postDelayed(stopRunnable, durationMillis)
            } catch (exc: Exception) {
                Log.e(TAG, "Intruder video binding failed: ${exc.message}", exc)
                isVideoRecording.set(false)
                activeRecording = null
            }
        }, ContextCompat.getMainExecutor(activity))
    }
}

fun takeIntruderSelfie(
    activity: FragmentActivity,
    targetPackageName: String = "",
    targetAppLabel: String = "Locked App",
    authMethodLabel: String = "PIN",
    failedAttemptNumber: Int = 1,
    isUninstallProtection: Boolean = false
) {
    IntruderCameraCapture.takeIntruderSelfie(
        activity = activity,
        targetPackageName = targetPackageName,
        targetAppLabel = targetAppLabel,
        authMethodLabel = authMethodLabel,
        failedAttemptNumber = failedAttemptNumber,
        isUninstallProtection = isUninstallProtection
    )
}
