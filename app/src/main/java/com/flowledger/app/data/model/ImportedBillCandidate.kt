package com.flowledger.app.data.model

import java.util.UUID

/**
 * 截图识别出的单笔待导入流水候选项
 */
data class ImportedBillCandidate(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var originalRawText: String = "",
    var occurredAt: Long,
    var amount: Double,                             // 正数金额
    var type: TransactionType = TransactionType.EXPENSE, // EXPENSE / INCOME / TRANSFER
    var categoryAccountId: String? = null,          // 对应的收支分类账户 ID
    var categoryAccountName: String = "",           // 分类名称 (如 "日常购物", "理财收益")
    var runningBalance: Double? = null,             // 账单明细中的运行余额 (用于连续性对账)
    var isSuspectedTransfer: Boolean = false,       // 是否疑似转账/还款（温和提醒用户）
    var transferToAccountId: String? = null,        // 若转为转账，对端账户 ID
    var transferToAccountName: String? = null,      // 若转为转账，对端账户名称
    var isDuplicate: Boolean = false,               // 是否与数据库已有交易重复
    var isSelected: Boolean = true                  // 是否勾选导入
)

/**
 * 账单截图解析的整体汇总结果
 */
data class BillParseResult(
    val sourceName: String,                         // 适配器识别出的账单渠道来源名称
    val detectedAccountBalance: Double? = null,     // 截图顶部/末尾识别出的账户最新实时余额
    val items: List<ImportedBillCandidate>,         // 解析出的流水列表
    val isReconciled: Boolean = false,              // 余额连续性对账是否 100% 通过
    val reconciliationDetail: String = ""           // 对账结果说明 (如 "42 笔流水连续性对账通过")
)
