package com.flowledger.app

import android.app.Application
import com.flowledger.app.data.AppDatabase
import com.flowledger.app.repository.LedgerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class FlowLedgerApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob())

    val database by lazy { AppDatabase.getDatabase(this, applicationScope) }
    val repository by lazy {
        LedgerRepository(
            this,
            database.bookDao(),
            database.accountDao(),
            database.transactionDao(),
            database.postingDao()
        )
    }
}
