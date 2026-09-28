package com.flowledger.app.sync

import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.BookEntity
import com.flowledger.app.data.model.PostingEntity
import com.flowledger.app.data.model.TransactionEntity

/**
 * 云端同步状态枚举
 */
enum class SyncStatus(val code: Int) {
    LOCAL_ONLY(0),      // 本地就绪 / 已完全同步
    PENDING_UPLOAD(1),  // 本地修改，待上传云端
    PENDING_DOWNLOAD(2),// 远端有更新，待拉取合并
    CONFLICT(3);        // 冲突待解决 (如多端同时修改)

    companion object {
        fun fromCode(code: Int): SyncStatus = entries.find { it.code == code } ?: LOCAL_ONLY
    }
}

/**
 * 云同步数据包结构 (预留云同步扩展)
 */
data class SyncPacket(
    val schemaVersion: Int = 1,
    val deviceId: String,
    val clientTimestamp: Long = System.currentTimeMillis(),
    val books: List<BookEntity> = emptyList(),
    val accounts: List<AccountEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val postings: List<PostingEntity> = emptyList(),
    val deletedIds: List<String> = emptyList() // 墓碑软删除 ID 清单
)

/**
 * 云端同步服务接口规范 (为未来接入后端 REST / WebSocket 同步预留)
 */
interface CloudSyncService {
    suspend fun pullChanges(sinceVersion: Long): Result<SyncPacket>
    suspend fun pushChanges(packet: SyncPacket): Result<Long> // 返回新的 serverVersion
}
