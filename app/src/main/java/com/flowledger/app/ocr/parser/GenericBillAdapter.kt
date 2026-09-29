package com.flowledger.app.ocr.parser

import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.BillParseResult
import com.flowledger.app.data.model.ImportedBillCandidate
import com.flowledger.app.data.model.TransactionType
import com.flowledger.app.ocr.CategoryInferenceEngine
import com.flowledger.app.ocr.plugin.OcrTextLine
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

/**
 * 通用启发式账单降级适配器
 * 当未能匹配到特定银行/支付软件时，以“金额正则”为空间锚点，在 Y 轴近邻空间聚类提取交易。
 */
class GenericBillAdapter : BillAdapter {

    override val adapterName: String = "通用账单识别"

    override fun canHandle(lines: List<OcrTextLine>): Boolean = true

    override fun parse(
        lines: List<OcrTextLine>,
        availableAccounts: List<AccountEntity>
    ): BillParseResult {
        val amountPattern = Pattern.compile("([+-]?)\\s*[¥￥]?\\s*([0-9,]+\\.[0-9]{2})")
        val datePattern = Pattern.compile("(\\d{4}[-/年]\\d{1,2}[-/月]\\d{1,2})")

        val sortedLines = lines.sortedBy { it.globalBounds.top }
        val candidates = mutableListOf<ImportedBillCandidate>()

        val amountLines = sortedLines.filter {
            amountPattern.matcher(it.text).find() && !it.text.contains("余额") && !it.text.contains("汇总")
        }

        for (amtLine in amountLines) {
            val matcher = amountPattern.matcher(amtLine.text)
            if (!matcher.find()) continue

            val sign = matcher.group(1)
            val numStr = matcher.group(2)?.replace(",", "")
            val amount = numStr?.toDoubleOrNull() ?: continue
            if (amount <= 0.0) continue

            val txType = if (sign == "+") TransactionType.INCOME else TransactionType.EXPENSE

            // 搜索 Y 轴上下 150px 内的近邻文本行
            val centerY = amtLine.globalBounds.centerY()
            val nearbyLines = sortedLines.filter {
                abs(it.globalBounds.centerY() - centerY) <= 180 && it != amtLine
            }

            // 提取日期
            var occurredAt = System.currentTimeMillis()
            for (near in nearbyLines) {
                val dMatch = datePattern.matcher(near.text)
                if (dMatch.find()) {
                    val dStr = dMatch.group(1)?.replace("年", "-")?.replace("月", "-")
                    try {
                        val parsedDate = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(dStr ?: "")
                        if (parsedDate != null) occurredAt = parsedDate.time
                    } catch (ignored: Exception) {}
                    break
                }
            }

            // 提取标题描述
            val titleCandidate = nearbyLines.firstOrNull {
                it.text.length in 2..25 &&
                        !it.text.contains("¥") &&
                        !datePattern.matcher(it.text).find()
            }?.text ?: if (txType == TransactionType.INCOME) "通用收入" else "通用支出"

            val isTransfer = CategoryInferenceEngine.isSuspectedTransfer(titleCandidate)
            val (catId, catName) = CategoryInferenceEngine.matchExistingCategoryAccount(
                availableAccounts,
                titleCandidate,
                txType
            )

            candidates.add(
                ImportedBillCandidate(
                    title = titleCandidate,
                    originalRawText = amtLine.text,
                    occurredAt = occurredAt,
                    amount = amount,
                    type = txType,
                    categoryAccountId = catId,
                    categoryAccountName = catName,
                    isSuspectedTransfer = isTransfer,
                    isSelected = true
                )
            )
        }

        return BillParseResult(
            sourceName = adapterName,
            detectedAccountBalance = null,
            items = candidates,
            isReconciled = false,
            reconciliationDetail = "按通用启发式规则提取出 ${candidates.size} 笔明细"
        )
    }
}
