package com.example.util

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

object AlarmAlertHelper {
    private val lock = Any()

    @Volatile
    private var toneGenerator: ToneGenerator? = null

    fun playBreakInAlarm(context: Context) {
        synchronized(lock) {
            try {
                try {
                    toneGenerator?.stopTone()
                    toneGenerator?.release()
                } catch (_: Exception) {
                }
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 2000)

                val vibrator = resolveVibrator(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 500), -1)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(longArrayOf(0, 300, 150, 300, 150, 500), -1)
                }
            } catch (e: Exception) {
                Log.e("AlarmAlertHelper", "Error playing alarm tone", e)
            }
        }
    }

    fun stopBreakInAlarm(context: Context? = null) {
        synchronized(lock) {
            try {
                toneGenerator?.stopTone()
                toneGenerator?.release()
            } catch (_: Exception) {
            } finally {
                toneGenerator = null
            }
            if (context != null) {
                try {
                    resolveVibrator(context)?.cancel()
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
