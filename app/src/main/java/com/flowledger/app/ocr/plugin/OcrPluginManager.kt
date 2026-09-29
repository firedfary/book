package com.flowledger.app.ocr.plugin

import android.content.Context
import android.content.SharedPreferences

/**
 * 识图插件注册与生命周期管理器
 * 控制默认引擎选择、离线模型下载状态与云端配置。
 */
class OcrPluginManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("flow_ledger_ocr_prefs", Context.MODE_PRIVATE)

    val mlKitPlugin = MlKitOcrPlugin()
    val cloudVisionPlugin = CloudVisionPlugin(context)

    val availablePlugins: List<IOcrEnginePlugin> = listOf(
        mlKitPlugin,
        cloudVisionPlugin
    )

    var activePluginId: String
        get() = prefs.getString("active_plugin_id", mlKitPlugin.id) ?: mlKitPlugin.id
        set(value) = prefs.edit().putString("active_plugin_id", value).apply()

    fun getActivePlugin(): IOcrEnginePlugin {
        return availablePlugins.find { it.id == activePluginId } ?: mlKitPlugin
    }

    /**
     * 判断当前选中的识图引擎是否已具备运行条件
     */
    fun isPluginReady(): Boolean {
        val plugin = getActivePlugin()
        return plugin.isInstalled
    }
}
