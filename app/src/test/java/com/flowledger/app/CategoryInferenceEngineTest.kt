package com.flowledger.app

import com.flowledger.app.data.model.AccountCategory
import com.flowledger.app.data.model.AccountEntity
import com.flowledger.app.data.model.AccountType
import com.flowledger.app.data.model.TransactionType
import com.flowledger.app.ocr.CategoryInferenceEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryInferenceEngineTest {

    @Test
    fun testPinduoduoInferredAsShopping() {
        val result = CategoryInferenceEngine.inferCategory("财付通-拼多多平台商户", TransactionType.EXPENSE)
        assertEquals(AccountCategory.SHOPPING, result.category)
        assertEquals("日常购物", result.defaultAccountName)
    }

    @Test
    fun testMeituanInferredAsDining() {
        val result = CategoryInferenceEngine.inferCategory("美团支付-北京三快在线科技有限公司", TransactionType.EXPENSE)
        assertEquals(AccountCategory.DINING, result.category)
        assertEquals("餐饮美食", result.defaultAccountName)
    }

    @Test
    fun testBakeryInferredAsDining() {
        val result = CategoryInferenceEngine.inferCategory("美团支付-阿阔提江烤馕架(汇金店)", TransactionType.EXPENSE)
        assertEquals(AccountCategory.DINING, result.category)
    }

    @Test
    fun testInterestInferredAsFinanceIncome() {
        val result = CategoryInferenceEngine.inferCategory("利息 批量业务", TransactionType.INCOME)
        assertEquals(AccountCategory.FINANCE_INCOME, result.category)
        assertEquals("理财收益", result.defaultAccountName)
    }

    @Test
    fun testRemittanceDetectedAsSuspectedTransfer() {
        val isTransfer1 = CategoryInferenceEngine.isSuspectedTransfer("跨行汇款 - *帆")
        assertTrue("跨行汇款应被识别为疑似转账", isTransfer1)

        val isTransfer2 = CategoryInferenceEngine.isSuspectedTransfer("数字人民币兑回")
        assertTrue("数字人民币兑回应被识别为疑似转账", isTransfer2)
    }

    @Test
    fun testMatchExistingCategoryAccount() {
        val accounts = listOf(
            AccountEntity(
                id = "acc_dining",
                name = "餐饮美食",
                type = AccountType.EXPENSE,
                category = AccountCategory.DINING
            ),
            AccountEntity(
                id = "acc_shopping",
                name = "日常购物",
                type = AccountType.EXPENSE,
                category = AccountCategory.SHOPPING
            )
        )

        val (matchedId, matchedName) = CategoryInferenceEngine.matchExistingCategoryAccount(
            accounts,
            "京东商城平台商户",
            TransactionType.EXPENSE
        )
        assertEquals("acc_shopping", matchedId)
        assertEquals("日常购物", matchedName)
    }
}
