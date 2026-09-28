package com.flowledger.app.utils

import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.BookEntity
import com.flowledger.app.data.model.PostingEntity
import com.flowledger.app.data.model.TransactionEntity
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.util.UUID

data class BookExportPackage(
    val formatVersion: Int = 1,
    val app: String = "FlowLedger",
    val exportedAt: Long = System.currentTimeMillis(),
    val book: BookEntity,
    val accounts: List<AccountEntity>,
    val transactions: List<TransactionWithPostingsExportItem>
)

data class TransactionWithPostingsExportItem(
    val transaction: TransactionEntity,
    val postings: List<PostingEntity>
)

data class ImportedBookData(
    val book: BookEntity,
    val accounts: List<AccountEntity>,
    val transactions: List<TransactionEntity>,
    val postings: List<PostingEntity>
)

object LedgerExportImportHelper {

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .create()

    fun exportToJson(
        book: BookEntity,
        accounts: List<AccountEntity>,
        transactionsWithPostings: List<TransactionWithPostingsExportItem>
    ): String {
        val pack = BookExportPackage(
            formatVersion = 1,
            app = "FlowLedger",
            exportedAt = System.currentTimeMillis(),
            book = book,
            accounts = accounts,
            transactions = transactionsWithPostings
        )
        return gson.toJson(pack)
    }

    /**
     * 解析导入的 JSON 字符串，执行复式记账资金守恒校验，
     * 并重新映射新的 UUID (Remap IDs) 避免在同一数据库中出现主键冲突。
     */
    fun parseAndRemapImport(
        jsonString: String,
        newBookNameOverride: String? = null
    ): Result<ImportedBookData> {
        return try {
            val pack = gson.fromJson(jsonString, BookExportPackage::class.java)
                ?: return Result.failure(IllegalArgumentException("解析账本数据失败，内容为空"))

            if (pack.app != "FlowLedger") {
                return Result.failure(IllegalArgumentException("不支持的数据格式，非 FlowLedger 导出数据"))
            }

            // 1. 生成新账本 ID 与基本信息
            val newBookId = UUID.randomUUID().toString()
            val bookName = newBookNameOverride?.ifBlank { null }
                ?: "${pack.book.name} (导入)"
            val newBook = pack.book.copy(
                id = newBookId,
                name = bookName,
                isDefault = false,
                isDeleted = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            // 2. 重映射账户 ID 字典: oldAccountId -> newAccountId
            val accountIdMap = mutableMapOf<String, String>()
            val remappedAccounts = pack.accounts.map { acc ->
                val newAccId = UUID.randomUUID().toString()
                accountIdMap[acc.id] = newAccId
                acc.copy(
                    id = newAccId,
                    bookId = newBookId,
                    isDeleted = false,
                    updatedAt = System.currentTimeMillis()
                )
            }

            // 3. 重映射交易与分录 ID，并严格校验守恒
            val remappedTransactions = mutableListOf<TransactionEntity>()
            val remappedPostings = mutableListOf<PostingEntity>()

            for (item in pack.transactions) {
                val oldTx = item.transaction
                val newTxId = UUID.randomUUID().toString()

                val newTx = oldTx.copy(
                    id = newTxId,
                    bookId = newBookId,
                    isDeleted = false,
                    updatedAt = System.currentTimeMillis()
                )
                remappedTransactions.add(newTx)

                // 校验分录守恒
                val sum = item.postings.sumOf { it.amount }
                if (kotlin.math.abs(sum) > 0.0001) {
                    return Result.failure(IllegalStateException("导入失败：交易【${oldTx.title}】的分录资金不守恒（总和: $sum）"))
                }

                for (p in item.postings) {
                    val mappedAccountId = accountIdMap[p.accountId]
                        ?: return Result.failure(IllegalStateException("导入失败：交易分录引用了未知的账户 ID: ${p.accountId}"))

                    val newPosting = p.copy(
                        id = UUID.randomUUID().toString(),
                        transactionId = newTxId,
                        accountId = mappedAccountId
                    )
                    remappedPostings.add(newPosting)
                }
            }

            Result.success(
                ImportedBookData(
                    book = newBook,
                    accounts = remappedAccounts,
                    transactions = remappedTransactions,
                    postings = remappedPostings
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
