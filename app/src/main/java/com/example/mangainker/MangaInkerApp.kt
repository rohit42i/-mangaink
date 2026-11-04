package com.example.mangainker

import android.app.Application
import com.example.mangainker.core.SessionController
import com.example.mangainker.update.GitHubUpdateManager

class MangaInkerApp : Application() {
    lateinit var sessionController: SessionController
        private set
    lateinit var updateManager: GitHubUpdateManager
        private set

    override fun onCreate() {
        super.onCreate()
        sessionController = SessionController(applicationContext)
        updateManager = GitHubUpdateManager(applicationContext)
        updateManager.checkForUpdate()
    }
}
