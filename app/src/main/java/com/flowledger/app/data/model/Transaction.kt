package com.flowledger.app.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

enum class TransactionType {
    EXPENSE,    // 消费支出 (资产/负债 -> 支出分类)
    INCOME,     // 收入入账 (收入分类 -> 资产)
    TRANSFER,   // 内部流转 (资产 -> 资产，或资产 -> 负债还款)
    REPAYMENT,  // 负债还款 (资产 -> 负债)
    ADJUST      // 余额校准
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val occurredAt: Long = System.currentTimeMillis(),
    val type: TransactionType,
    val memo: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncVersion: Long = 1L
)

data class TransactionWithPostings(
    @Embedded val transaction: TransactionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "transactionId"
    )
    val postings: List<PostingEntity>
)

data class TransactionDisplayItem(
    val transactionId: String,
    val title: String,
    val occurredAt: Long,
    val type: TransactionType,
    val amount: Double,
    val fromAccountName: String,
    val toAccountName: String,
    val feeAccountName: String? = null,
    val feeAmount: Double? = null,
    val memo: String = ""
)
