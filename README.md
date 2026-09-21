# FlowLedger 多账户资金流转记账系统 (Android 原生客户端)

FlowLedger 是一款专为解决现代人**多账户（微信、支付宝、银行卡、信用卡、花呗、白条等）资金流转混乱**而设计的个人财务应用。

传统单式记账软件将所有交易强行简化为“收入”或“支出”，在面对**信用卡还款、零钱提现手续费、账户间资金调拨**等场景时容易出现虚增收支或余额失真。FlowLedger 采用**“复式分录与节点流向图”**作为底层数学模型，确保资金流动清晰透明、借贷平衡。

---

## 一、 核心财务模型与流转逻辑

### 1. 账户分类（五大节点体系）
* **资产（ASSET）**：招商银行卡、微信零钱、支付宝、现金钱包、理财等。
* **负债（LIABILITY）**：招商信用卡、蚂蚁花呗、白条、商业贷款等。
* **支出（EXPENSE）**：餐饮、交通、购物、住房、**提现与转账手续费**等。
* **收入（INCOME）**：工资薪金、理财收益、奖金等。
* **权益（EQUITY）**：期初资产、平账调节。

### 2. 资金守恒公理（复式分录）
每一笔交易（`Transaction`）由一组分录明细（`Postings`）构成，恒满足：
$$\sum_{i=1}^{n} \text{Posting}_i.\text{amount} = 0$$
* `amount < 0`：资金从该节点流出；
* `amount > 0`：资金流入该节点。

### 3. 典型流转业务场景映射
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

## 二、 系统架构设计

```
com.flowledger.app
├── data/
│   ├── model/
│   │   ├── Account.kt          # 账户实体、分类与带实时余额的包装类
│   │   ├── Transaction.kt      # 交易实体、展示项模型
│   │   └── Posting.kt          # 复式分录明细实体（外键级联）
│   ├── dao/
│   │   ├── AccountDao.kt       # 账户数据访问与动态聚合余额 SQL 查询
│   │   ├── TransactionDao.kt   # 交易事务管理
│   │   └── PostingDao.kt       # 分录流水访问
│   └── AppDatabase.kt          # Room 数据库与基础多账户种子数据注入
├── repository/
│   └── LedgerRepository.kt     # 核心业务调度、借贷平衡校验、还款与转账分录自动生成
├── ui/
│   ├── MainActivity.kt         # 主界面（底部导航与记账入口）
│   ├── dashboard/
│   │   └── DashboardFragment.kt# 资产看板（净资产/总资产/总负债统计卡片、分类账户列表）
│   ├── transactions/
│   │   └── TransactionsFragment.kt # 流水列表（资金流向节点标注与手续费标签）
│   ├── dialogs/
│   │   └── AddTransactionBottomSheet.kt # 智能记账弹窗（支持支出、收入、转账/还款三模式）
│   ├── adapters/
│   │   ├── AccountCardAdapter.kt
│   │   └── TransactionListAdapter.kt
│   └── viewmodel/
│       └── MainViewModel.kt
└── FlowLedgerApplication.kt    # 全局依赖单例上下文
```

---

## 三、 使用 VS Code 进行开发

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
* **Install APK via ADB**：直接安装 APK 到连接的安卓手机
* **Clean Project**：清理构建缓存

---

## 四、 命令行编译与打包

在终端直接运行：
```powershell
powershell -ExecutionPolicy Bypass -File .\build_apk.ps1
```
或者使用 Gradle Wrapper：
```powershell
.\gradlew.bat assembleDebug
```
产物将输出至：`D:\book\FlowLedger-v1.0.0.apk`。
