package com.flowledger.app

import com.flowledger.app.data.model.ImportedBillCandidate
import com.flowledger.app.data.model.PostingEntity
import com.flowledger.app.data.model.TransactionEntity
import com.flowledger.app.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.math.abs

class DoubleEntryImportTest {

    @Test
    fun testExpensePostingsSumToZero() {
        val targetAccountId = "acc_icbc_card"
        val expenseCatId = "acc_shopping"
        val candidate = ImportedBillCandidate(
            title = "消费 - 财付通-拼多多",
            occurredAt = System.currentTimeMillis(),
            amount = 33.40,
            type = TransactionType.EXPENSE,
            categoryAccountId = expenseCatId
        )

        val postings = listOf(
            PostingEntity(
                transactionId = "tx_1",
                accountId = targetAccountId,
                amount = -candidate.amount,
                memo = "支出扣款"
            ),
            PostingEntity(
                transactionId = "tx_1",
                accountId = expenseCatId,
                amount = candidate.amount,
                memo = "计入支出"
            )
        )

        val sum = postings.sumOf { it.amount }
        assertTrue("复式记账支出分录代数和必须严格守恒为 0", abs(sum) < 0.0001)
    }

    @Test
    fun testIncomePostingsSumToZero() {
        val targetAccountId = "acc_icbc_card"
        val incomeCatId = "acc_finance"
        val candidate = ImportedBillCandidate(
            title = "利息 - 批量业务",
            occurredAt = System.currentTimeMillis(),
            amount = 0.26,
            type = TransactionType.INCOME,
            categoryAccountId = incomeCatId
        )

        val postings = listOf(
            PostingEntity(
                transactionId = "tx_2",
                accountId = incomeCatId,
                amount = -candidate.amount,
                memo = "收入来源"
            ),
            PostingEntity(
                transactionId = "tx_2",
                accountId = targetAccountId,
                amount = candidate.amount,
                memo = "收入到账"
            )
        )

        val sum = postings.sumOf { it.amount }
        assertTrue("复式记账收入分录代数和必须严格守恒为 0", abs(sum) < 0.0001)
    }

    @Test
    fun testTransferPostingsSumToZero() {
        val fromAccountId = "acc_icbc_card"
        val toAccountId = "acc_alipay"
        val candidate = ImportedBillCandidate(
            title = "跨行汇款 - *帆",
            occurredAt = System.currentTimeMillis(),
            amount = 3729.50,
            type = TransactionType.TRANSFER,
            transferToAccountId = toAccountId
        )

        val postings = listOf(
            PostingEntity(
                transactionId = "tx_3",
                accountId = fromAccountId,
                amount = -candidate.amount,
                memo = "转出"
            ),
            PostingEntity(
                transactionId = "tx_3",
                accountId = toAccountId,
                amount = candidate.amount,
                memo = "转入"
            )
        )

        val sum = postings.sumOf { it.amount }
        assertTrue("复式记账转账分录代数和必须严格守恒为 0", abs(sum) < 0.0001)
    }
}
