package com.flowledger.app.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "postings",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("transactionId"),
        Index("accountId")
    ]
)
data class PostingEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val transactionId: String,
    val accountId: String,
    // 关键设计: 负数表示资金从该节点流出，正数表示资金流入该节点
    // 守恒公理: 同一事务的所有 postings 金额总和必须为 0
    val amount: Double,
    val memo: String = ""
)
