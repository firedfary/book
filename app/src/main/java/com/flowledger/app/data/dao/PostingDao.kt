package com.flowledger.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.flowledger.app.data.model.PostingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PostingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPostings(postings: List<PostingEntity>)

    @Query("SELECT * FROM postings WHERE transactionId = :transactionId")
    suspend fun getPostingsForTransaction(transactionId: String): List<PostingEntity>

    @Query("SELECT * FROM postings WHERE accountId = :accountId ORDER BY id DESC")
    fun getPostingsForAccount(accountId: String): Flow<List<PostingEntity>>
}
