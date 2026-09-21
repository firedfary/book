package com.flowledger.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flowledger.app.data.dao.AccountDao
import com.flowledger.app.data.dao.PostingDao
import com.flowledger.app.data.dao.TransactionDao
import com.flowledger.app.data.model.AccountCategory
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.data.model.PostingEntity
import com.flowledger.app.data.model.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AccountEntity::class,
        TransactionEntity::class,
        PostingEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun postingDao(): PostingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flow_ledger.db"
                )
                    .addCallback(DatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialAccounts(database.accountDao())
                    }
                }
            }

            private suspend fun populateInitialAccounts(dao: AccountDao) {
                val initialAccounts = listOf(
                    // 1. 资产账户
                    AccountEntity(
                        name = "招商银行储蓄卡",
                        type = AccountType.ASSET,
                        category = AccountCategory.BANK_CARD,
                        initialBalance = 12500.00,
                        colorHex = "#C62828",
                        iconName = "ic_bank"
                    ),
                    AccountEntity(
                        name = "微信零钱",
                        type = AccountType.ASSET,
                        category = AccountCategory.WECHAT,
                        initialBalance = 1580.50,
                        colorHex = "#2E7D32",
                        iconName = "ic_wechat"
                    ),
                    AccountEntity(
                        name = "支付宝余额",
                        type = AccountType.ASSET,
                        category = AccountCategory.ALIPAY,
                        initialBalance = 3200.00,
                        colorHex = "#1565C0",
                        iconName = "ic_alipay"
                    ),
                    AccountEntity(
                        name = "现金钱包",
                        type = AccountType.ASSET,
                        category = AccountCategory.CASH,
                        initialBalance = 450.00,
                        colorHex = "#F57F17",
                        iconName = "ic_cash"
                    ),

                    // 2. 负债账户
                    AccountEntity(
                        name = "招商银行信用卡",
                        type = AccountType.LIABILITY,
                        category = AccountCategory.CREDIT_CARD,
                        creditLimit = 30000.00,
                        billingDay = 5,
                        repaymentDay = 23,
                        initialBalance = 0.00,
                        colorHex = "#D32F2F",
                        iconName = "ic_credit_card"
                    ),
                    AccountEntity(
                        name = "蚂蚁花呗",
                        type = AccountType.LIABILITY,
                        category = AccountCategory.HUABEI,
                        creditLimit = 15000.00,
                        billingDay = 1,
                        repaymentDay = 10,
                        initialBalance = 0.00,
                        colorHex = "#0288D1",
                        iconName = "ic_huabei"
                    ),

                    // 3. 支出分类节点
                    AccountEntity(
                        name = "餐饮美食",
                        type = AccountType.EXPENSE,
                        category = AccountCategory.DINING,
                        colorHex = "#FF7043",
                        iconName = "ic_dining"
                    ),
                    AccountEntity(
                        name = "交通出行",
                        type = AccountType.EXPENSE,
                        category = AccountCategory.TRANSPORT,
                        colorHex = "#26A69A",
                        iconName = "ic_transport"
                    ),
                    AccountEntity(
                        name = "日常购物",
                        type = AccountType.EXPENSE,
                        category = AccountCategory.SHOPPING,
                        colorHex = "#AB47BC",
                        iconName = "ic_shopping"
                    ),
                    AccountEntity(
                        name = "住房物业",
                        type = AccountType.EXPENSE,
                        category = AccountCategory.HOUSING,
                        colorHex = "#78909C",
                        iconName = "ic_housing"
                    ),
                    AccountEntity(
                        name = "提现与转账手续费",
                        type = AccountType.EXPENSE,
                        category = AccountCategory.FEE,
                        colorHex = "#8D6E63",
                        iconName = "ic_fee"
                    ),
                    AccountEntity(
                        name = "休闲娱乐",
                        type = AccountType.EXPENSE,
                        category = AccountCategory.ENTERTAINMENT,
                        colorHex = "#FFA726",
                        iconName = "ic_entertainment"
                    ),

                    // 4. 收入分类节点
                    AccountEntity(
                        name = "工资薪金",
                        type = AccountType.INCOME,
                        category = AccountCategory.SALARY,
                        colorHex = "#43A047",
                        iconName = "ic_salary"
                    ),
                    AccountEntity(
                        name = "理财收益",
                        type = AccountType.INCOME,
                        category = AccountCategory.FINANCE_INCOME,
                        colorHex = "#3949AB",
                        iconName = "ic_finance"
                    )
                )
                dao.insertAccounts(initialAccounts)
            }
        }
    }
}
