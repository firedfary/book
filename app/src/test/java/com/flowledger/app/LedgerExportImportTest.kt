package com.flowledger.app

import com.flowledger.app.data.model.AccountCategory
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.data.model.BookEntity
import com.flowledger.app.data.model.PostingEntity
import com.flowledger.app.data.model.TransactionEntity
import com.flowledger.app.data.model.TransactionType
import com.flowledger.app.utils.LedgerExportImportHelper
import com.flowledger.app.utils.TransactionWithPostingsExportItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerExportImportTest {

    @Test
    fun testExportAndImportCycle() {
        val originalBook = BookEntity(
            id = "book-1",
            name = "日常账本",
            description = "测试日常流水",
            currency = "CNY"
        )

        val acc1 = AccountEntity(
            id = "acc-bank",
            bookId = "book-1",
            name = "工商银行储蓄卡",
            type = AccountType.ASSET,
            category = AccountCategory.BANK_CARD,
            initialBalance = 5000.00
        )

        val acc2 = AccountEntity(
            id = "acc-dining",
            bookId = "book-1",
            name = "餐饮美食",
            type = AccountType.EXPENSE,
            category = AccountCategory.DINING
        )

        val tx = TransactionEntity(
            id = "tx-1",
            bookId = "book-1",
            title = "午餐外卖",
            type = TransactionType.EXPENSE
        )

        val postings = listOf(
            PostingEntity(
                id = "p-1",
                transactionId = "tx-1",
                accountId = "acc-bank",
                amount = -35.00
            ),
            PostingEntity(
                id = "p-2",
                transactionId = "tx-1",
                accountId = "acc-dining",
                amount = 35.00
            )
        )

        val txExportItems = listOf(
            TransactionWithPostingsExportItem(
                transaction = tx,
                postings = postings
            )
        )

        // 1. 导出为 JSON
        val json = LedgerExportImportHelper.exportToJson(
            book = originalBook,
            accounts = listOf(acc1, acc2),
            transactionsWithPostings = txExportItems
        )

        assertTrue(json.contains("FlowLedger"))
        assertTrue(json.contains("工商银行储蓄卡"))
        assertTrue(json.contains("午餐外卖"))

        // 2. 导入 JSON 并重映射
        val importResult = LedgerExportImportHelper.parseAndRemapImport(json)
        assertTrue(importResult.isSuccess)

        val imported = importResult.getOrThrow()
        assertEquals("日常账本 (导入)", imported.book.name)
        assertNotEquals("book-1", imported.book.id)
        assertEquals(2, imported.accounts.size)
        assertEquals(1, imported.transactions.size)
        assertEquals(2, imported.postings.size)

        // 验证分录依然满足资金零和守恒
        val sum = imported.postings.sumOf { it.amount }
        assertEquals(0.0, sum, 0.0001)

        // 验证重映射后的 transactionId 与 posting.transactionId 一致
        val newTxId = imported.transactions[0].id
        assertTrue(imported.postings.all { it.transactionId == newTxId })
    }

    @Test
    fun testImportFailsOnCorruptedNonZeroSumTransaction() {
        val corruptedJson = """
            {
              "formatVersion": 1,
              "app": "FlowLedger",
              "exportedAt": 1727500000000,
              "book": {
                "id": "book-corrupt",
                "name": "异常账本",
                "description": "",
                "currency": "CNY",
                "colorHex": "#1976D2",
                "iconName": "ic_book",
                "isDefault": false,
                "isDeleted": false,
                "createdAt": 1727500000000,
                "updatedAt": 1727500000000,
                "syncVersion": 1,
                "syncStatus": 0
              },
              "accounts": [
                {
                  "id": "acc-1",
                  "bookId": "book-corrupt",
                  "name": "银行卡",
                  "type": "ASSET",
                  "category": "BANK_CARD",
                  "currency": "CNY",
                  "creditLimit": 0.0,
                  "initialBalance": 0.0,
                  "colorHex": "#1976D2",
                  "iconName": "ic_account",
                  "isArchived": false,
                  "isDeleted": false,
                  "createdAt": 1727500000000,
                  "updatedAt": 1727500000000,
                  "syncVersion": 1,
                  "syncStatus": 0
                }
              ],
              "transactions": [
                {
                  "transaction": {
                    "id": "tx-bad",
                    "bookId": "book-corrupt",
                    "title": "失衡交易",
                    "occurredAt": 1727500000000,
                    "type": "EXPENSE",
                    "memo": "",
                    "isDeleted": false,
                    "createdAt": 1727500000000,
                    "updatedAt": 1727500000000,
                    "syncVersion": 1,
                    "syncStatus": 0
                  },
                  "postings": [
                    {
                      "id": "p-bad",
                      "transactionId": "tx-bad",
                      "accountId": "acc-1",
                      "amount": -100.0,
                      "memo": ""
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val result = LedgerExportImportHelper.parseAndRemapImport(corruptedJson)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("资金不守恒") == true)
    }
}
