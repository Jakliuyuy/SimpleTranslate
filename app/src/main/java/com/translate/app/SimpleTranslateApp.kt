package com.translate.app

import android.app.Application
import com.translate.app.data.api.EngineFactory
import com.translate.app.data.db.TranslationDbHelper
import com.translate.app.data.repository.PreferenceRepository
import com.translate.app.data.repository.TranslationRepository
import com.translate.app.util.TtsManager

class SimpleTranslateApp : Application() {

    lateinit var preferenceRepository: PreferenceRepository
        private set

    lateinit var dbHelper: TranslationDbHelper
        private set

    lateinit var engineFactory: EngineFactory
        private set

    lateinit var translationRepository: TranslationRepository
        private set

    lateinit var ttsManager: TtsManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        preferenceRepository = PreferenceRepository(this)
        dbHelper = TranslationDbHelper.getInstance(this)
        engineFactory = EngineFactory(preferenceRepository)
        translationRepository = TranslationRepository(engineFactory, dbHelper, preferenceRepository)
        ttsManager = TtsManager(this)
    }

    override fun onTerminate() {
        ttsManager.release()
        super.onTerminate()
    }

    companion object {
        lateinit var instance: SimpleTranslateApp
            private set
    }
}
