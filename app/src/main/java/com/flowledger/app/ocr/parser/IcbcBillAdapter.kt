package com.flowledger.app.ocr.parser

import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.BillParseResult
import com.flowledger.app.data.model.ImportedBillCandidate
import com.flowledger.app.data.model.TransactionType
import com.flowledger.app.ocr.CategoryInferenceEngine
import com.flowledger.app.ocr.plugin.OcrTextLine
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import kotlin.math.abs

/**
 * 中国工商银行储蓄卡明细截图专用适配器
 * 支持三列空间排版、月度通栏状态机、精确时间合成与连续性运行余额对账。
 */
class IcbcBillAdapter : BillAdapter {

    override val adapterName: String = "中国工商银行 (工银借记卡)"

    override fun canHandle(lines: List<OcrTextLine>): Boolean {
        val fullText = lines.joinToString(" ") { it.text }
        return fullText.contains("工银借记卡") ||
                fullText.contains("借记卡(0类)") ||
                fullText.contains("中国工商银行") ||
                (fullText.contains("人民币余额") && fullText.contains("当月汇总"))
    }

    override fun parse(
        lines: List<OcrTextLine>,
        availableAccounts: List<AccountEntity>
    ): BillParseResult {
        // 按全局空间坐标从上到下、从左到右排序
        val sortedLines = lines.sortedWith(
            compareBy<OcrTextLine> { it.globalBounds.top }
                .thenBy { it.globalBounds.left }
        )

        var detectedHeaderBalance: Double? = null
        val candidates = mutableListOf<ImportedBillCandidate>()

        // 状态机变量
        var currentYear = Calendar.getInstance().get(Calendar.YEAR)
        var currentMonth = Calendar.getInstance().get(Calendar.MONTH) + 1
        var currentDay = 1
        var currentBlockIndex = 0
        val candidateBlockIndices = mutableListOf<Int>()

        val monthPattern = Pattern.compile("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月")
        val dayPattern = Pattern.compile("^(\\d{1,2})(?:\\s*周[一二三四五六日])?$")
        val timePattern = Pattern.compile("工银借记卡\\d*\\s*(\\d{2}:\\d{2}(?::\\d{2})?)")
        val amountPattern = Pattern.compile("([+-])\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
        val balancePattern = Pattern.compile("余额\\s*[:：]?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")
        val headerBalancePattern = Pattern.compile("人民币余额\\s*[:：]?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)")

        // 临时条目聚合器
        var tempDay: Int? = null
        var tempTimeStr: String? = null
        var tempTitle: String? = null
        var tempChannel: String? = null
        var tempAmount: Double? = null
        var tempType: TransactionType = TransactionType.EXPENSE
        var tempBalance: Double? = null
        var tempRawLines = mutableListOf<String>()

        fun commitCurrentItem() {
            if (tempAmount != null && tempAmount!! > 0) {
                val day = tempDay ?: currentDay
                val timeStr = tempTimeStr ?: "12:00:00"
                val occurredAt = buildTimestamp(currentYear, currentMonth, day, timeStr)

                val displayTitle = when {
                    !tempChannel.isNullOrBlank() && !tempTitle.isNullOrBlank() ->
                        "${tempTitle} - ${tempChannel}"
                    !tempTitle.isNullOrBlank() -> tempTitle!!
                    !tempChannel.isNullOrBlank() -> tempChannel!!
                    else -> if (tempType == TransactionType.INCOME) "收入入账" else "日常支出"
                }

                val fullRaw = tempRawLines.joinToString(" | ")
                val isSuspectedTransfer = CategoryInferenceEngine.isSuspectedTransfer(displayTitle) ||
                        CategoryInferenceEngine.isSuspectedTransfer(fullRaw)

                val (matchedCatId, matchedCatName) = CategoryInferenceEngine.matchExistingCategoryAccount(
                    availableAccounts,
                    displayTitle,
                    tempType
                )

                candidates.add(
                    ImportedBillCandidate(
                        title = displayTitle,
                        originalRawText = fullRaw,
                        occurredAt = occurredAt,
                        amount = tempAmount!!,
                        type = tempType,
                        categoryAccountId = matchedCatId,
                        categoryAccountName = matchedCatName,
                        runningBalance = tempBalance,
                        isSuspectedTransfer = isSuspectedTransfer,
                        isSelected = true
                    )
                )
                candidateBlockIndices.add(currentBlockIndex)
            }

            // 重置单笔缓存
            tempDay = null
            tempTimeStr = null
            tempTitle = null
            tempChannel = null
            tempAmount = null
            tempType = TransactionType.EXPENSE
            tempBalance = null
            tempRawLines.clear()
        }

        for (line in sortedLines) {
            val text = line.text.trim()
            if (text.isBlank()) continue

            // 1. 顶部总余额提取
            val headerMatcher = headerBalancePattern.matcher(text)
            if (headerMatcher.find()) {
                val bStr = headerMatcher.group(1)?.replace(",", "")
                detectedHeaderBalance = bStr?.toDoubleOrNull()
            }

            // 2. 月度通栏匹配 (如 2026年09月)
            val monthMatcher = monthPattern.matcher(text)
            if (monthMatcher.find()) {
                commitCurrentItem()
                currentBlockIndex++
                val y = monthMatcher.group(1)?.toIntOrNull()
                val m = monthMatcher.group(2)?.toIntOrNull()
                if (y != null) currentYear = y
                if (m != null) currentMonth = m
                continue
            }

            // 3. 运行余额行 (每笔明细的结束标记)
            val balanceMatcher = balancePattern.matcher(text)
            if (balanceMatcher.find()) {
                tempRawLines.add(text)
                val bStr = balanceMatcher.group(1)?.replace(",", "")
                tempBalance = bStr?.toDoubleOrNull()
                // 工行一笔明细以余额作为末尾行，提交此笔
                commitCurrentItem()
                continue
            }

            // 4. 左列日期匹配 (如 21 或 21 周一)
            val dayMatcher = dayPattern.matcher(text)
            if (dayMatcher.matches()) {
                val d = dayMatcher.group(1)?.toIntOrNull()
                if (d != null && d in 1..31) {
                    currentDay = d
                    tempDay = d
                    tempRawLines.add(text)
                    continue
                }
            }

            // 5. 金额行 (如 +0.26 或 -3,729.50)
            val amountMatcher = amountPattern.matcher(text)
            if (amountMatcher.matches()) {
                tempRawLines.add(text)
                val sign = amountMatcher.group(1)
                val numStr = amountMatcher.group(2)?.replace(",", "")
                val amt = numStr?.toDoubleOrNull()
                if (amt != null) {
                    tempAmount = amt
                    tempType = if (sign == "+") TransactionType.INCOME else TransactionType.EXPENSE
                }
                continue
            }

            // 6. 卡号与时间戳行 (如 工银借记卡4175 00:40:22)
            val timeMatcher = timePattern.matcher(text)
            if (timeMatcher.find()) {
                tempRawLines.add(text)
                tempTimeStr = timeMatcher.group(1)
                continue
            }

            // 7. 业务类型与对手方商户
            if (text.length <= 8 && isKnownIcbcType(text)) {
                tempRawLines.add(text)
                tempTitle = text
            } else if (!text.contains("当月汇总") && !text.contains("人民币余额") && !text.contains("查询明细")) {
                tempRawLines.add(text)
                if (tempChannel == null) {
                    tempChannel = text
                } else {
                    tempChannel += " $text"
                }
            }
        }

        // 提交最后一笔
        commitCurrentItem()

        // 8. 运行余额连续性对账计算
        var reconciledPairs = 0
        var mismatchCount = 0

        for (i in 0 until candidates.size - 1) {
            val curr = candidates[i]
            val prev = candidates[i + 1] // 截图从上至下为倒序，prev 是更早的一笔
            val sameBlock = (candidateBlockIndices[i] == candidateBlockIndices[i + 1])

            if (curr.runningBalance != null && prev.runningBalance != null) {
                val delta = if (curr.type == TransactionType.INCOME) curr.amount else -curr.amount
                val expectedCurrentBalance = prev.runningBalance!! + delta
                val diff = abs(curr.runningBalance!! - expectedCurrentBalance)
                if (diff < 0.05) {
                    reconciledPairs++
                } else if (sameBlock) {
                    mismatchCount++
                }
            }
        }

        val isReconciled = reconciledPairs > 0 && mismatchCount == 0
        val detailStr = when {
            isReconciled -> "✓ 连续性余额对账 $reconciledPairs 笔完全平账"
            mismatchCount > 0 -> "部分流水可能存在跨月截断或未对齐 ($reconciledPairs 笔平账, $mismatchCount 笔偏差)"
            else -> "已提取 ${candidates.size} 笔明细"
        }

        return BillParseResult(
            sourceName = adapterName,
            detectedAccountBalance = detectedHeaderBalance ?: candidates.firstOrNull()?.runningBalance,
            items = candidates,
            isReconciled = isReconciled,
            reconciliationDetail = detailStr
        )
    }

    private fun isKnownIcbcType(text: String): Boolean {
        return text in listOf(
            "消费", "利息", "跨行汇款", "数字人民币兑回", "一卡通销户退款", "学费",
            "奖学金", "转账", "他行汇入", "退款", "金融付款", "理财", "代发工资"
        )
    }

    private fun buildTimestamp(year: Int, month: Int, day: Int, timeStr: String): Long {
        return try {
            val parts = timeStr.split(":")
            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 12
            val min = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val sec = parts.getOrNull(2)?.toIntOrNull() ?: 0

            val cal = Calendar.getInstance()
            cal.set(Calendar.YEAR, year)
            cal.set(Calendar.MONTH, month - 1)
            cal.set(Calendar.DAY_OF_MONTH, day)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, min)
            cal.set(Calendar.SECOND, sec)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
