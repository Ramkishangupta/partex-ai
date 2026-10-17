package com.voicecare.ai.ai

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.sqrt

/**
 * Speech Recognition Engine for VoiceCare AI:
 * Handles on-device microphone streaming & real-time acoustic transcription of Doctor & Patient speech
 * (Hindi / Marathi / Indian English code-switching).
 * Uses on-device speech engine with offline preference & real RMS audio waveform visualization.
 */
class WhisperAsrEngine(private val context: Context) {

    private val _isModelLoaded = MutableStateFlow(true)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _liveTranscript = MutableStateFlow("")
    val liveTranscript: StateFlow<String> = _liveTranscript.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    private val _detectedLanguage = MutableStateFlow("Hindi • English")
    val detectedLanguage: StateFlow<String> = _detectedLanguage.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val accumulatedText = StringBuilder()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(3200)

    fun initializeModel(modelPath: String? = null) {
        _isModelLoaded.value = SpeechRecognizer.isRecognitionAvailable(context)
    }

    @SuppressLint("MissingPermission")
    fun startListening(presetTranscript: String? = null) {
        if (_isRecording.value) return

        accumulatedText.clear()
        if (!presetTranscript.isNullOrBlank()) {
            accumulatedText.append(presetTranscript.trim())
            _liveTranscript.value = presetTranscript.trim()
        } else {
            _liveTranscript.value = ""
        }

        _isRecording.value = true

        // 1. Start Native Android On-Device SpeechRecognizer
        mainHandler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer?.destroy()
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {}
                            override fun onBeginningOfSpeech() {}

                            override fun onRmsChanged(rmsdB: Float) {
                                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
                                _audioAmplitude.value = normalized
                            }

                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}

                            override fun onError(error: Int) {
                                // Auto-restart if still recording (handles natural speech pauses)
                                if (_isRecording.value) {
                                    mainHandler.postDelayed({
                                        if (_isRecording.value) startSpeechRecognizerIntent()
                                    }, 200)
                                }
                            }

                            override fun onResults(results: Bundle?) {
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = matches?.firstOrNull() ?: ""
                                if (text.isNotBlank()) {
                                    if (accumulatedText.isNotEmpty()) accumulatedText.append(". ")
                                    accumulatedText.append(text)
                                    _liveTranscript.value = accumulatedText.toString()
                                }

                                if (_isRecording.value) {
                                    mainHandler.postDelayed({
                                        if (_isRecording.value) startSpeechRecognizerIntent()
                                    }, 100)
                                }
                            }

                            override fun onPartialResults(partialResults: Bundle?) {
                                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = partial?.firstOrNull() ?: ""
                                if (text.isNotBlank()) {
                                    val current = if (accumulatedText.isNotEmpty()) "$accumulatedText $text" else text
                                    _liveTranscript.value = current
                                }
                            }

                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                    }
                    startSpeechRecognizerIntent()
                } else {
                    startAudioRecordFallback()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                startAudioRecordFallback()
            }
        }
    }

    private fun startSpeechRecognizerIntent() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("hi-IN", "mr-IN"))
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            startAudioRecordFallback()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAudioRecordFallback() {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
                audioRecord?.startRecording()

                recordingJob = scope.launch {
                    val buffer = ShortArray(bufferSize / 2)
                    while (_isRecording.value && isActive) {
                        val readCount = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                        if (readCount > 0) {
                            var sum = 0.0
                            for (i in 0 until readCount) {
                                sum += buffer[i] * buffer[i]
                            }
                            val rms = sqrt(sum / readCount)
                            val normalizedLevel = (rms / 32768.0).toFloat().coerceIn(0.05f, 1f)
                            _audioAmplitude.value = normalizedLevel
                        }
                        delay(40)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopListening(): String {
        _isRecording.value = false

        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        audioRecord = null
        _audioAmplitude.value = 0f

        val finalResult = _liveTranscript.value.ifBlank { accumulatedText.toString() }.trim()
        return finalResult
    }

    fun setManualTranscript(text: String) {
        accumulatedText.clear()
        accumulatedText.append(text.trim())
        _liveTranscript.value = text.trim()
    }

    fun finalizeTranscription(): String {
        return _liveTranscript.value.ifBlank { accumulatedText.toString() }.trim()
    }

    fun clear() {
        accumulatedText.clear()
        _liveTranscript.value = ""
        _audioAmplitude.value = 0f
    }
}
