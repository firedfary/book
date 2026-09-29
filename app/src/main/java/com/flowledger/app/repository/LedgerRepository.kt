package com.flowledger.app.repository

import android.content.Context
import android.content.SharedPreferences
import com.flowledger.app.data.AppDatabase
import com.flowledger.app.data.dao.AccountDao
import com.flowledger.app.data.dao.BookDao
import com.flowledger.app.data.dao.PostingDao
import com.flowledger.app.data.dao.TransactionDao
import com.flowledger.app.data.model.AccountCategory
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.data.model.AccountWithBalance
import com.flowledger.app.data.model.BookEntity
import com.flowledger.app.data.model.BookWithStats
import com.flowledger.app.data.model.ImportedBillCandidate
import com.flowledger.app.data.model.PostingEntity
import com.flowledger.app.data.model.TransactionDisplayItem
import com.flowledger.app.data.model.TransactionEntity
import com.flowledger.app.data.model.TransactionType
import com.flowledger.app.utils.AccountNameUtils
import com.flowledger.app.utils.LedgerExportImportHelper
import com.flowledger.app.utils.TransactionWithPostingsExportItem
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerRepository(
    private val context: Context,
    private val bookDao: BookDao,
    private val accountDao: AccountDao,
    private val transactionDao: TransactionDao,
    private val postingDao: PostingDao
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("flow_ledger_prefs", Context.MODE_PRIVATE)
    }

    private val _currentBookId = MutableStateFlow(
        prefs.getString("current_book_id", AppDatabase.DEFAULT_BOOK_ID) ?: AppDatabase.DEFAULT_BOOK_ID
    )
    val currentBookId = _currentBookId.asStateFlow()

    fun switchBook(bookId: String) {
        _currentBookId.value = bookId
        prefs.edit().putString("current_book_id", bookId).apply()
    }

    // 1. 账本列表与当前激活账本
    val allBooks: Flow<List<BookEntity>> = bookDao.getAllBooksFlow()

    val allBooksWithStats: Flow<List<BookWithStats>> =
        combine(bookDao.getAllBooksFlow(), _currentBookId) { books, currentId ->
            books.map { book ->
                val accCount = accountDao.getAccountCountByBook(book.id)
                val txCount = transactionDao.getTransactionCountByBook(book.id)
                BookWithStats(
                    book = book,
                    accountCount = accCount,
                    transactionCount = txCount,
                    isCurrent = (book.id == currentId)
                )
            }
        }

    val activeBook: Flow<BookEntity?> = _currentBookId.flatMapLatest { bookId ->
        flow {
            emit(bookDao.getBookById(bookId))
        }
    }

    // 2. 账户流与余额聚合 (自动响应当前账本切换)
    val allAccountsWithBalances: Flow<List<AccountWithBalance>> =
        _currentBookId.flatMapLatest { bookId ->
            accountDao.getAccountsWithBalancesByBookFlow(bookId).map { rows ->
                rows.map { AccountWithBalance(it.account, it.currentBalance) }
            }
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
        _currentBookId.flatMapLatest { bookId ->
            accountDao.getAccountsByTypeAndBookFlow(bookId, AccountType.EXPENSE)
        }

    val incomeAccounts: Flow<List<AccountEntity>> =
        _currentBookId.flatMapLatest { bookId ->
            accountDao.getAccountsByTypeAndBookFlow(bookId, AccountType.INCOME)
        }

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

    // 3. 交易流水列表 (当前账本关联)
    val transactionsFlow: Flow<List<TransactionDisplayItem>> =
        _currentBookId.flatMapLatest { bookId ->
            combine(
                transactionDao.getAllTransactionsWithPostingsByBook(bookId),
                accountDao.getAccountsByBookFlow(bookId)
            ) { txList, accounts ->
                val accountMap = accounts.associateBy { it.id }
                txList.map { item ->
                    val tx = item.transaction
                    val postings = item.postings

                    val outflows = postings.filter { it.amount < 0 }
                    val inflows = postings.filter { it.amount > 0 }

                    var fromName = "未知账户"
                    var toName = "未知账户"
                    var displayAmount: Double
                    var feeName: String? = null
                    var feeAmount: Double? = null

                    when (tx.type) {
                        TransactionType.EXPENSE -> {
                            val from = outflows.firstOrNull()
                            val to = inflows.firstOrNull()
                            fromName = from?.let { accountMap[it.accountId]?.name } ?: "账户"
                            toName = to?.let { accountMap[it.accountId]?.name } ?: tx.title
                            displayAmount = to?.amount ?: (outflows.firstOrNull()?.let { -it.amount } ?: 0.0)
                        }
                        TransactionType.INCOME -> {
                            val from = outflows.firstOrNull()
                            val to = inflows.firstOrNull()
                            fromName = from?.let { accountMap[it.accountId]?.name } ?: tx.title
                            toName = to?.let { accountMap[it.accountId]?.name } ?: "账户"
                            displayAmount = to?.amount ?: 0.0
                        }
                        TransactionType.TRANSFER, TransactionType.REPAYMENT -> {
                            val toPosting = inflows.firstOrNull {
                                val acc = accountMap[it.accountId]
                                acc?.type != AccountType.EXPENSE
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
        }

    // 4. 账本管理 (添加、删除、导出、导入)

    /**
     * 创建新账本，并初始化该账本的标准收支分类节点
     */
    suspend fun createBook(
        name: String,
        description: String = "",
        currency: String = "CNY",
        colorHex: String = "#1976D2"
    ): BookEntity {
        require(name.isNotBlank()) { "账本名称不能为空" }
        val bookId = UUID.randomUUID().toString()
        val newBook = BookEntity(
            id = bookId,
            name = name.trim(),
            description = description.trim(),
            currency = currency,
            colorHex = colorHex,
            isDefault = false
        )
        bookDao.insertBook(newBook)

        // 为新账本预置标准收支分类节点，确保复式记账守恒即时可用
        val standardNodes = listOf(
            AccountEntity(
                bookId = bookId,
                name = "餐饮美食",
                type = AccountType.EXPENSE,
                category = AccountCategory.DINING,
                colorHex = "#FF7043",
                iconName = "ic_dining"
            ),
            AccountEntity(
                bookId = bookId,
                name = "交通出行",
                type = AccountType.EXPENSE,
                category = AccountCategory.TRANSPORT,
                colorHex = "#26A69A",
                iconName = "ic_transport"
            ),
            AccountEntity(
                bookId = bookId,
                name = "日常购物",
                type = AccountType.EXPENSE,
                category = AccountCategory.SHOPPING,
                colorHex = "#AB47BC",
                iconName = "ic_shopping"
            ),
            AccountEntity(
                bookId = bookId,
                name = "住房物业",
                type = AccountType.EXPENSE,
                category = AccountCategory.HOUSING,
                colorHex = "#78909C",
                iconName = "ic_housing"
            ),
            AccountEntity(
                bookId = bookId,
                name = "提现与转账手续费",
                type = AccountType.EXPENSE,
                category = AccountCategory.FEE,
                colorHex = "#8D6E63",
                iconName = "ic_fee"
            ),
            AccountEntity(
                bookId = bookId,
                name = "休闲娱乐",
                type = AccountType.EXPENSE,
                category = AccountCategory.ENTERTAINMENT,
                colorHex = "#FFA726",
                iconName = "ic_entertainment"
            ),
            AccountEntity(
                bookId = bookId,
                name = "工资薪金",
                type = AccountType.INCOME,
                category = AccountCategory.SALARY,
                colorHex = "#43A047",
                iconName = "ic_salary"
            ),
            AccountEntity(
                bookId = bookId,
                name = "理财收益",
                type = AccountType.INCOME,
                category = AccountCategory.FINANCE_INCOME,
                colorHex = "#3949AB",
                iconName = "ic_finance"
            )
        )
        accountDao.insertAccounts(standardNodes)
        return newBook
    }

    /**
     * 删除账本（至少保留一个账本）
     */
    suspend fun deleteBook(bookId: String): Result<Unit> {
        val totalActive = bookDao.getActiveBookCount()
        if (totalActive <= 1) {
            return Result.failure(IllegalStateException("无法删除：系统中至少需要保留一个账本"))
        }

        // 清理关联账户与交易
        accountDao.deleteAccountsByBook(bookId)
        transactionDao.deleteTransactionsByBook(bookId)
        bookDao.deleteBookById(bookId)

        // 若当前选中的正是被删除的账本，自动回退到剩余账本中的第一个
        if (_currentBookId.value == bookId) {
            val remainingBooks = bookDao.getAllBooks()
            val fallback = remainingBooks.firstOrNull() ?: BookEntity(
                id = AppDatabase.DEFAULT_BOOK_ID,
                name = "默认账本"
            )
            switchBook(fallback.id)
        }
        return Result.success(Unit)
    }

    /**
     * 导出指定账本为 JSON 文本
     */
    suspend fun exportBookToJson(bookId: String): Result<String> {
        val book = bookDao.getBookById(bookId)
            ?: return Result.failure(IllegalArgumentException("指定的账本不存在"))
        val accounts = accountDao.getAccountsByBook(bookId)
        val txWithPostings = transactionDao.getAllTransactionsWithPostingsByBookSync(bookId)

        val exportItems = txWithPostings.map {
            TransactionWithPostingsExportItem(
                transaction = it.transaction,
                postings = it.postings
            )
        }
        val json = LedgerExportImportHelper.exportToJson(book, accounts, exportItems)
        return Result.success(json)
    }

    /**
     * 从 JSON 文本导入账本
     */
    suspend fun importBookFromJson(jsonContent: String): Result<BookEntity> {
        val parsed = LedgerExportImportHelper.parseAndRemapImport(jsonContent)
        if (parsed.isFailure) {
            return Result.failure(parsed.exceptionOrNull()!!)
        }

        val imported = parsed.getOrThrow()
        bookDao.insertBook(imported.book)
        accountDao.insertAccounts(imported.accounts)
        transactionDao.insertTransactions(imported.transactions)
        postingDao.insertPostings(imported.postings)

        // 自动切换至导入的新账本
        switchBook(imported.book.id)
        return Result.success(imported.book)
    }

    // 5. 自定义账户管理与同名智能自动编号

    /**
     * 添加自定义账户（支持同名自动追加递增数字）
     */
    suspend fun addCustomAccount(
        name: String,
        type: AccountType,
        category: AccountCategory,
        currency: String = "CNY",
        initialBalance: Double = 0.0,
        creditLimit: Double = 0.0,
        billingDay: Int? = null,
        repaymentDay: Int? = null,
        colorHex: String = "#1976D2",
        iconName: String = "ic_account"
    ): AccountEntity {
        val targetBookId = _currentBookId.value

        // 读取当前账本中已存在的所有账户名称
        val existingNames = accountDao.getAllAccountNamesInBook(targetBookId)

        // 调用命名解析器生成唯一不重名的新账户名
        val uniqueName = AccountNameUtils.generateUniqueAccountName(name, existingNames)

        val account = AccountEntity(
            bookId = targetBookId,
            name = uniqueName,
            type = type,
            category = category,
            currency = currency,
            initialBalance = initialBalance,
            creditLimit = if (type == AccountType.LIABILITY) creditLimit else 0.0,
            billingDay = if (type == AccountType.LIABILITY) billingDay else null,
            repaymentDay = if (type == AccountType.LIABILITY) repaymentDay else null,
            colorHex = colorHex,
            iconName = iconName
        )
        accountDao.insertAccount(account)
        return account
    }

    suspend fun getAccountTransactionCount(accountId: String): Int {
        return transactionDao.getTransactionCountForAccount(accountId)
    }

    /**
     * 删除账户
     * @param cascadeTransactions 是否级联删除包含该账户的所有交易流水
     */
    suspend fun deleteAccount(accountId: String, cascadeTransactions: Boolean): Result<Unit> {
        val txCount = transactionDao.getTransactionCountForAccount(accountId)
        if (txCount > 0 && !cascadeTransactions) {
            return Result.failure(IllegalStateException("该账户包含 $txCount 笔交易流水，需确认级联删除或选择归档"))
        }

        if (txCount > 0) {
            transactionDao.cascadeDeleteAccountTransactions(accountId)
        }
        accountDao.deleteAccountById(accountId)
        return Result.success(Unit)
    }

    suspend fun archiveAccount(accountId: String) {
        accountDao.archiveAccount(accountId)
    }

    // 6. 记账操作 (自动关联当前 activeBookId 并确保守恒)

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
            bookId = _currentBookId.value,
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
            bookId = _currentBookId.value,
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
            bookId = _currentBookId.value,
            title = title.ifBlank { defaultTitle },
            occurredAt = occurredAt,
            type = if (isRepayment) TransactionType.REPAYMENT else TransactionType.TRANSFER,
            memo = memo
        )

        val postings = mutableListOf<PostingEntity>()
        val totalOutflow = amount + feeAmount

        postings.add(
            PostingEntity(
                transactionId = txId,
                accountId = fromAccountId,
                amount = -totalOutflow,
                memo = if (feeAmount > 0) "转出(含手续费 ¥$feeAmount)" else "转出"
            )
        )

        postings.add(
            PostingEntity(
                transactionId = txId,
                accountId = toAccountId,
                amount = amount,
                memo = "转入"
            )
        )

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

    suspend fun deleteTransaction(transactionId: String) {
        transactionDao.deleteTransaction(transactionId)
    }

    // 7. 智能截图流水批量导入与对账融入

    /**
     * 针对候选流水进行相近时间与金额的查重
     */
    suspend fun checkDuplicates(
        targetAccountId: String,
        candidates: List<ImportedBillCandidate>
    ): List<ImportedBillCandidate> {
        val targetBookId = _currentBookId.value
        val twoMinutes = 2 * 60 * 1000L

        return candidates.map { candidate ->
            val startTime = candidate.occurredAt - twoMinutes
            val endTime = candidate.occurredAt + twoMinutes
            val existing = transactionDao.getTransactionsForAccountInTimeRange(
                bookId = targetBookId,
                accountId = targetAccountId,
                startTime = startTime,
                endTime = endTime
            )
            val isDup = existing.any { tx ->
                tx.title == candidate.title || tx.title.contains(candidate.title) || candidate.title.contains(tx.title)
            }
            candidate.copy(
                isDuplicate = isDup,
                isSelected = if (isDup) false else candidate.isSelected
            )
        }
    }

    /**
     * 将用户勾选的候选流水装配为严格平衡的复式分录并批量原子写入数据库
     */
    suspend fun importBillCandidates(
        targetAccountId: String,
        candidates: List<ImportedBillCandidate>
    ): Result<Int> {
        val selected = candidates.filter { it.isSelected && it.amount > 0 }
        if (selected.isEmpty()) return Result.success(0)

        val targetBookId = _currentBookId.value
        val accountsInBook = accountDao.getAccountsByBook(targetBookId)
        val defaultExpenseAccount = accountsInBook.firstOrNull { it.type == AccountType.EXPENSE }
            ?: return Result.failure(IllegalStateException("当前账本缺少支出分类节点，请先初始化分类账户"))
        val defaultIncomeAccount = accountsInBook.firstOrNull { it.type == AccountType.INCOME }
            ?: return Result.failure(IllegalStateException("当前账本缺少收入分类节点，请先初始化分类账户"))
        val defaultAssetAccount = accountsInBook.firstOrNull { it.type == AccountType.ASSET && it.id != targetAccountId }

        val txEntities = mutableListOf<TransactionEntity>()
        val postingEntities = mutableListOf<PostingEntity>()

        for (item in selected) {
            val txId = UUID.randomUUID().toString()
            val tx = TransactionEntity(
                id = txId,
                bookId = targetBookId,
                title = item.title,
                occurredAt = item.occurredAt,
                type = item.type,
                memo = if (item.originalRawText.isNotBlank()) "截图导入: ${item.originalRawText}" else "截图导入"
            )
            txEntities.add(tx)

            val itemPostings = mutableListOf<PostingEntity>()

            when (item.type) {
                TransactionType.EXPENSE -> {
                    val expCatId = item.categoryAccountId ?: defaultExpenseAccount.id
                    itemPostings.add(
                        PostingEntity(
                            transactionId = txId,
                            accountId = targetAccountId,
                            amount = -item.amount,
                            memo = "支出扣款"
                        )
                    )
                    itemPostings.add(
                        PostingEntity(
                            transactionId = txId,
                            accountId = expCatId,
                            amount = item.amount,
                            memo = "计入支出"
                        )
                    )
                }
                TransactionType.INCOME -> {
                    val incCatId = item.categoryAccountId ?: defaultIncomeAccount.id
                    itemPostings.add(
                        PostingEntity(
                            transactionId = txId,
                            accountId = incCatId,
                            amount = -item.amount,
                            memo = "收入来源"
                        )
                    )
                    itemPostings.add(
                        PostingEntity(
                            transactionId = txId,
                            accountId = targetAccountId,
                            amount = item.amount,
                            memo = "收入到账"
                        )
                    )
                }
                TransactionType.TRANSFER, TransactionType.REPAYMENT -> {
                    val toAccId = item.transferToAccountId ?: defaultAssetAccount?.id ?: targetAccountId
                    itemPostings.add(
                        PostingEntity(
                            transactionId = txId,
                            accountId = targetAccountId,
                            amount = -item.amount,
                            memo = "转出"
                        )
                    )
                    itemPostings.add(
                        PostingEntity(
                            transactionId = txId,
                            accountId = toAccId,
                            amount = item.amount,
                            memo = "转入"
                        )
                    )
                }
                else -> {
                    val expCatId = item.categoryAccountId ?: defaultExpenseAccount.id
                    itemPostings.add(
                        PostingEntity(
                            transactionId = txId,
                            accountId = targetAccountId,
                            amount = -item.amount,
                            memo = "支出扣款"
                        )
                    )
                    itemPostings.add(
                        PostingEntity(
                            transactionId = txId,
                            accountId = expCatId,
                            amount = item.amount,
                            memo = "计入支出"
                        )
                    )
                }
            }

            verifyBalance(itemPostings)
            postingEntities.addAll(itemPostings)
        }

        transactionDao.insertFullTransactionsBatch(txEntities, postingEntities)
        return Result.success(selected.size)
    }

    /**
     * 账户余额校准 (根据截图最新余额生成一笔校准分录)
     */
    suspend fun adjustAccountBalance(
        targetAccountId: String,
        targetBalance: Double,
        reason: String = "截图对账期末校准"
    ): Result<Unit> {
        val current = accountDao.getAccountBalance(targetAccountId) ?: 0.0
        val delta = targetBalance - current
        if (kotlin.math.abs(delta) < 0.005) {
            return Result.success(Unit) // 已经一致，无需校准
        }

        val targetBookId = _currentBookId.value
        val txId = UUID.randomUUID().toString()
        val tx = TransactionEntity(
            id = txId,
            bookId = targetBookId,
            title = "余额校准",
            occurredAt = System.currentTimeMillis(),
            type = TransactionType.ADJUST,
            memo = reason
        )

        val postings = mutableListOf<PostingEntity>()
        if (delta > 0) {
            // 需要增加余额: 从理财/收益分类流入资产
            val incomeAcc = accountDao.getAccountsByBook(targetBookId).firstOrNull { it.type == AccountType.INCOME }
                ?: return Result.failure(IllegalStateException("未找到收入分类节点"))
            postings.add(
                PostingEntity(
                    transactionId = txId,
                    accountId = incomeAcc.id,
                    amount = -delta,
                    memo = "校准来源"
                )
            )
            postings.add(
                PostingEntity(
                    transactionId = txId,
                    accountId = targetAccountId,
                    amount = delta,
                    memo = "余额调增"
                )
            )
        } else {
            // 需要减少余额: 从资产流出至支出分类
            val expenseAcc = accountDao.getAccountsByBook(targetBookId).firstOrNull { it.type == AccountType.EXPENSE }
                ?: return Result.failure(IllegalStateException("未找到支出分类节点"))
            val outflow = -delta
            postings.add(
                PostingEntity(
                    transactionId = txId,
                    accountId = targetAccountId,
                    amount = -outflow,
                    memo = "余额调减"
                )
            )
            postings.add(
                PostingEntity(
                    transactionId = txId,
                    accountId = expenseAcc.id,
                    amount = outflow,
                    memo = "校准计损"
                )
            )
        }

        verifyBalance(postings)
        transactionDao.insertFullTransaction(tx, postings)
        return Result.success(Unit)
    }

    private fun verifyBalance(postings: List<PostingEntity>) {
        val sum = postings.sumOf { it.amount }
        if (kotlin.math.abs(sum) > 0.0001) {
            throw IllegalStateException("复式分录资金不守恒！总和为: $sum")
        }
    }
}
