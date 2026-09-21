package com.flowledger.app

import com.flowledger.app.data.model.AccountCategory
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.data.model.AccountWithBalance
import com.flowledger.app.data.model.PostingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerRepositoryTest {

    @Test
    fun testPostingBalanceConservation_TransferWithFee() {
        val transferAmount = 1000.00
        val feeAmount = 1.50
        val totalOutflow = transferAmount + feeAmount

        val postings = listOf(
            PostingEntity(
                transactionId = "tx-1",
                accountId = "wechat-acc",
                amount = -totalOutflow,
                memo = "转出(含手续费)"
            ),
            PostingEntity(
                transactionId = "tx-1",
                accountId = "bank-acc",
                amount = transferAmount,
                memo = "转入"
            ),
            PostingEntity(
                transactionId = "tx-1",
                accountId = "fee-acc",
                amount = feeAmount,
                memo = "提现手续费"
            )
        )

        val sum = postings.sumOf { it.amount }
        assertEquals(0.0, sum, 0.0001)
    }

    @Test
    fun testPostingBalanceConservation_Expense() {
        val expenseAmount = 45.00

        val postings = listOf(
            PostingEntity(
                transactionId = "tx-2",
                accountId = "alipay-acc",
                amount = -expenseAmount,
                memo = "付款"
            ),
            PostingEntity(
                transactionId = "tx-2",
                accountId = "dining-acc",
                amount = expenseAmount,
                memo = "餐饮支出"
            )
        )

        val sum = postings.sumOf { it.amount }
        assertEquals(0.0, sum, 0.0001)
    }

    @Test
    fun testPostingBalanceConservation_Repayment() {
        val repayAmount = 3000.00

        val postings = listOf(
            PostingEntity(
                transactionId = "tx-3",
                accountId = "bank-card",
                amount = -repayAmount,
                memo = "还款转出"
            ),
            PostingEntity(
                transactionId = "tx-3",
                accountId = "credit-card",
                amount = repayAmount,
                memo = "信用卡入账"
            )
        )

        val sum = postings.sumOf { it.amount }
        assertEquals(0.0, sum, 0.0001)
    }

    @Test
    fun testLiabilityAccount_AvailableCreditCalculation() {
        val creditCard = AccountEntity(
            id = "cc-1",
            name = "招行信用卡",
            type = AccountType.LIABILITY,
            category = AccountCategory.CREDIT_CARD,
            creditLimit = 30000.00,
            initialBalance = 0.00
        )

        // 刷卡消费 3500 元后，余额变为 -3500.00
        val accountWithBalance = AccountWithBalance(
            account = creditCard,
            currentBalance = -3500.00
        )

        assertEquals(3500.00, accountWithBalance.debtAmount, 0.001)
        assertEquals(26500.00, accountWithBalance.availableCredit, 0.001)
    }
}
