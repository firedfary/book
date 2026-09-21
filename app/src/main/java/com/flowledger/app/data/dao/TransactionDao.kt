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
    suspend fun insertPostings(postings: List<PostingEntity>)

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteTransaction(transactionId: String)

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC")
    fun getAllTransactionsWithPostings(): Flow<List<TransactionWithPostings>>

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY occurredAt DESC LIMIT :limit")
    fun getRecentTransactionsWithPostings(limit: Int): Flow<List<TransactionWithPostings>>

    @Transaction
    suspend fun insertFullTransaction(transaction: TransactionEntity, postings: List<PostingEntity>) {
        insertTransaction(transaction)
        insertPostings(postings)
    }
}
