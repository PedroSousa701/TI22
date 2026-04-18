package com.example.ti22

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private var mediaPlayer: MediaPlayer? = null
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    private var lastShakeTime = 0L
    private lateinit var listView: ListView
    private var songs: List<Song> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🔐 PERMISSÕES (Android 13+)
        if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_AUDIO), 1)
        }

        listView = findViewById(R.id.listView)
        val btnLoad = findViewById<Button>(R.id.btnLoad)

        // 🎧 CONFIGURAÇÃO DO SENSOR
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // 🎵 CARREGAR MÚSICAS
        btnLoad.setOnClickListener {
            songs = getSongs(this)
            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, songs.map { it.title })
            listView.adapter = adapter
        }

        // ▶️ SELECIONAR MÚSICA
        listView.setOnItemClickListener { _, _, position, _ ->
            playSong(songs[position].path)
        }
    }

    // --- LÓGICA DE ÁUDIO ---

    private fun getSongs(context: Context): List<Song> {
        val songsList = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DATA)
        val cursor: Cursor? = context.contentResolver.query(uri, projection, null, null, null)

        cursor?.use {
            val titleIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val pathIdx = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while (it.moveToNext()) {
                songsList.add(Song(it.getString(titleIdx), it.getString(pathIdx)))
            }
        }
        return songsList
    }

    private fun playSong(path: String) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            prepare()
            start()
        }
    }

    // ⏯️ ALTERNAR PLAY/PAUSE
    private fun togglePlayPause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.start()
            }
        }
    }

    // --- LÓGICA DE SENSORES ---

    override fun onSensorChanged(event: SensorEvent) {
        // EXTRAIR VALORES (Importante: usar,, para evitar erro de FloatArray)
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // 🔊 VOLUME POR INCLINAÇÃO (Eixo X)
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        // Usamos -1 * x para evitar problemas com o operador unário
        val formulaVolume = (((-1 * x) + 10) / 20) * maxVol
        val volumeFinal = formulaVolume.toInt().coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volumeFinal, 0)

        // 📳 AGITAR (SHAKE) PARA PLAY/PAUSE
        val acceleration = sqrt((x * x + y * y + z * z).toDouble())

        if (acceleration > 15) {
            val agora = System.currentTimeMillis()
            // Delay de 1 segundo para não pausar/tocar repetidamente no mesmo abanão
            if (agora - lastShakeTime > 1000) {
                lastShakeTime = agora
                togglePlayPause()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // --- CICLO DE VIDA ---

    override fun onResume() {
        super.onResume()
        accelerometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}