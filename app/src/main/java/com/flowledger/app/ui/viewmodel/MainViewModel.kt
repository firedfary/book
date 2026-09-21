package com.flowledger.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.repository.LedgerRepository
import kotlinx.coroutines.launch

class MainViewModel(private val repository: LedgerRepository) : ViewModel() {

    val allAccountsWithBalances = repository.allAccountsWithBalances.asLiveData()
    val assetAccounts = repository.assetAccounts.asLiveData()
    val liabilityAccounts = repository.liabilityAccounts.asLiveData()
    val expenseAccounts = repository.expenseAccounts.asLiveData()
    val incomeAccounts = repository.incomeAccounts.asLiveData()
    val financialSummary = repository.financialSummary.asLiveData()
    val transactions = repository.transactionsFlow.asLiveData()

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

    fun addAccount(account: AccountEntity, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                repository.addAccount(account)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "添加账户失败")
            }
        }
    }

    fun deleteTransaction(txId: String) {
        viewModelScope.launch {
            repository.deleteTransaction(txId)
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch {
            repository.deleteAccount(account)
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
