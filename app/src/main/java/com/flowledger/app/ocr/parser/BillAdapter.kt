package com.flowledger.app.ocr.parser

import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.BillParseResult
import com.flowledger.app.ocr.plugin.OcrTextLine

/**
 * 账单渠道解析适配器契约接口
 */
interface BillAdapter {
    val adapterName: String

    /**
     * 判断当前适配器是否能够处理此批 OCR 文本特征
     */
    fun canHandle(lines: List<OcrTextLine>): Boolean

    /**
     * 执行具体解析、时间与金额提取、并进行余额对账核验
     */
    fun parse(
        lines: List<OcrTextLine>,
        availableAccounts: List<AccountEntity>
    ): BillParseResult
}
