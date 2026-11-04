package com.example.mangainker

import android.app.Application
import com.example.mangainker.gesture.PathPlayer

class MangaInkerApp : Application() {
    lateinit var pathPlayer: PathPlayer
        private set

    override fun onCreate() {
        super.onCreate()
        pathPlayer = PathPlayer()
    }
}
