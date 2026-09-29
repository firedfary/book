package com.flowledger.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.flowledger.app.data.model.AccountCategory
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.data.model.BookEntity
import com.flowledger.app.data.model.ImportedBillCandidate
import com.flowledger.app.repository.LedgerRepository
import kotlinx.coroutines.launch

class MainViewModel(private val repository: LedgerRepository) : ViewModel() {

    // 账本状态与列表
    val currentBookId = repository.currentBookId.asLiveData()
    val allBooksWithStats = repository.allBooksWithStats.asLiveData()
    val activeBook = repository.activeBook.asLiveData()

    // 财务大盘与交易流水 (响应当前激活账本)
    val allAccountsWithBalances = repository.allAccountsWithBalances.asLiveData()
    val assetAccounts = repository.assetAccounts.asLiveData()
    val liabilityAccounts = repository.liabilityAccounts.asLiveData()
    val expenseAccounts = repository.expenseAccounts.asLiveData()
    val incomeAccounts = repository.incomeAccounts.asLiveData()
    val financialSummary = repository.financialSummary.asLiveData()
    val transactions = repository.transactionsFlow.asLiveData()

    fun switchBook(bookId: String) {
        repository.switchBook(bookId)
    }

    fun createBook(
        name: String,
        description: String = "",
        currency: String = "CNY",
        colorHex: String = "#1976D2",
        onSuccess: (BookEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val newBook = repository.createBook(name, description, currency, colorHex)
                repository.switchBook(newBook.id)
                onSuccess(newBook)
            } catch (e: Exception) {
                onError(e.message ?: "创建账本失败")
            }
        }
    }

    fun deleteBook(bookId: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteBook(bookId)
            if (result.isSuccess) {
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "删除账本失败")
            }
        }
    }

    fun exportBook(bookId: String, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.exportBookToJson(bookId)
            if (result.isSuccess) {
                onSuccess(result.getOrThrow())
            } else {
                onError(result.exceptionOrNull()?.message ?: "导出账本失败")
            }
        }
    }

    fun importBook(jsonContent: String, onSuccess: (BookEntity) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.importBookFromJson(jsonContent)
            if (result.isSuccess) {
                onSuccess(result.getOrThrow())
            } else {
                onError(result.exceptionOrNull()?.message ?: "导入账本失败")
            }
        }
    }

    fun addCustomAccount(
        name: String,
        type: AccountType,
        category: AccountCategory,
        currency: String = "CNY",
        initialBalance: Double = 0.0,
        creditLimit: Double = 0.0,
        billingDay: Int? = null,
        repaymentDay: Int? = null,
        colorHex: String = "#1976D2",
        onSuccess: (AccountEntity) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val created = repository.addCustomAccount(
                    name = name,
                    type = type,
                    category = category,
                    currency = currency,
                    initialBalance = initialBalance,
                    creditLimit = creditLimit,
                    billingDay = billingDay,
                    repaymentDay = repaymentDay,
                    colorHex = colorHex
                )
                onSuccess(created)
            } catch (e: Exception) {
                onError(e.message ?: "添加账户失败")
            }
        }
    }

    fun checkAccountTransactionCount(accountId: String, onResult: (Int) -> Unit) {
        viewModelScope.launch {
            val count = repository.getAccountTransactionCount(accountId)
            onResult(count)
        }
    }

    fun deleteAccount(accountId: String, cascade: Boolean, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteAccount(accountId, cascade)
            if (result.isSuccess) {
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "删除账户失败")
            }
        }
    }

    fun archiveAccount(accountId: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.archiveAccount(accountId)
            onSuccess()
        }
    }

    fun recordExpense(
        fromAccountId: String,
        expenseCategoryAccountId: String,
        amount: Double,
        title: String,
        memo: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.recordExpense(
                    fromAccountId = fromAccountId,
                    expenseCategoryAccountId = expenseCategoryAccountId,
                    amount = amount,
                    title = title,
                    memo = memo
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "记账失败")
            }
        }
    }

    fun recordIncome(
        toAccountId: String,
        incomeCategoryAccountId: String,
        amount: Double,
        title: String,
        memo: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.recordIncome(
                    toAccountId = toAccountId,
                    incomeCategoryAccountId = incomeCategoryAccountId,
                    amount = amount,
                    title = title,
                    memo = memo
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "记录收入失败")
            }
        }
    }

    fun recordTransfer(
        fromAccountId: String,
        toAccountId: String,
        amount: Double,
        feeAccountId: String?,
        feeAmount: Double,
        isRepayment: Boolean,
        title: String,
        memo: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                repository.recordTransfer(
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amount = amount,
                    feeAccountId = feeAccountId,
                    feeAmount = feeAmount,
                    isRepayment = isRepayment,
                    title = title,
                    memo = memo
                )
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "转账/还款失败")
            }
        }
    }

    fun deleteTransaction(txId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(txId)
        }
    }

    fun checkDuplicates(
        targetAccountId: String,
        candidates: List<ImportedBillCandidate>,
        onResult: (List<ImportedBillCandidate>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.checkDuplicates(targetAccountId, candidates)
            onResult(result)
        }
    }

    fun importBillCandidates(
        targetAccountId: String,
        candidates: List<ImportedBillCandidate>,
        onSuccess: (Int) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.importBillCandidates(targetAccountId, candidates)
            if (result.isSuccess) {
                onSuccess(result.getOrThrow())
            } else {
                onError(result.exceptionOrNull()?.message ?: "导入失败")
            }
        }
    }

    fun adjustAccountBalance(
        targetAccountId: String,
        targetBalance: Double,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.adjustAccountBalance(targetAccountId, targetBalance)
            if (result.isSuccess) {
                onSuccess()
            } else {
                onError(result.exceptionOrNull()?.message ?: "校准余额失败")
            }
        }
    }
}

class MainViewModelFactory(private val repository: LedgerRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
