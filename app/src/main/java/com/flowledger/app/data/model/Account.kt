package com.flowledger.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class AccountType {
    ASSET,      // 资产 (存款、现金、理财)
    LIABILITY,  // 负债 (信用卡、花呗、贷款)
    EXPENSE,    // 支出分类节点 (餐饮、交通、购物、手续费)
    INCOME,     // 收入分类节点 (工资、奖金、理财收益)
    EQUITY      // 净资产/期初权益
}

enum class AccountCategory {
    // 资产子分类
    BANK_CARD,
    WECHAT,
    ALIPAY,
    CASH,
    INVESTMENT,

    // 负债子分类
    CREDIT_CARD,
    HUABEI,
    BAITIAO,
    LOAN,

    // 支出子分类
    DINING,
    SHOPPING,
    TRANSPORT,
    HOUSING,
    ENTERTAINMENT,
    MEDICAL,
    FEE,         // 手续费专有节点

    // 收入子分类
    SALARY,
    BONUS,
    FINANCE_INCOME,

    OTHER
}

@Entity(
    tableName = "accounts",
    indices = [Index("bookId")]
)
data class AccountEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val bookId: String = "default_book_id",
    val name: String,
    val type: AccountType,
    val category: AccountCategory,
    val currency: String = "CNY",
    val creditLimit: Double = 0.0,      // 信用额度 (信用卡/花呗适用)
    val billingDay: Int? = null,        // 账单日 (1-31)
    val repaymentDay: Int? = null,      // 还款日 (1-31)
    val initialBalance: Double = 0.0,   // 期初余额
    val colorHex: String = "#1976D2",   // 颜色标记
    val iconName: String = "ic_account",
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,     // 软删除标记
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncVersion: Long = 1L,
    val syncId: String? = null,
    val syncStatus: Int = 0
)

data class AccountWithBalance(
    val account: AccountEntity,
    val currentBalance: Double
) {
    // 计算负债账户的当前欠款与剩余可用额度
    val debtAmount: Double
        get() = if (account.type == AccountType.LIABILITY) {
            -currentBalance.coerceAtMost(0.0)
        } else 0.0

    val availableCredit: Double
        get() = if (account.type == AccountType.LIABILITY) {
            (account.creditLimit - debtAmount).coerceAtLeast(0.0)
        } else 0.0
}
