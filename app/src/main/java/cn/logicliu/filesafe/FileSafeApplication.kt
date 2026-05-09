package cn.logicliu.filesafe

import android.app.Application
import cn.logicliu.filesafe.data.FileDataStore
import cn.logicliu.filesafe.data.repository.FileRepository
import cn.logicliu.filesafe.security.CryptoManager
import cn.logicliu.filesafe.security.PasswordManager
import cn.logicliu.filesafe.security.SecurityQuestionManager
import cn.logicliu.filesafe.security.SecuritySettingsManager

class FileSafeApplication : Application() {
    lateinit var fileDataStore: FileDataStore
        private set

    lateinit var cryptoManager: CryptoManager
        private set

    lateinit var passwordManager: PasswordManager
        private set

    lateinit var securityQuestionManager: SecurityQuestionManager
        private set

    lateinit var securitySettingsManager: SecuritySettingsManager
        private set

    lateinit var fileRepository: FileRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        initializeDependencies()
    }

    private fun initializeDependencies() {
        fileDataStore = FileDataStore.getInstance(this)
        cryptoManager = CryptoManager(this)
        passwordManager = PasswordManager(this, cryptoManager)
        securityQuestionManager = SecurityQuestionManager(this)
        securitySettingsManager = SecuritySettingsManager(this)
        fileRepository = FileRepository(
            this,
            fileDataStore,
            cryptoManager,
            securitySettingsManager
        )
    }

    companion object {
        lateinit var instance: FileSafeApplication
            private set
    }
}
