package com.flowledger.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.flowledger.app.data.model.BookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :bookId")
    suspend fun deleteBookById(bookId: String)

    @Query("SELECT * FROM books WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getBookById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE isDefault = 1 AND isDeleted = 0 LIMIT 1")
    suspend fun getDefaultBook(): BookEntity?

    @Query("SELECT * FROM books WHERE isDeleted = 0 ORDER BY isDefault DESC, createdAt ASC")
    fun getAllBooksFlow(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isDeleted = 0 ORDER BY isDefault DESC, createdAt ASC")
    suspend fun getAllBooks(): List<BookEntity>

    @Query("SELECT COUNT(*) FROM books WHERE isDeleted = 0")
    suspend fun getActiveBookCount(): Int

    @Query("UPDATE books SET isDefault = 0")
    suspend fun clearDefaultFlags()

    @Query("UPDATE books SET isDefault = 1 WHERE id = :bookId")
    suspend fun setDefaultBook(bookId: String)

    @Query("UPDATE books SET isDeleted = 1, updatedAt = :timestamp WHERE id = :bookId")
    suspend fun softDeleteBook(bookId: String, timestamp: Long = System.currentTimeMillis())
}
