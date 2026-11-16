# 拾股 · A股 / ETF 选股与策略模拟（自用）

> 工作名。Android APK，已跑通：本地行情同步 → 因子筛选 → 策略回测 → 创建后纸面模拟 → 每日调仓红标/通知。
> 仅自用，不接券商、不荐股、不构成投资建议。
>
> 仓库：https://github.com/rongyaoidea/shigu （public）
> Release：https://github.com/rongyaoidea/shigu/releases/tag/v0.1.0

## 现在有什么

- **资产池**：沪深主板、创业板、科创板、北交所、ETF；约 6,500+ 个代码（随数据源变化）。
- **37 个因子**：RSRS 右偏/标准分/斜率/R²、LLT、均线趋势、距 20 日高、回撤、量价背离、换手/成交额稳定、球队硬币、隔夜距离、特质波动、MAX、偏度、峰度、Amihud、低波、Beta，以及传统动量/反转和当前 PE/PB/市值。
- **6 个内置策略**（研究依据见 `DESIGN.md` / `docs/QUANT_UI_RESEARCH.md`）：
  - RSRS 右偏趋势
  - 球队硬币（方正金工）
  - 量价稳定（华西 + 国盛）
  - 低特质波动·彩票回避
  - LLT 低延迟趋势（广发）
  - 新因子复合
- **因子 ↔ 策略分离**：
  - `因子` 页只做手动筛选、排序、解释；
  - 点“存为策略”后，因子组合 + TopK + 调仓/止损才成为策略；
  - 策略页可改持仓数、调仓周期、股票池、止损，并回测；
  - 自定义策略可直接编辑并保存（会重置创建后模拟）；内置策略另存为我的策略；
  - 保存后自动进入 `模拟` 页，从创建日开始记录纸面收益；
  - 模拟策略卡“详情 →”可看纸面净值、现金和实际持仓；
  - 模拟页顶部“立即核对 →”可不等 16:10 手动刷新纸面组合。
- **调仓提示**：
  - 每日 16:10 WorkManager 同步日线；
  - 策略按自己的调仓周期决定买入/卖出/继续持有；每天检查止损/止盈；
  - 有买卖时，策略卡出现**红色“调仓 · 买N 卖N”标志**，底部“策略/模拟”也出现红点；
  - 同时发本地通知（需授权）。
- **回测**：收盘信号、次日开盘、整手 100 股、等权、初始资金 100 万、成本 8bps/边；含基准、超额、最大回撤、夏普、胜率、交易数、持仓。
- **数据**：新浪资产列表 + 腾讯前复权日线；北交所走新浪不复权兜底；东方财富估值历史可选（可能限流）。

## 看设计

- 早期设计说明（纸墨语言、tokens、6 屏概念稿）：`DESIGN.md`
- 开源量化工具调研与信息架构：`docs/QUANT_UI_RESEARCH.md`
- 数据与模拟口径：`docs/DATA_AND_SIMULATION.md`
- 实现状态 / 已验证项 / 缺口：`docs/STATUS.md`
- 发布与 GitHub Secrets 签名：`docs/RELEASE_SIGNING.md`
- 高保真设计稿：`design/mockups.html`

### 真机截图

- `design/screens/app-factors.png` 因子页（股票/ETF/全部、TopK、存为策略）
- `design/screens/app-factor-picker.png` 因子库与权重步进
- `design/screens/app-strategies.png` 策略库（回测 + 创建后 + 编辑因子/持仓数）
- `design/screens/app-simulation.png` 模拟页（创建后收益、目标持仓、详情）
- `design/screens/app-paper-detail.png` 纸面净值/实际持仓详情
- `design/screens/app-strategy-alert.png` 调仓红标（本地测试注入的持仓差异，用于验证 UI）
- `design/screens/app-simulation-alert.png` 模拟页调仓提示（同上）
- `design/screens/app-stock.png` 个股 K 线与因子快照

## 构建

```powershell
$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot"
.\gradlew.bat :app:assembleDebug
```

APK：

```
app\build\outputs\apk\debug\app-debug.apk      # com.shigu.debug
app\build\outputs\apk\release\app-release.apk  # 正式签名（CI tag）或本地 debug 签名
app-release.apk                                # 根目录副本，直接安装
```

发布：`git tag v0.1.0 && git push origin v0.1.0` → GitHub Actions 用 Secrets 里的 keystore 签名并创建 Release。详见 `docs/RELEASE_SIGNING.md`。

安装到模拟器/真机：

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.shigu.debug/com.shigu.MainActivity
```

首次启动自动拉“市值前 300（股票 + ETF）”，约 1–3 分钟；之后每日 16:10 自动更新当前池。全量下载在“数据”页。

## 模块

```
app/src/main/java/com/shigu/
├── core/          纯 Kotlin：因子、筛选、回测、策略预置、收益口径
├── data/          新浪/腾讯 API、SQLite、同步、策略/纸面模拟、WorkManager
└── ui/            Compose：因子 / 策略 / 模拟 / 数据 + 个股页 + 纸墨组件
```

## 已知边界

- 行情延迟约 15 分钟；成交额/换手为日线估算，不是交易所逐笔口径。
- 回测池是当前仍上市的资产，有幸存者偏差；已退市样本不在内。
- BSE 日线为新浪不复权；ETF 池剔除了货币/短融/债券型，避免平坦序列占满 TopK。
- 估值历史来自东方财富，可能限流；价值类因子在历史回测中缺少数据会自动降权。
- 不接券商、不下单；“模拟”是纸面组合，不是实盘。

## 设计/研究参考

- Microsoft Qlib：Data → Alpha/Factor → Portfolio Strategy → Backtest
- QuantConnect LEAN：Universe → Alpha → Portfolio Construction → Risk → Execution
- qteasy：Data → Strategy → Runtime（Backtest / Live）
- QuantDinger：Factor Lab → Strategy Evolution → Signal-only virtual accounts

详见 `docs/QUANT_UI_RESEARCH.md`。
