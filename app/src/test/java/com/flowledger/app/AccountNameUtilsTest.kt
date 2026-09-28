package com.flowledger.app

import com.flowledger.app.utils.AccountNameUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountNameUtilsTest {

    @Test
    fun testGenerateUniqueAccountName_NoCollision() {
        val existing = listOf("招商银行储蓄卡", "微信零钱", "支付宝余额")
        val result = AccountNameUtils.generateUniqueAccountName("私房钱", existing)
        assertEquals("私房钱", result)
    }

    @Test
    fun testGenerateUniqueAccountName_FirstDuplicateAddsOne() {
        val existing = listOf("招商银行储蓄卡", "私房钱")
        val result = AccountNameUtils.generateUniqueAccountName("私房钱", existing)
        assertEquals("私房钱 1", result)
    }

    @Test
    fun testGenerateUniqueAccountName_SecondDuplicateAddsTwo() {
        val existing = listOf("私房钱", "私房钱 1")
        val result = AccountNameUtils.generateUniqueAccountName("私房钱", existing)
        assertEquals("私房钱 2", result)
    }

    @Test
    fun testGenerateUniqueAccountName_FillsHolesInSequence() {
        val existing = listOf("私房钱", "私房钱 2")
        val result = AccountNameUtils.generateUniqueAccountName("私房钱", existing)
        assertEquals("私房钱 1", result)
    }

    @Test
    fun testGenerateUniqueAccountName_ExplicitNumberCollision() {
        val existing = listOf("私房钱", "私房钱 1")
        val result = AccountNameUtils.generateUniqueAccountName("私房钱 1", existing)
        assertEquals("私房钱 2", result)
    }

    @Test
    fun testGenerateUniqueAccountName_DifferentBaseNamesDoNotInterfere() {
        val existing = listOf("私房钱", "私房钱 1", "花呗", "花呗 1")
        val result1 = AccountNameUtils.generateUniqueAccountName("私房钱", existing)
        val result2 = AccountNameUtils.generateUniqueAccountName("花呗", existing)
        val result3 = AccountNameUtils.generateUniqueAccountName("招商银行储蓄卡", existing)

        assertEquals("私房钱 2", result1)
        assertEquals("花呗 2", result2)
        assertEquals("招商银行储蓄卡", result3)
    }

    @Test
    fun testGenerateUniqueAccountName_HandlesWhitespaceProperly() {
        val existing = listOf("私房钱")
        val result = AccountNameUtils.generateUniqueAccountName("  私房钱  ", existing)
        assertEquals("私房钱 1", result)
    }
}
