package com.example.ti22

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private var songs: List<Song> = listOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.READ_MEDIA_AUDIO), 1)
        }

        listView = findViewById(R.id.listView)
        val btnLoad = findViewById<Button>(R.id.btnLoad)

        btnLoad.setOnClickListener {
            songs = getSongs().sortedBy { it.title.lowercase() }

            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_1,
                songs.map { it.title }
            )

            listView.adapter = adapter
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            val intent = Intent(this, PlayerActivity::class.java)
            intent.putParcelableArrayListExtra("songs", ArrayList(songs))
            intent.putExtra("index", position)
            startActivity(intent)
        }
    }

    private fun getSongs(): List<Song> {
        val list = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA
        )

        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val titleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                list.add(
                    Song(
                        cursor.getString(titleIndex),
                        cursor.getString(pathIndex)
                    )
                )
            }
        }

        return list
    }
}