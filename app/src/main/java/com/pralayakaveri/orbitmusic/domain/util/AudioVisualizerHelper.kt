package com.pralayakaveri.orbitmusic.domain.util

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.hypot

class AudioVisualizerHelper {
    private var visualizer: Visualizer? = null
    private var lastDetectionTime = 0L
    private var isContinuous = false
    private var onVocalOnset: (Long) -> Unit = {}
    private var onFrequencyData: (Float) -> Unit = {}
    private var vocalOnsetDetected = false // Added this based on usage in startMonitoring

    fun startMonitoring(
        audioSessionId: Int, 
        continuous: Boolean = false, 
        onVocalOnset: (Long) -> Unit,
        onFrequencyData: (Float) -> Unit = {}
    ) {
        if (audioSessionId == 0) return
        
        stopMonitoring()
        vocalOnsetDetected = false
        isContinuous = continuous
        lastDetectionTime = 0L
        this.onVocalOnset = onVocalOnset
        this.onFrequencyData = onFrequencyData

        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        if (waveform == null) return
                        val now = System.currentTimeMillis()
                        if (now - lastDetectionTime < 1000) return

                        var maxAmp = 0f
                        for (i in waveform.indices) {
                            val amp = Math.abs(waveform[i].toInt() - 128).toFloat()
                            if (amp > maxAmp) maxAmp = amp
                        }

                        if (maxAmp > 35f) {
                            lastDetectionTime = now
                            vocalOnsetDetected = true
                            onVocalOnset(now)
                            if (!isContinuous) stopMonitoring()
                        }
                    }

                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null) return
                        
                        // Mid-range frequency detection (approx 300Hz to 3kHz)
                        // FFT data format: [real0, imag0, real1, imag1, ...]
                        // n is captureSize
                        val n = fft.size
                        var midBandEnergy = 0f
                        var count = 0
                        
                        // Sampling rate is usually 44100. Resolution = samplingRate / n.
                        // For captureSize 1024, resolution is ~43Hz.
                        // 300Hz is index ~7, 3000Hz is index ~70.
                        for (i in 7 until 70) {
                            val real = fft[i * 2].toFloat()
                            val imag = fft[i * 2 + 1].toFloat()
                            val magnitude = Math.sqrt((real * real + imag * imag).toDouble()).toFloat()
                            midBandEnergy += magnitude
                            count++
                        }
                        
                        if (count > 0) {
                            onFrequencyData(midBandEnergy / count)
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                enabled = true
            }
        } catch (e: Exception) {
            Log.e("AudioVisualizerHelper", "Failed to initialize Visualizer", e)
        }
    }

    fun stopMonitoring() {
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
    }
}
