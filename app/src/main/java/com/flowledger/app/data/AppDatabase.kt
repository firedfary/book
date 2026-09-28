package com.flowledger.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.flowledger.app.data.dao.AccountDao
import com.flowledger.app.data.dao.BookDao
import com.flowledger.app.data.dao.PostingDao
import com.flowledger.app.data.dao.TransactionDao
import com.flowledger.app.data.model.AccountCategory
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.data.model.BookEntity
import com.flowledger.app.data.model.PostingEntity
import com.flowledger.app.data.model.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        BookEntity::class,
        AccountEntity::class,
        TransactionEntity::class,
        PostingEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun bookDao(): BookDao
    abstract fun accountDao(): AccountDao
    abstract fun transactionDao(): TransactionDao
    abstract fun postingDao(): PostingDao

    companion object {
        const val DEFAULT_BOOK_ID = "default_book_id"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. 创建 books 账本表
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `books` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `currency` TEXT NOT NULL,
                        `colorHex` TEXT NOT NULL,
                        `iconName` TEXT NOT NULL,
                        `isDefault` INTEGER NOT NULL,
                        `isDeleted` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `syncVersion` INTEGER NOT NULL,
                        `syncId` TEXT,
                        `syncStatus` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                // 2. 插入初始默认账本
                val now = System.currentTimeMillis()
                db.execSQL("""
                    INSERT OR IGNORE INTO `books` 
                    (`id`, `name`, `description`, `currency`, `colorHex`, `iconName`, `isDefault`, `isDeleted`, `createdAt`, `updatedAt`, `syncVersion`, `syncId`, `syncStatus`)
                    VALUES ('$DEFAULT_BOOK_ID', '默认账本', '日常主账本', 'CNY', '#1976D2', 'ic_book', 1, 0, $now, $now, 1, NULL, 0)
                """.trimIndent())

                // 3. 为 accounts 增加 bookId 与云同步字段
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `bookId` TEXT NOT NULL DEFAULT '$DEFAULT_BOOK_ID'")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `syncId` TEXT")
                db.execSQL("ALTER TABLE `accounts` ADD COLUMN `syncStatus` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_accounts_bookId` ON `accounts` (`bookId`)")

                // 4. 为 transactions 增加 bookId 与云同步字段
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `bookId` TEXT NOT NULL DEFAULT '$DEFAULT_BOOK_ID'")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `syncId` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `syncStatus` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_bookId` ON `transactions` (`bookId`)")
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "flow_ledger.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
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
                        populateInitialData(database)
                    }
                }
            }

            private suspend fun populateInitialData(db: AppDatabase) {
                // 1. 初始化默认账本
                val defaultBook = BookEntity(
                    id = DEFAULT_BOOK_ID,
                    name = "默认账本",
                    description = "日常主账本",
                    currency = "CNY",
                    colorHex = "#1976D2",
                    isDefault = true
                )
                db.bookDao().insertBook(defaultBook)

                // 2. 初始化默认账本下的标准账户与收支节点
                val initialAccounts = listOf(
                    // 资产账户
                    AccountEntity(
                        bookId = DEFAULT_BOOK_ID,
                        name = "招商银行储蓄卡",
                        type = AccountType.ASSET,
                        category = AccountCategory.BANK_CARD,
                        initialBalance = 12500.00,
                        colorHex = "#C62828",
                        iconName = "ic_bank"
                    ),
                    AccountEntity(
                        bookId = DEFAULT_BOOK_ID,
                        name = "微信零钱",
                        type = AccountType.ASSET,
                        category = AccountCategory.WECHAT,
                        initialBalance = 1580.50,
                        colorHex = "#2E7D32",
                        iconName = "ic_wechat"
                    ),
                    AccountEntity(
                        bookId = DEFAULT_BOOK_ID,
                        name = "支付宝余额",
                        type = AccountType.ASSET,
                        category = AccountCategory.ALIPAY,
                        initialBalance = 3200.00,
                        colorHex = "#1565C0",
                        iconName = "ic_alipay"
                    ),
                    AccountEntity(
                        bookId = DEFAULT_BOOK_ID,
                        name = "现金钱包",
                        type = AccountType.ASSET,
                        category = AccountCategory.CASH,
                        initialBalance = 450.00,
                        colorHex = "#F57F17",
                        iconName = "ic_cash"
                    ),

                    // 负债账户
                    AccountEntity(
                        bookId = DEFAULT_BOOK_ID,
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
                        bookId = DEFAULT_BOOK_ID,
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

                    // 支出分类节点
                    AccountEntity(
                        bookId = DEFAULT_BOOK_ID,
                        name = "餐饮美食",
                        type = AccountType.EXPENSE,
                        category = AccountCategory.DINING,
                        colorHex = "#FF7043",
                        iconName = "ic_dining"
                    ),
                    AccountEntity(
                        bookId = DEFAULT_BOOK_ID,
                        name = "交通出行",
                        type = AccountType.EXPENSE,
                        category = AccountCategory.TRANSPORT,
                        colorHex = "#26A69A",
                        iconName = "ic_transport"
                    ),
                    AccountEntity(
                        bookId = DEFAULT_BOOK_ID,
                        name = "日常购物",
                        type = AccountType.EXPENSE,
                        category = AccountCategory.SHOPPING,
                        colorHex = "#AB47BC",
                        iconName = "ic_shopping"
                    ),
                    AccountEntity(
                        bookId = DEFAULT_BOOK_ID,
                        name = "住房物业",
                        type = AccountType.EXPENSE,
                        category = AccountCategory.HOUSING,
                        colorHex = "#78909C",
                        iconName = "ic_housing"
                    ),
                    AccountEntity(
                        bookId = DEFAULT_BOOK_ID,
                        name = "提现与转账手续费",
                        type = AccountType.EXPENSE,
                        category = AccountCategory.FEE,
                        colorHex = "#8D6E63",
                        iconName = "ic_fee"
                    ),
                    AccountEntity(
                        bookId = DEFAULT_BOOK_ID,
                        name = "休闲娱乐",
                        type = AccountType.EXPENSE,
                        category = AccountCategory.ENTERTAINMENT,
                        colorHex = "#FFA726",
                        iconName = "ic_entertainment"
                    ),

                    // 收入分类节点
                    AccountEntity(
                        bookId = DEFAULT_BOOK_ID,
                        name = "工资薪金",
                        type = AccountType.INCOME,
                        category = AccountCategory.SALARY,
                        colorHex = "#43A047",
                        iconName = "ic_salary"
                    ),
                    AccountEntity(
                        bookId = DEFAULT_BOOK_ID,
                        name = "理财收益",
                        type = AccountType.INCOME,
                        category = AccountCategory.FINANCE_INCOME,
                        colorHex = "#3949AB",
                        iconName = "ic_finance"
                    )
                )
                db.accountDao().insertAccounts(initialAccounts)
            }
        }
    }
}
