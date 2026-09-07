package com.ozzyvoice

import android.Manifest
import android.content.pm.PackageManager
import android.media.*
import android.os.Bundle
import android.widget.*
import androidx.activity.ComponentActivity
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private var recorder: AudioRecord? = null
    private var player: AudioTrack? = null
    @Volatile private var running = false
    @Volatile private var effect = 0
    @Volatile private var pitch = 1.0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        }

        val status = findViewById<TextView>(R.id.status)
        val group = findViewById<RadioGroup>(R.id.effects)
        group.setOnCheckedChangeListener { _, id ->
            effect = when (id) {
                R.id.deep -> 1
                R.id.robot -> 2
                R.id.echo -> 3
                else -> 0
            }
        }

        findViewById<SeekBar>(R.id.pitch).setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { pitch = 0.5f + p / 100f }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        findViewById<Button>(R.id.startButton).setOnClickListener {
            if (!running) {
                startAudio()
                status.text = "● MIC ON"
            }
        }
        findViewById<Button>(R.id.stopButton).setOnClickListener {
            stopAudio()
            status.text = "● OFF"
        }
    }

    private fun startAudio() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val rate = 44100
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setBufferSizeInBytes(min * 2).build()

        player = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(min * 2).build()

        running = true
        recorder!!.startRecording()
        player!!.play()

        Thread {
            val buf = ShortArray(min)
            var phase = 0.0
            var previous = 0f
            while (running) {
                val n = recorder!!.read(buf, 0, buf.size)
                for (i in 0 until n) {
                    var x = buf[i].toFloat()
                    x = when (effect) {
                        1 -> x * 0.72f
                        2 -> if (x > 0) 18000f else -18000f
                        3 -> x * 0.8f + previous * 0.35f
                        else -> x
                    }
                    if (effect == 2) {
                        x += (sin(phase) * 2500.0).toFloat()
                        phase += 0.08
                    }
                    previous = x
                    buf[i] = (x * pitch).toInt().coerceIn(-32768, 32767).toShort()
                }
                if (n > 0) player!!.write(buf, 0, n)
            }
        }.start()
    }

    private fun stopAudio() {
        running = false
        try { recorder?.stop(); recorder?.release() } catch (_: Exception) {}
        try { player?.stop(); player?.release() } catch (_: Exception) {}
        recorder = null
        player = null
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }
}
