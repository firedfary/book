package com.flowledger.app.repository

import com.flowledger.app.data.dao.AccountDao
import com.flowledger.app.data.dao.PostingDao
import com.flowledger.app.data.dao.TransactionDao
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.data.model.AccountWithBalance
import com.flowledger.app.data.model.PostingEntity
import com.flowledger.app.data.model.TransactionDisplayItem
import com.flowledger.app.data.model.TransactionEntity
import com.flowledger.app.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

class LedgerRepository(
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val postingDao: PostingDao
) {

    // 1. 账户流与余额聚合
    val allAccountsWithBalances: Flow<List<AccountWithBalance>> =
        accountDao.getAccountsWithBalancesFlow().map { rows ->
            rows.map { AccountWithBalance(it.account, it.currentBalance) }
        }

    val assetAccounts: Flow<List<AccountWithBalance>> =
        allAccountsWithBalances.map { list ->
            list.filter { it.account.type == AccountType.ASSET }
        }

    val liabilityAccounts: Flow<List<AccountWithBalance>> =
        allAccountsWithBalances.map { list ->
            list.filter { it.account.type == AccountType.LIABILITY }
        }

    val expenseAccounts: Flow<List<AccountEntity>> =
        accountDao.getAccountsByTypeFlow(AccountType.EXPENSE)

    val incomeAccounts: Flow<List<AccountEntity>> =
        accountDao.getAccountsByTypeFlow(AccountType.INCOME)

    // 财务大盘指标
    data class FinancialSummary(
        val totalAssets: Double,
        val totalLiabilities: Double,
        val netWorth: Double
    )

    val financialSummary: Flow<FinancialSummary> =
        allAccountsWithBalances.map { accounts ->
            var totalAssets = 0.0
            var totalLiab = 0.0
            for (item in accounts) {
                when (item.account.type) {
                    AccountType.ASSET -> {
                        if (item.currentBalance > 0) {
                            totalAssets += item.currentBalance
                        }
                    }
                    AccountType.LIABILITY -> {
                        // 负债账户如果余额小于0，表示当前欠款（如信用卡刷卡后余额为负）
                        if (item.currentBalance < 0) {
                            totalLiab += -item.currentBalance
                        }
                    }
                    else -> {}
                }
            }
            FinancialSummary(
                totalAssets = totalAssets,
                totalLiabilities = totalLiab,
                netWorth = totalAssets - totalLiab
            )
        }

    // 2. 交易流水列表 (转换为展示项)
    val transactionsFlow: Flow<List<TransactionDisplayItem>> =
        combine(
            transactionDao.getAllTransactionsWithPostings(),
            accountDao.getAllAccountsFlow()
        ) { txList, accounts ->
            val accountMap = accounts.associateBy { it.id }
            txList.map { item ->
                val tx = item.transaction
                val postings = item.postings

                // 分析分录流向：负数表示流出方，正数表示流入方
                val outflows = postings.filter { it.amount < 0 }
                val inflows = postings.filter { it.amount > 0 }

                var fromName = "未知账户"
                var toName = "未知账户"
                var displayAmount = 0.0
                var feeName: String? = null
                var feeAmount: Double? = null

                when (tx.type) {
                    TransactionType.EXPENSE -> {
                        // 出账方通常是资产或负债节点，入账方是支出分类节点
                        val from = outflows.firstOrNull()
                        val to = inflows.firstOrNull()
                        fromName = from?.let { accountMap[it.accountId]?.name } ?: "账户"
                        toName = to?.let { accountMap[it.accountId]?.name } ?: tx.title
                        displayAmount = to?.amount ?: (outflows.firstOrNull()?.let { -it.amount } ?: 0.0)
                    }
                    TransactionType.INCOME -> {
                        // 出账方通常是收入分类节点，入账方是资产节点
                        val from = outflows.firstOrNull()
                        val to = inflows.firstOrNull()
                        fromName = from?.let { accountMap[it.accountId]?.name } ?: tx.title
                        toName = to?.let { accountMap[it.accountId]?.name } ?: "账户"
                        displayAmount = to?.amount ?: 0.0
                    }
                    TransactionType.TRANSFER, TransactionType.REPAYMENT -> {
                        val toPosting = inflows.firstOrNull {
                            val acc = accountMap[it.accountId]
                            acc?.type != AccountType.EXPENSE // 排除手续费
                        }
                        val feePosting = inflows.firstOrNull {
                            val acc = accountMap[it.accountId]
                            acc?.type == AccountType.EXPENSE
                        }

                        val fromPosting = outflows.firstOrNull()

                        fromName = fromPosting?.let { accountMap[it.accountId]?.name } ?: "源账户"
                        toName = toPosting?.let { accountMap[it.accountId]?.name } ?: "目标账户"
                        displayAmount = toPosting?.amount ?: (outflows.firstOrNull()?.let { -it.amount } ?: 0.0)

                        if (feePosting != null && feePosting.amount > 0) {
                            feeName = accountMap[feePosting.accountId]?.name ?: "手续费"
                            feeAmount = feePosting.amount
                        }
                    }
                    else -> {
                        displayAmount = inflows.sumOf { it.amount }
                    }
                }

                TransactionDisplayItem(
                    transactionId = tx.id,
                    title = tx.title,
                    occurredAt = tx.occurredAt,
                    type = tx.type,
                    amount = displayAmount,
                    fromAccountName = fromName,
                    toAccountName = toName,
                    feeAccountName = feeName,
                    feeAmount = feeAmount,
                    memo = tx.memo
                )
            }
        }

    // 3. 核心记账操作 (原子事务与数学守恒校验)

    /**
     * 记录普通消费支出
     * 守恒: -amount (资金账户) + amount (支出分类账户) = 0
     */
    suspend fun recordExpense(
        fromAccountId: String,
        expenseCategoryAccountId: String,
        amount: Double,
        title: String,
        occurredAt: Long = System.currentTimeMillis(),
        memo: String = ""
    ) {
        require(amount > 0) { "金额必须大于0" }
        val txId = UUID.randomUUID().toString()
        val tx = TransactionEntity(
            id = txId,
            title = title.ifBlank { "日常消费" },
            occurredAt = occurredAt,
            type = TransactionType.EXPENSE,
            memo = memo
        )
        val postings = listOf(
            PostingEntity(
                transactionId = txId,
                accountId = fromAccountId,
                amount = -amount,
                memo = "支出扣款"
            ),
            PostingEntity(
                transactionId = txId,
                accountId = expenseCategoryAccountId,
                amount = amount,
                memo = "计入支出"
            )
        )
        verifyBalance(postings)
        transactionDao.insertFullTransaction(tx, postings)
    }

    /**
     * 记录收入
     * 守恒: -amount (收入分类账户) + amount (资金账户) = 0
     */
    suspend fun recordIncome(
        toAccountId: String,
        incomeCategoryAccountId: String,
        amount: Double,
        title: String,
        occurredAt: Long = System.currentTimeMillis(),
        memo: String = ""
    ) {
        require(amount > 0) { "金额必须大于0" }
        val txId = UUID.randomUUID().toString()
        val tx = TransactionEntity(
            id = txId,
            title = title.ifBlank { "收入入账" },
            occurredAt = occurredAt,
            type = TransactionType.INCOME,
            memo = memo
        )
        val postings = listOf(
            PostingEntity(
                transactionId = txId,
                accountId = incomeCategoryAccountId,
                amount = -amount,
                memo = "收入来源"
            ),
            PostingEntity(
                transactionId = txId,
                accountId = toAccountId,
                amount = amount,
                memo = "收入到账"
            )
        )
        verifyBalance(postings)
        transactionDao.insertFullTransaction(tx, postings)
    }

    /**
     * 记录资金流动 (转账/划转/信用卡还款，支持手续费)
     * 无手续费守恒: -amount + amount = 0
     * 含手续费守恒: -(amount + fee) + amount + fee = 0
     */
    suspend fun recordTransfer(
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        feeAccountId: String? = null,
        feeAmount: Double = 0.0,
        isRepayment: Boolean = false,
        title: String = "",
        occurredAt: Long = System.currentTimeMillis(),
        memo: String = ""
    ) {
        require(amount > 0) { "转账金额必须大于0" }
        require(feeAmount >= 0) { "手续费不能为负" }

        val txId = UUID.randomUUID().toString()
        val defaultTitle = if (isRepayment) "信用卡还款" else "账户转账"
        val tx = TransactionEntity(
            id = txId,
            title = title.ifBlank { defaultTitle },
            occurredAt = occurredAt,
            type = if (isRepayment) TransactionType.REPAYMENT else TransactionType.TRANSFER,
            memo = memo
        )

        val postings = mutableListOf<PostingEntity>()
        val totalOutflow = amount + feeAmount

        // 1. 转出账户扣除总额（本金 + 手续费）
        postings.add(
            PostingEntity(
                transactionId = txId,
                accountId = fromAccountId,
                amount = -totalOutflow,
                memo = if (feeAmount > 0) "转出(含手续费 ¥$feeAmount)" else "转出"
            )
        )

        // 2. 转入账户到账金额（本金）
        postings.add(
            PostingEntity(
                transactionId = txId,
                accountId = toAccountId,
                amount = amount,
                memo = "转入"
            )
        )

        // 3. 手续费分录（若有）
        if (feeAmount > 0 && !feeAccountId.isNullOrBlank()) {
            postings.add(
                PostingEntity(
                    transactionId = txId,
                    accountId = feeAccountId,
                    amount = feeAmount,
                    memo = "转账手续费"
                )
            )
        }

        verifyBalance(postings)
        transactionDao.insertFullTransaction(tx, postings)
    }

    suspend fun addAccount(account: AccountEntity) {
        accountDao.insertAccount(account)
    }

    suspend fun updateAccount(account: AccountEntity) {
        accountDao.updateAccount(account)
    }

    suspend fun deleteAccount(account: AccountEntity) {
        accountDao.deleteAccount(account)
    }

    suspend fun deleteTransaction(transactionId: String) {
        transactionDao.deleteTransaction(transactionId)
    }

    private fun verifyBalance(postings: List<PostingEntity>) {
        val sum = postings.sumOf { it.amount }
        // 允许浮点数微小舍入误差
        if (kotlin.math.abs(sum) > 0.0001) {
            throw IllegalStateException("复式分录资金不守恒！总和为: $sum")
        }
    }
}
