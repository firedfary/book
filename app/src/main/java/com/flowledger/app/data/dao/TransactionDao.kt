package com.flowledger.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.flowledger.app.data.model.PostingEntity
import com.flowledger.app.data.model.TransactionEntity
import com.flowledger.app.data.model.TransactionWithPostings
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPostings(postings: List<PostingEntity>)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransaction(transactionId: String)

    @Query("DELETE FROM transactions WHERE bookId = :bookId")
    suspend fun deleteTransactionsByBook(bookId: String)

    @Transaction
    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY occurredAt DESC")
    fun getAllTransactionsWithPostings(): Flow<List<TransactionWithPostings>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE bookId = :bookId AND isDeleted = 0 ORDER BY occurredAt DESC")
    fun getAllTransactionsWithPostingsByBook(bookId: String): Flow<List<TransactionWithPostings>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE bookId = :bookId AND isDeleted = 0 ORDER BY occurredAt DESC")
    suspend fun getAllTransactionsWithPostingsByBookSync(bookId: String): List<TransactionWithPostings>

    @Transaction
    @Query("SELECT * FROM transactions WHERE bookId = :bookId AND isDeleted = 0 ORDER BY occurredAt DESC LIMIT :limit")
    fun getRecentTransactionsWithPostingsByBook(bookId: String, limit: Int): Flow<List<TransactionWithPostings>>

    @Query("SELECT COUNT(*) FROM transactions WHERE bookId = :bookId AND isDeleted = 0")
    suspend fun getTransactionCountByBook(bookId: String): Int

    @Query("SELECT COUNT(DISTINCT p.transactionId) FROM postings p WHERE p.accountId = :accountId")
    suspend fun getTransactionCountForAccount(accountId: String): Int

    @Query("SELECT DISTINCT p.transactionId FROM postings p WHERE p.accountId = :accountId")
    suspend fun getTransactionIdsForAccount(accountId: String): List<String>

    @Transaction
    suspend fun insertFullTransaction(transaction: TransactionEntity, postings: List<PostingEntity>) {
        insertTransaction(transaction)
        insertPostings(postings)
    }

    @Transaction
    suspend fun insertFullTransactionsBatch(
        transactions: List<TransactionEntity>,
        postings: List<PostingEntity>
    ) {
        insertTransactions(transactions)
        insertPostings(postings)
    }

    @Transaction
    @Query("""
        SELECT t.* FROM transactions t
        INNER JOIN postings p ON t.id = p.transactionId
        WHERE t.bookId = :bookId 
          AND t.isDeleted = 0 
          AND p.accountId = :accountId
          AND t.occurredAt BETWEEN :startTime AND :endTime
    """)
    suspend fun getTransactionsForAccountInTimeRange(
        bookId: String,
        accountId: String,
        startTime: Long,
        endTime: Long
    ): List<TransactionEntity>

    @Transaction
    suspend fun cascadeDeleteAccountTransactions(accountId: String) {
        val txIds = getTransactionIdsForAccount(accountId)
        for (txId in txIds) {
            deleteTransaction(txId)
        }
    }
}
