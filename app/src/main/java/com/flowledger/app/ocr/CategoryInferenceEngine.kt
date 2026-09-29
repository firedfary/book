package com.flowledger.app.ocr

import com.flowledger.app.data.model.AccountCategory
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.data.model.TransactionType

/**
 * 离线商户与交易关键词智能分类推断引擎
 */
object CategoryInferenceEngine {

    data class InferenceResult(
        val category: AccountCategory,
        val defaultAccountName: String,
        val isSuspectedTransfer: Boolean = false
    )

    private val DINING_KEYWORDS = listOf(
        "美团", "饿了么", "餐饮", "饭店", "麦当劳", "肯德基", "星巴克", "瑞幸",
        "咖啡", "面馆", "烤肉", "火锅", "烤馕", "蛋挞", "烘焙", "奶茶",
        "食堂", "烧烤", "小吃", "零食", "买菜", "生鲜", "知快软件", "快餐"
    )

    private val TRANSPORT_KEYWORDS = listOf(
        "地铁", "公交", "打车", "滴滴", "高铁", "12306", "铁路", "航空",
        "机票", "加油", "停车", "过路费", "顺风车", "曹操", "出行", "单车"
    )

    private val SHOPPING_KEYWORDS = listOf(
        "拼多多", "京东", "淘宝", "天猫", "商城", "超市", "便利店", "百货",
        "零售", "文具", "花名堂", "专卖店", "优衣库", "服装", "数码", "商户", "购物"
    )

    private val HOUSING_KEYWORDS = listOf(
        "房租", "物业", "水电", "燃气", "电费", "水费", "宽带", "电信", "移动", "联通"
    )

    private val ENTERTAINMENT_KEYWORDS = listOf(
        "电影", "影院", "游戏", "腾讯", "网易", "Steam", "充值", "会员", "KTV", "游乐"
    )

    private val FEE_KEYWORDS = listOf(
        "手续费", "服务费", "提现手续费"
    )

    private val SALARY_KEYWORDS = listOf(
        "工资", "薪资", "薪金", "报酬", "劳务"
    )

    private val FINANCE_KEYWORDS = listOf(
        "利息", "理财", "分红", "收益", "基金", "批量业务"
    )

    private val TRANSFER_KEYWORDS = listOf(
        "跨行汇款", "转账", "他行汇入", "汇出", "数字人民币兑回", "兑回", "提现",
        "支付宝-提现", "销户退款", "还款"
    )

    /**
     * 判断某条交易是否疑似转账/还款
     */
    fun isSuspectedTransfer(text: String): Boolean {
        return TRANSFER_KEYWORDS.any { text.contains(it, ignoreCase = true) }
    }

    /**
     * 根据交易标题和文本推断分类
     */
    fun inferCategory(text: String, txType: TransactionType): InferenceResult {
        val isTransfer = isSuspectedTransfer(text)

        if (txType == TransactionType.INCOME) {
            if (FINANCE_KEYWORDS.any { text.contains(it, ignoreCase = true) }) {
                return InferenceResult(AccountCategory.FINANCE_INCOME, "理财收益", isTransfer)
            }
            if (SALARY_KEYWORDS.any { text.contains(it, ignoreCase = true) }) {
                return InferenceResult(AccountCategory.SALARY, "工资薪金", isTransfer)
            }
            if (text.contains("奖学金") || text.contains("奖励") || text.contains("补贴")) {
                return InferenceResult(AccountCategory.BONUS, "理财收益", isTransfer)
            }
            return InferenceResult(AccountCategory.OTHER, "理财收益", isTransfer)
        }

        // 支出推断
        if (DINING_KEYWORDS.any { text.contains(it, ignoreCase = true) }) {
            return InferenceResult(AccountCategory.DINING, "餐饮美食", isTransfer)
        }
        if (TRANSPORT_KEYWORDS.any { text.contains(it, ignoreCase = true) }) {
            return InferenceResult(AccountCategory.TRANSPORT, "交通出行", isTransfer)
        }
        if (SHOPPING_KEYWORDS.any { text.contains(it, ignoreCase = true) }) {
            return InferenceResult(AccountCategory.SHOPPING, "日常购物", isTransfer)
        }
        if (HOUSING_KEYWORDS.any { text.contains(it, ignoreCase = true) }) {
            return InferenceResult(AccountCategory.HOUSING, "住房物业", isTransfer)
        }
        if (ENTERTAINMENT_KEYWORDS.any { text.contains(it, ignoreCase = true) }) {
            return InferenceResult(AccountCategory.ENTERTAINMENT, "休闲娱乐", isTransfer)
        }
        if (FEE_KEYWORDS.any { text.contains(it, ignoreCase = true) }) {
            return InferenceResult(AccountCategory.FEE, "提现与转账手续费", isTransfer)
        }

        // 默认按日常购物兜底
        return InferenceResult(AccountCategory.SHOPPING, "日常购物", isTransfer)
    }

    /**
     * 将推断分类绑定到当前账本现存的具体 AccountEntity 上
     */
    fun matchExistingCategoryAccount(
        accounts: List<AccountEntity>,
        text: String,
        txType: TransactionType
    ): Pair<String?, String> {
        val inferred = inferCategory(text, txType)
        val targetType = if (txType == TransactionType.INCOME) AccountType.INCOME else AccountType.EXPENSE

        // 1. 尝试按 Category 精准匹配已有分类账户
        val matchedByCategory = accounts.firstOrNull {
            it.type == targetType && it.category == inferred.category && !it.isDeleted
        }
        if (matchedByCategory != null) {
            return Pair(matchedByCategory.id, matchedByCategory.name)
        }

        // 2. 尝试按默认名称匹配
        val matchedByName = accounts.firstOrNull {
            it.type == targetType && it.name.contains(inferred.defaultAccountName) && !it.isDeleted
        }
        if (matchedByName != null) {
            return Pair(matchedByName.id, matchedByName.name)
        }

        // 3. 兜底取目标类型的第一个账户
        val fallback = accounts.firstOrNull { it.type == targetType && !it.isDeleted }
        return Pair(fallback?.id, fallback?.name ?: inferred.defaultAccountName)
    }
}
