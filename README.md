# FlowLedger 多账户资金流转记账系统 (Android 原生客户端)

[![Version](https://img.shields.io/badge/version-1.1.0-blue.svg)](file:///D:/book/PROGRESS.md)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-brightgreen.svg)](file:///D:/book/app/build.gradle.kts)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](file:///D:/book/README.md)
[![Architecture](https://img.shields.io/badge/architecture-MVVM%20%7C%20Double--Entry-orange.svg)](file:///D:/book/ARCHITECTURE.md)

FlowLedger 是一款专为解决现代人**多账户（微信、支付宝、银行卡、信用卡、花呗、白条等）资金流转混乱**而设计的个人财务应用。

传统单式记账软件将所有交易强行简化为“收入”或“支出”，在面对**信用卡还款、零钱提现手续费、账户间资金调拨**等场景时容易出现虚增收支或余额失真。FlowLedger 采用**“复式分录与节点流向图”**作为底层数学模型，确保资金流动清晰透明、借贷平衡。

> 📖 **架构与工程规范专区**：
> - 🏛️ **系统架构设计与技术全景规范**：详见 [ARCHITECTURE.md](file:///D:/book/ARCHITECTURE.md)
> - 📈 **研发进度看板与演进路线图**：详见 [PROGRESS.md](file:///D:/book/PROGRESS.md)

---

## 一、 核心功能特色

1. **五大账户节点体系与复式记账**：
   - 资产 (ASSET)、负债 (LIABILITY)、支出 (EXPENSE)、收入 (INCOME)、权益 (EQUITY)；
   - 严格满足资金零和守恒公理：$\sum_{i=1}^n \text{Posting}_i.\text{amount} = 0$。
2. **多账本管理与动态切换 (Multi-Ledger)**：
   - 支持多套账本独立核算（家庭账、个人账、商务账等）；
   - 顶部 Chip 一键切换账本，支持创建新账本、安全删除防呆（禁止删除当前或唯一账本）。
3. **自定义账户与重名自增序号 (Custom Accounts & Auto-Numbering)**：
   - 用户可自定义任意账户名称（如“私房钱”、“美团月付”等）；
   - 内置纯函数智能自增编号算法，重名时自动递增并填补空隙（“私房钱” $\rightarrow$ “私房钱 1” $\rightarrow$ “私房钱 2”）。
4. **全量 JSON 备份与复式校验导入 (Export & Import)**：
   - 支持 Android Storage Access Framework (SAF) 导出结构化备份、系统剪贴板与原生分享；
   - 导入支持自动 UUID 字典重排与分录资金平账强校验，确保外部数据安全导入。
5. **云同步架构协议预留 (Cloud Sync Ready)**：
   - 持久化层内嵌 `syncId`、`syncVersion`、`syncStatus` 与墓碑软删除（`isDeleted`）；
   - 契约接口 [`SyncContract.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/sync/SyncContract.kt) 为后续云端双向增量同步提供规范。
6. **全套 Material 3 深色模式适配**：
   - 语义化颜色令牌（Token）驱动，深色背景下文字与卡片层级对比鲜明，视觉舒适护眼。

---

## 二、 核心财务模型与流转逻辑

### 1. 资金守恒公理（复式分录）
每一笔交易（`Transaction`）由一组分录明细（`Postings`）构成，恒满足：
$$\sum_{i=1}^{n} \text{Posting}_i.\text{amount} = 0$$
* `amount < 0`：资金从该节点流出；
* `amount > 0`：资金流入该节点。

### 2. 典型流转业务场景映射
* **场景 1：微信零钱提现至招行卡（含手续费）**
  * 提现金额 1000 元，手续费 1 元：
    * `微信零钱`：`-1001.00`
    * `招行储蓄卡`：`+1000.00`
    * `提现与转账手续费`：`+1.00`
    * 总和：`-1001 + 1000 + 1 = 0`。准确体现了资产转移与财务损耗。
* **场景 2：银行卡向信用卡还款**
  * 工行卡还款招商信用卡 3000 元：
    * `工行储蓄卡`：`-3000.00`
    * `招商信用卡`：`+3000.00`
    * 总和：`-3000 + 3000 = 0`。资产减少 3000，负债减少 3000，净资产不变，当期收支表不受干扰。
* **场景 3：信用卡/花呗消费**
  * 花呗支付 200 元餐饮：
    * `蚂蚁花呗`：`-200.00`（欠款增加 200）
    * `餐饮美食`：`+200.00`
    * 总和：`-200 + 200 = 0`。

---

## 三、 系统包结构设计

```
com.flowledger.app
├── data/
│   ├── model/
│   │   ├── Book.kt             # 账本实体与包含统计信息的聚合模型
│   │   ├── Account.kt          # 账户实体、分类与带实时余额的包装类
│   │   ├── Transaction.kt      # 交易实体、展示项模型
│   │   └── Posting.kt          # 复式分录明细实体（外键级联）
│   ├── dao/
│   │   ├── BookDao.kt          # 账本增删改查与软删除管理
│   │   ├── AccountDao.kt       # 账户数据访问与动态聚合余额 SQL 查询
│   │   ├── TransactionDao.kt   # 交易事务管理与外键约束
│   │   └── PostingDao.kt       # 分录流水访问
│   └── AppDatabase.kt          # Room 数据库（v2）与 MIGRATION_1_2 迁移逻辑
├── sync/
│   └── SyncContract.kt         # 云同步状态机、SyncPacket 数据包与服务接口契约
├── utils/
│   ├── AccountNameUtils.kt     # 重名账户智能自增编号纯函数算法
│   └── LedgerExportImportHelper.kt # 账本 JSON 导出导入与资金守恒零和校验
├── repository/
│   └── LedgerRepository.kt     # 核心业务调度、激活账本状态流、借贷平衡与级联保护
├── ui/
│   ├── MainActivity.kt         # 主界面（底部导航与记账入口）
│   ├── dashboard/
│   │   └── DashboardFragment.kt# 资产看板（账本切换、净资产卡片、分类账户列表）
│   ├── transactions/
│   │   └── TransactionsFragment.kt # 流水列表（资金流向节点标注与分类标签）
│   ├── dialogs/
│   │   ├── AddTransactionBottomSheet.kt # 智能记账弹窗（支持支出、收入、转账/还款）
│   │   ├── AddAccountBottomSheet.kt     # 自定义账户添加弹窗
│   │   └── LedgerManagerBottomSheet.kt  # 多账本管理、导入与导出弹窗
│   ├── adapters/
│   │   ├── AccountCardAdapter.kt
│   │   ├── TransactionListAdapter.kt
│   │   └── BookCardAdapter.kt
│   └── viewmodel/
│       └── MainViewModel.kt
└── FlowLedgerApplication.kt    # 全局依赖单例上下文
```

---

## 四、 使用 VS Code 进行开发

本项目已内置完整的 VS Code 配置文件（位于 `.vscode/` 目录），无需安装庞大的 Android Studio，即可在轻量高效的 VS Code 中开发、编译与调试。

### 1. 推荐安装的 VS Code 插件
打开 VS Code，按 `Ctrl+P` 输入 `Extensions: Show Recommended Extensions`，或安装以下插件：
* **Kotlin** (`fwcd.kotlin` 或 `mathiasfrohlich.Kotlin`)：语法高亮、代码提示与补全
* **Gradle for Java** (`vscjava.vscode-gradle`)：Gradle 任务管理与可视化树
* **Android** (`adelphes.android-dev-ext`)：Android 开发工具集

### 2. 快捷键与任务
项目中已配置好快捷任务（按 `Ctrl+Shift+B` 或 `Ctrl+Shift+P` -> `Tasks: Run Task`）：
* **Build Debug APK**：编译生成 Debug 安装包
* **Run Unit Tests**：运行单元测试
* **Install APK via ADB**：直接安装 APK 到连接的安卓手机或模拟器
* **Clean Project**：清理构建缓存

---

## 五、 命令行编译与打包

在终端直接运行：
```powershell
powershell -ExecutionPolicy Bypass -File .\build_apk.ps1
```
或者使用 Gradle Wrapper：
```powershell
.\gradlew.bat assembleDebug
```
产物将输出至根目录：`D:\book\FlowLedger-v1.0.0.apk`。
