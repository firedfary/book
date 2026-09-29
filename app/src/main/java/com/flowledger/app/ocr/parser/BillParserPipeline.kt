package com.flowledger.app.ocr.parser

import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.BillParseResult
import com.flowledger.app.ocr.plugin.OcrTextLine

/**
 * 账单渠道适配器调度管道
 * 自动识别输入文本特征，匹配最适适配器（如工行专用、微信专用或通用降级）。
 */
class BillParserPipeline(
    private val adapters: List<BillAdapter> = listOf(
        IcbcBillAdapter(),
        GenericBillAdapter()
    )
) {

    fun parse(
        lines: List<OcrTextLine>,
        availableAccounts: List<AccountEntity>
    ): BillParseResult {
        if (lines.isEmpty()) {
            return BillParseResult(
                sourceName = "空数据",
                items = emptyList(),
                reconciliationDetail = "未在图片中识别到有效文字"
            )
        }

        // 匹配首个能处理的适配器
        val matchedAdapter = adapters.firstOrNull { it.canHandle(lines) }
            ?: adapters.last() // 兜底适配器

        return matchedAdapter.parse(lines, availableAccounts)
    }
}
