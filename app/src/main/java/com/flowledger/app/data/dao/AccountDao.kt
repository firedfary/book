package com.flowledger.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import kotlinx.coroutines.flow.Flow

data class AccountBalanceRow(
    @Embedded val account: AccountEntity,
    val currentBalance: Double
)

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY type ASC, name ASC")
    fun getAllAccountsFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE type = :type AND isArchived = 0 ORDER BY name ASC")
    fun getAccountsByTypeFlow(type: AccountType): Flow<List<AccountEntity>>

    @Query("""
        SELECT a.*, 
               (a.initialBalance + IFNULL((SELECT SUM(p.amount) FROM postings p WHERE p.accountId = a.id), 0.0)) AS currentBalance
        FROM accounts a
        WHERE a.isArchived = 0
        ORDER BY a.type ASC, a.name ASC
    """)
    fun getAccountsWithBalancesFlow(): Flow<List<AccountBalanceRow>>

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun getAccountCount(): Int
}
