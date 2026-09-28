package com.flowledger.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val currency: String = "CNY",
    val colorHex: String = "#1976D2",
    val iconName: String = "ic_book",
    val isDefault: Boolean = false,
    val isDeleted: Boolean = false,       // 软删除标记，为后续云端同步预留墓碑机制 (Tombstone)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncVersion: Long = 1L,           // 本地/服务端递增版本号
    val syncId: String? = null,           // 预留云端记录 ID
    val syncStatus: Int = 0               // 0: 本地就绪/已同步, 1: 待上传增量, 2: 待下载, 3: 冲突待解决
)

data class BookWithStats(
    val book: BookEntity,
    val accountCount: Int = 0,
    val transactionCount: Int = 0,
    val isCurrent: Boolean = false
)
