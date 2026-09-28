package com.flowledger.app.utils

object AccountNameUtils {

    /**
     * 根据用户期望的账户名和当前账本中已有的账户名列表，
     * 智能生成不冲突的唯一账户名：
     * 1. 若当前不存在重名，直接返回 trimmedName；
     * 2. 若已存在同名账户，自动在末尾追加空格与递增数字（例如从 1 开始编号：私房钱 1，私房钱 2...）；
     * 3. 若用户输入的名称本身已带有数字后缀（如 "私房钱 1"），且库中冲突，则自动递增该编号；
     * 4. 优先填补序列空档或追加到最大编号之后。
     */
    fun generateUniqueAccountName(requestedName: String, existingNames: Collection<String>): String {
        val trimmed = requestedName.trim()
        if (trimmed.isEmpty()) return "新账户"

        val normalizedExisting = existingNames.map { it.trim() }.toSet()
        if (!normalizedExisting.contains(trimmed)) {
            return trimmed
        }

        // 解析输入的基准名称与可能自带的序号，例如 "私房钱 1" -> baseName="私房钱", suffixNum=1
        // 或 "私房钱" -> baseName="私房钱", suffixNum=null
        val suffixRegex = Regex("""^(.*?)(?:\s+(\d+))?$""")
        val match = suffixRegex.matchEntire(trimmed)
        val baseName = match?.groupValues?.get(1)?.trim()?.ifBlank { trimmed } ?: trimmed

        // 收集该 baseName 下已有占用的所有编号（其中 0 代表未带编号的原名）
        val pattern = Regex("""^${Regex.escape(baseName)}(?:\s+(\d+))?$""")
        val usedNumbers = mutableSetOf<Int>()

        for (name in normalizedExisting) {
            val m = pattern.matchEntire(name)
            if (m != null) {
                val numStr = m.groupValues[1]
                if (numStr.isEmpty()) {
                    usedNumbers.add(0) // 代表 baseName 本身已被占用
                } else {
                    numStr.toIntOrNull()?.let { usedNumbers.add(it) }
                }
            }
        }

        // 寻找第一个未使用的递增正整数编号 (1, 2, 3...)
        var candidate = 1
        while (usedNumbers.contains(candidate)) {
            candidate++
        }

        return "$baseName $candidate"
    }
}
