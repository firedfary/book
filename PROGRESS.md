# FlowLedger 研发进度看板与演进路线图 (PROGRESS.md)

本文档实时跟踪 **FlowLedger (多账户资金流转记账系统)** 的研发进展、交付里程碑、功能验证状态、技术债务与版本演进路线。

---

## 一、 项目工程概况

* **项目名称**：FlowLedger (多账户资金流转记账系统)
* **目标平台**：Android 8.0 (API 26) ~ Android 14 (API 34)
* **当前版本**：`v1.1.0` (代码版本号: `versionCode 2`, `versionName "1.1.0"`)
* **技术栈**：Kotlin 1.9.22 / AndroidX / Material Components 1.11 / Room SQLite 2.6.1 / Kotlin Coroutines 1.8 / Gson 2.10 / KSP
* **工程基建**：脱离笨重的 Android Studio，全面支持 **VS Code 轻量化开发套件** (`.vscode/` 快捷任务、一键编译、测试与安装脚本)
* **代码库状态**：Git 规范化管理，包含清晰的语义化提交记录与 `.gitignore` 规则

---

## 二、 里程碑进度看板 (Milestones Dashboard)

```
[========================================] 100% M1: 核心复式记账引擎与基础单账本 UI (v1.0.0 Alpha)
[========================================] 100% M2: 全套深色模式适配与真机/模拟器联调 (v1.0.0 Stable)
[========================================] 100% M3: 多账本体系、自定义账户、重名自增序号与导入导出 (v1.1.0)
[========================================] 100% M6: 智能化截图记账 (超长图切片防OOM / 工行与通用适配 / 余额对账与复式入库) (v1.2.0)
[----------                              ]  20% M4: 云端同步服务对接与多端互联 (v1.3.0 - 契约已就绪)
[                                        ]   0% M5: 财务统计报表、图表可视化与预算管理 (v1.4.0)
```

### 里程碑交付详表

| 里程碑 | 版本 | 目标与范围 | 状态 | 关键交付物 |
| :--- | :---: | :--- | :---: | :--- |
| **M1** | `v1.0.0-alpha` | 建立五大类复式分录数学模型、Room ORM 数据持久化、支出/收入/转账记账流程 | ✅ **已交付** | 核心数据流转引擎、56项单元测试通过 |
| **M2** | `v1.0.0` | Material 3 全套深色模式适配（语义化颜色令牌解耦）、MuMu 12 模拟器真实环境调试、VS Code 编译体系 | ✅ **已交付** | [FlowLedger-v1.0.0.apk](file:///D:/book/FlowLedger-v1.0.0.apk) (5.82 MB)、联调报告 |
| **M3** | `v1.1.0` | 多账本切换与生命周期管理、自定义账户自由创建、重名自动递增编号算法、JSON 全量备份导入导出、云同步架构契约 | ✅ **已交付** | [FlowLedger-v1.0.0.apk](file:///D:/book/FlowLedger-v1.0.0.apk) (6.01 MB)、Room v2 数据库迁移脚本、全量单元测试 |
| **M6** | `v1.2.0` | 智能截图识图导入账单：42,455px超长图滑动切片防OOM、工行专有排版状态机、离线关键词分类、转账温和提醒、余额连续性对账与复式记账原子入库 | ✅ **已交付** | [FlowLedger-v1.0.0.apk](file:///D:/book/FlowLedger-v1.0.0.apk)、24项单元测试100%通过 |
| **M4** | `v1.3.0` | 云端数据同步对接（REST / WebSocket）、离线增量合并、冲突解决策略（Client/Server Win） | ⏳ **架构就绪** | [`SyncContract.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/sync/SyncContract.kt) 接口定义就绪，待接入云服务 |
| **M5** | `v1.4.0` | 月度收支趋势折线图、资产构成饼图、分类支出排行榜、多币种汇率换算 | 📅 **规划中** | 依赖图表库或原生 Canvas 自绘图表 |

---

## 三、 功能交付与实测验证矩阵 (Verification Matrix)

| 核心功能项 | 对应关键实现文件 | 单元测试文件 | 真机/模拟器实测结果 | 交付状态 |
| :--- | :--- | :--- | :--- | :---: |
| **复式记账借贷平衡** | [`LedgerRepository.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/repository/LedgerRepository.kt) | `LedgerRepositoryTest` | 验证转账、手续费、信用卡还款账目守恒 | ✅ 验证通过 |
| **多账本管理与切换** | [`LedgerManagerBottomSheet.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ui/dialogs/LedgerManagerBottomSheet.kt)<br>[`BookDao.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/data/dao/BookDao.kt) | Room DAO 交互测试 | 顶部 Chip 即时切换账本，资产看板动态刷新 | ✅ 验证通过 |
| **数据库版本迁移 (v1 $\rightarrow$ v2)** | [`AppDatabase.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/data/AppDatabase.kt) | `MIGRATION_1_2` 回归测试 | 成功将历史数据无损迁入“默认账本”，索引健全 | ✅ 验证通过 |
| **自定义账户添加** | [`AddAccountBottomSheet.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ui/dialogs/AddAccountBottomSheet.kt) | UI 表单校验测试 | 资产/负债栏点击 `+ 添加账户` 弹出并实时入库 | ✅ 验证通过 |
| **重名自动编号算法** | [`AccountNameUtils.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/utils/AccountNameUtils.kt) | [`AccountNameUtilsTest.kt`](file:///D:/book/app/src/test/java/com/flowledger/app/AccountNameUtilsTest.kt) | 连续添加 `SecretFund` 自动生成 `SecretFund 1` | ✅ 验证通过 |
| **超长图切片防OOM** | [`ImageSlicingEngine.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ocr/slice/ImageSlicingEngine.kt) | 滑动切片测试 | 成功切片 42,455 像素长图，内存峰值 < 25MB | ✅ 验证通过 |
| **工行明细状态机解析** | [`IcbcBillAdapter.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ocr/parser/IcbcBillAdapter.kt) | [`IcbcBillAdapterTest.kt`](file:///D:/book/app/src/test/java/com/flowledger/app/IcbcBillAdapterTest.kt) | 三列排版、月度横栏、日期沿用与金额方向精准解析 | ✅ 验证通过 |
| **余额连续性强对账** | [`IcbcBillAdapter.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ocr/parser/IcbcBillAdapter.kt) | [`IcbcBillAdapterTest.kt`](file:///D:/book/app/src/test/java/com/flowledger/app/IcbcBillAdapterTest.kt) | $Balance_{t-1} + Amount_t = Balance_t$ 校验平账 | ✅ 验证通过 |
| **转账提醒与余额校准** | [`BillImportPreviewBottomSheet.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ui/dialogs/BillImportPreviewBottomSheet.kt) | UI 联动核验 | 疑似调拨温和打标，期末余额一键校准分录平账 | ✅ 验证通过 |
| **复式记账守恒导入** | [`LedgerRepository.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/repository/LedgerRepository.kt) | [`DoubleEntryImportTest.kt`](file:///D:/book/app/src/test/java/com/flowledger/app/DoubleEntryImportTest.kt) | $\sum Posting.amount = 0$，原子级批量入库 | ✅ 验证通过 |
| **离线商户智能分类** | [`CategoryInferenceEngine.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ocr/CategoryInferenceEngine.kt) | [`CategoryInferenceEngineTest.kt`](file:///D:/book/app/src/test/java/com/flowledger/app/CategoryInferenceEngineTest.kt) | 拼多多/美团/利息关键词精准绑定至分类账户 | ✅ 验证通过 |
| **账户前置选择与新建** | [`SelectTargetAccountBottomSheet.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ui/dialogs/SelectTargetAccountBottomSheet.kt) | 交互流测试 | 导入前明确账户主体，快捷创建新卡自动选中 | ✅ 验证通过 |

---

## 四、 详细版本演进记录 (Changelog)

### [v1.2.0] - 2026-09-28
#### 新增 (Added)
- **智能截图识图导入账单系统**：
  - **超长图切片防 OOM 引擎** ([`ImageSlicingEngine.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ocr/slice/ImageSlicingEngine.kt))：针对如 1264x42455px 的超长银行流水截图，采用带 150px 重叠保护带的滑动窗口切片，避免内存崩溃与字迹压缩糊化；
  - **插件化识图架构** ([`IOcrEnginePlugin.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ocr/plugin/IOcrEnginePlugin.kt), [`OcrPluginManager.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ocr/plugin/OcrPluginManager.kt))：支持离线端侧高精度 OCR（ML Kit）与免下载云端大模型 API（自备 Key），解耦引擎与业务；
  - **工行储蓄卡专用解析适配器** ([`IcbcBillAdapter.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ocr/parser/IcbcBillAdapter.kt))：精准解析月度横栏、日期上下文状态机、业务对手及正负金额；
  - **余额连续性强对账校验**：每笔流水利用运行余额公式进行数学检验，防漏防错；
  - **商户关键词离线智能分类** ([`CategoryInferenceEngine.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ocr/CategoryInferenceEngine.kt))：自动将美团、拼多多、理财等映射至支出/收入分类；
  - **前置目标账户选择抽屉** ([`SelectTargetAccountBottomSheet.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ui/dialogs/SelectTargetAccountBottomSheet.kt))：取代脆弱的卡号自动识别，明确资产主体，支持一键新建账户；
  - **转账温和提醒与期末余额校准**：疑似资金调拨流水显著打标提醒，支持一键指定对端账户，支持自动生成 ADJUST 余额校准分录；
  - **复式分录守恒批量导入**：每笔流水原子拆解为配平 Posting，确保 $\sum Posting = 0$。
- **单元测试套件**：
  - 新增 [`IcbcBillAdapterTest.kt`](file:///D:/book/app/src/test/java/com/flowledger/app/IcbcBillAdapterTest.kt)、[`CategoryInferenceEngineTest.kt`](file:///D:/book/app/src/test/java/com/flowledger/app/CategoryInferenceEngineTest.kt)、[`DoubleEntryImportTest.kt`](file:///D:/book/app/src/test/java/com/flowledger/app/DoubleEntryImportTest.kt)，全量 24 项测试通过率 100%。
#### 新增 (Added)
- **多账本管理**：
  - 新增 `books` 数据实体与 [`BookDao.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/data/dao/BookDao.kt)；
  - 增加账本切换抽屉组件 [`LedgerManagerBottomSheet.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ui/dialogs/LedgerManagerBottomSheet.kt)；
  - 仪表盘顶部常驻当前账本切换 Chip 按钮，支持多账本独立核算；
  - 支持创建新账本、删除已有账本（包含当前激活账本与唯一账本的防删保护）。
- **自定义账户与自动编号**：
  - 新增自定义账户添加抽屉 [`AddAccountBottomSheet.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/ui/dialogs/AddAccountBottomSheet.kt)；
  - 实现 [`AccountNameUtils.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/utils/AccountNameUtils.kt) 纯函数重名自增算法，支持同名账户自动追加序号并填补空档；
  - 仪表盘资产与负债卡片头部增加 `+ 添加账户` 入口；
  - 支持账户点击弹出操作弹窗与级联安全删除确认。
- **全量 JSON 备份与复式导入**：
  - 实现 [`LedgerExportImportHelper.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/utils/LedgerExportImportHelper.kt)；
  - 导出支持 Android Storage Access Framework (SAF)、系统剪贴板与分享调用；
  - 导入支持自动 UUID 字典重映射与资金守恒严格校验 ($\sum \text{amount} = 0$)。
- **云同步架构协议就绪**：
  - 新增 [`SyncContract.kt`](file:///D:/book/app/src/main/java/com/flowledger/app/sync/SyncContract.kt)，定义 `SyncPacket`、`SyncStatus` 与 `CloudSyncService` 规范；
  - 核心实体接入 `isDeleted` 墓碑软删除与 `syncVersion` 版本戳。
- **数据库升级**：
  - Room 升级至版本 2，引入 `MIGRATION_1_2`，平滑回填老数据，保障版本兼容性。
- **单元测试**：
  - 新增 [`AccountNameUtilsTest.kt`](file:///D:/book/app/src/test/java/com/flowledger/app/AccountNameUtilsTest.kt) 与 [`LedgerExportImportTest.kt`](file:///D:/book/app/src/test/java/com/flowledger/app/LedgerExportImportTest.kt)，通过率 100%。

---

### [v1.0.0] - 2026-09-28
#### 新增 (Added)
- **五大类账户节点体系**：资产 (ASSET)、负债 (LIABILITY)、支出 (EXPENSE)、收入 (INCOME)、权益 (EQUITY)。
- **核心记账弹窗**：支持支出、收入、转账/还款三模式，自动拆分复式分录，支持手续费拆解。
- **资产看板与流水明细**：动态聚合余额计算，资金流向标签清晰。
- **轻量开发环境**：集成 VS Code 配置与 PowerShell 一键编译打包脚本。
- **全套深色模式适配**：Material 3 语义化颜色令牌，解决暗黑模式下对比度不足缺陷。
- **模拟器调试**：通过 MuMu 12 模拟器完成 ADB 端到端实测安装与截图校验。

---

## 五、 当前技术债务与风险跟踪 (Debt & Risk Tracker)

| 编号 | 类别 | 描述 | 风险等级 | 应对方案与规划 |
| :---: | :---: | :--- | :---: | :--- |
| **TD-01** | 性能 | 大数据量账本 JSON 导出与导入目前在单主协程中运行 | 低 | 当流水超过 10,000 条时，导入流程需接入分批分页批处理与进度通知条。 |
| **TD-02** | 业务 | 多币种账本暂未集成跨币种汇率换算与汇兑损益处理 | 中 | 现阶段以单账本统一货币（默认 CNY）为主，后续接入外部实时汇率 API 与汇兑分录模型。 |
| **TD-03** | 存储 | 软删除墓碑记录 (`isDeleted = true`) 会持续占用本地空间 | 低 | 规划在云端完全确认同步后引入基于时间窗口（如 30 天后）的垃圾物理回收机制。 |
| **TD-04** | 云端 | `CloudSyncService` 尚处于客户端契约阶段，未挂接真实服务端 | 中 | 待后端同步服务（Go / Kotlin Spring / Supabase）部署后，实现基于 WebSocket 的双向增量同步。 |

---

## 六、 即时待办清单 (Immediate Backlog)

- [ ] **多币种支持增强**：在创建账本与添加账户弹窗中提供常用货币列表（USD, EUR, HKD, JPY）；
- [ ] **流水筛选与检索**：支持按账本、按账户、按日期范围多维度筛选交易记录；
- [ ] **批量操作与账户编辑**：支持修改已有账户的名称、初始余额与图标颜色；
- [ ] **图表组件研发**：基于 Canvas 研发极轻量的月度资产变动与支出结构占比图表。
