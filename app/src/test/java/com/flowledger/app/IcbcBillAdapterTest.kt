package com.flowledger.app

import android.graphics.Rect
import com.flowledger.app.data.model.AccountCategory
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.data.model.TransactionType
import com.flowledger.app.ocr.parser.IcbcBillAdapter
import com.flowledger.app.ocr.plugin.OcrTextLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IcbcBillAdapterTest {

    private var yCoord = 100

    private fun line(text: String, deltaY: Int = 15): OcrTextLine {
        yCoord += deltaY
        val rect = Rect(10, yCoord, 500, yCoord + 20)
        return OcrTextLine(text = text, localBounds = rect, globalBounds = rect)
    }

    @Test
    fun testIcbcCanHandleRecognition() {
        val adapter = IcbcBillAdapter()
        val lines = listOf(
            line("借记卡(0类) 尾号4175"),
            line("人民币余额:0.26"),
            line("工银借记卡4175 00:40:22")
        )
        assertTrue(adapter.canHandle(lines))
    }

    @Test
    fun testParseRealIcbcScreenshotFlow() {
        val adapter = IcbcBillAdapter()
        val testAccounts = listOf(
            AccountEntity(
                id = "acc_shopping",
                name = "日常购物",
                type = AccountType.EXPENSE,
                category = AccountCategory.SHOPPING
            ),
            AccountEntity(
                id = "acc_finance",
                name = "理财收益",
                type = AccountType.INCOME,
                category = AccountCategory.FINANCE_INCOME
            )
        )

        yCoord = 100
        val sampleLines = listOf(
            line("借记卡(0类) 尾号4175", 20),
            line("人民币余额:0.26", 20),
            line("当月汇总 支出: 144 / 收入: ¥80,822.32 / 支出: ¥80,822.06", 20),
            // 2026年09月
            line("2026年09月", 30),
            // Item 1: 利息 (+0.26, 余额:0.26)
            line("21", 20),
            line("周一", 10),
            line("利息", 10),
            line("批量业务", 10),
            line("工银借记卡4175 00:40:22", 10),
            line("+0.26", 10),
            line("余额:0.26", 10),
            // Item 2: 跨行汇款 (-3,729.50, 余额:0)
            line("18", 20),
            line("周五", 10),
            line("跨行汇款", 10),
            line("*帆", 10),
            line("工银借记卡4175 00:55:54", 10),
            line("-3,729.50", 10),
            line("余额:0", 10),
            // Item 3: 数字人民币兑回 (+3,003.69, 余额: 3,729.50)
            line("数字人民币兑回", 20),
            line("工银借记卡4175 00:52:47", 10),
            line("+3,003.69", 10),
            line("余额: 3,729.50", 10),
            // 2026年06月
            line("2026年06月", 30),
            // Item 4: 消费 (-33.40, 余额: 5,721.45)
            line("25", 20),
            line("周四", 10),
            line("消费", 10),
            line("财付通-拼多多平台商户", 10),
            line("工银借记卡4175 18:25:56", 10),
            line("-33.40", 10),
            line("余额: 5,721.45", 10),
            // Item 5: 消费 (-19.90, 余额: 5,754.85)
            line("消费", 20),
            line("财付通-拼多多平台商户", 10),
            line("工银借记卡4175 15:31:26", 10),
            line("-19.90", 10),
            line("余额: 5,754.85", 10)
        )

        val result = adapter.parse(sampleLines, testAccounts)

        // 验证基本汇总
        assertEquals(0.26, result.detectedAccountBalance ?: 0.0, 0.001)
        assertEquals(5, result.items.size)

        // 验证 Item 1 (利息)
        val item1 = result.items[0]
        assertTrue(item1.title.contains("利息"))
        assertEquals(0.26, item1.amount, 0.001)
        assertEquals(TransactionType.INCOME, item1.type)
        assertEquals(0.26, item1.runningBalance ?: 0.0, 0.001)

        // 验证 Item 2 (跨行汇款 - 疑似转账)
        val item2 = result.items[1]
        assertTrue(item2.title.contains("跨行汇款"))
        assertEquals(3729.50, item2.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, item2.type)
        assertTrue("跨行汇款应被打标为疑似转账", item2.isSuspectedTransfer)
        assertEquals(0.0, item2.runningBalance ?: -1.0, 0.001)

        // 验证 Item 3 (数字人民币兑回 - 疑似转账)
        val item3 = result.items[2]
        assertTrue(item3.title.contains("数字人民币兑回"))
        assertEquals(3003.69, item3.amount, 0.001)
        assertEquals(TransactionType.INCOME, item3.type)
        assertTrue("数字人民币兑回应被打标为疑似转账", item3.isSuspectedTransfer)
        assertEquals(3729.50, item3.runningBalance ?: 0.0, 0.001)

        // 验证 Item 4 (拼多多消费)
        val item4 = result.items[3]
        assertTrue(item4.title.contains("拼多多"))
        assertEquals(33.40, item4.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, item4.type)
        assertEquals("日常购物", item4.categoryAccountName)

        // 验证连续性对账计算
        assertTrue("运行余额连续性校验应通过", result.isReconciled)
        assertTrue(result.reconciliationDetail.contains("完全平账"))
    }
}
