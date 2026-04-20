package com.example.ti22

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var listView: ListView

    // ✔ Lista simples
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

            songs = getSongs(this)

            // 🎯 apenas ordenação por nome (A → Z)
            songs = songs.sortedBy { it.title.lowercase() }

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

    private fun getSongs(context: Context): List<Song> {

        val list = mutableListOf<Song>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA
        )

        val cursor: Cursor? =
            context.contentResolver.query(uri, projection, null, null, null)

        cursor?.use {

            val titleIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val pathIndex = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (it.moveToNext()) {

                list.add(
                    Song(
                        it.getString(titleIndex),
                        it.getString(pathIndex)
                    )
                )
            }
        }

        return list
    }
}