package com.example.ti22

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt

class PlayerActivity : AppCompatActivity(), SensorEventListener {

    private var mediaPlayer: MediaPlayer? = null
    private var clickPlayer: MediaPlayer? = null

    private lateinit var songs: ArrayList<Song>
    private var currentIndex = 0

    private lateinit var txtTitle: TextView
    private lateinit var txtTime: TextView
    private lateinit var txtCurrentTime: TextView
    private lateinit var txtEndTime: TextView

    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnNext: Button
    private lateinit var btnPrev: Button
    private lateinit var btnBack: Button

    private lateinit var seekBar: SeekBar

    private val handler = Handler()

    // 📳 SENSORES
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var rotationSensor: Sensor? = null
    private var lastShakeTime = 0L

    // 🎚️ volume suavizado
    private var smoothVolume = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        songs = intent.getParcelableArrayListExtra("songs") ?: arrayListOf()
        currentIndex = intent.getIntExtra("index", 0)

        txtTitle = findViewById(R.id.txtTitle)
        txtTime = findViewById(R.id.txtTime)
        txtCurrentTime = findViewById(R.id.txtCurrentTime)
        txtEndTime = findViewById(R.id.txtEndTime)

        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnNext = findViewById(R.id.btnNext)
        btnPrev = findViewById(R.id.btnPrev)
        btnBack = findViewById(R.id.btnBack)

        seekBar = findViewById(R.id.seekBar)

        // 📳 SENSOR MANAGER
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

        // 🔊 click sound
        clickPlayer = MediaPlayer.create(this, R.raw.click)

        playSong()

        btnPlayPause.setOnClickListener { togglePlayPause() }
        btnNext.setOnClickListener { nextSong() }
        btnPrev.setOnClickListener { prevSong() }
        btnBack.setOnClickListener { finish() }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) mediaPlayer?.seekTo(progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    // 🎵 PLAY MUSIC
    private fun playSong() {

        if (songs.isEmpty()) return

        try {
            mediaPlayer?.release()

            val song = songs[currentIndex]
            txtTitle.text = song.title

            mediaPlayer = MediaPlayer().apply {

                setDataSource(song.path)

                setOnPreparedListener { mp ->
                    mp.start()

                    val duration = mp.duration
                    seekBar.max = duration

                    txtTime.text = "Duração: ${formatTime(duration)}"
                    txtEndTime.text = formatTime(duration)

                    updateSeekBar()
                    updatePlayPauseButton()
                }

                setOnCompletionListener {
                    nextSong()
                }

                prepareAsync()
            }

        } catch (e: Exception) {
            Log.e("PLAYER", "Erro", e)
            Toast.makeText(this, "Erro ao tocar música", Toast.LENGTH_SHORT).show()
        }
    }

    // ▶️ ⏸ PLAY / PAUSE
    private fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                playClickSound()
            } else {
                it.start()
                playClickSound()
            }
            updatePlayPauseButton()
        }
    }

    private fun updatePlayPauseButton() {
        btnPlayPause.setImageResource(
            if (mediaPlayer?.isPlaying == true) R.drawable.pause
            else R.drawable.play
        )
    }

    private fun nextSong() {
        if (currentIndex < songs.size - 1) {
            currentIndex++
            playSong()
            updatePlayPauseButton()
        }
    }

    private fun prevSong() {
        if (currentIndex > 0) {
            currentIndex--
            playSong()
            updatePlayPauseButton()
        }
    }

    private fun playClickSound() {
        clickPlayer?.let {
            if (it.isPlaying) it.seekTo(0)
            it.start()
        }
    }

    private fun updateSeekBar() {
        handler.post(object : Runnable {
            override fun run() {
                mediaPlayer?.let {
                    seekBar.progress = it.currentPosition
                    txtCurrentTime.text = formatTime(it.currentPosition)
                }
                handler.postDelayed(this, 500)
            }
        })
    }

    private fun formatTime(ms: Int): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    // 📳 SENSORES
    override fun onSensorChanged(event: SensorEvent) {

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        // 🔄 ROTATION VECTOR → VOLUME
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {

            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)

            val roll = orientation[2] // 🔥 esquerda/direita

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            // 🎯 -90° a +90° (~ -1.57 a +1.57 rad)
            val maxAngle = 1.57f

            // 📏 normalizar roll para 0..1
            val normalized = ((roll + maxAngle) / (2 * maxAngle)).coerceIn(0f, 1f)

            val targetVolume = normalized * maxVol

            // 🔥 suavização
            val alpha = 0.3f
            smoothVolume += alpha * (targetVolume - smoothVolume)

            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                smoothVolume.toInt().coerceIn(0, maxVol),
                0
            )
        }

        // 📳 ACCELEROMETER → SHAKE
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val acceleration = sqrt((x * x + y * y + z * z).toDouble())

            if (acceleration > 18) {
                val now = System.currentTimeMillis()

                if (now - lastShakeTime > 1000) {
                    lastShakeTime = now
                    togglePlayPause()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()

        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        rotationSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacksAndMessages(null)

        mediaPlayer?.release()
        mediaPlayer = null

        clickPlayer?.release()
        clickPlayer = null
    }
}