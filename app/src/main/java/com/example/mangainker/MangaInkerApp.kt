package com.example.mangainker

import android.app.Application
import com.example.mangainker.core.SessionController

class MangaInkerApp : Application() {
    lateinit var sessionController: SessionController
        private set

    override fun onCreate() {
        super.onCreate()
        sessionController = SessionController(applicationContext)
    }
}
