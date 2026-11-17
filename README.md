# 拾股 · A股 / ETF 选股与策略模拟（自用）

> 工作名。Android APK，已跑通：本地行情同步 → 因子筛选 → 策略回测 → 创建后纸面模拟 → 每日调仓红标/通知。
> 仅自用，不接券商、不荐股、不构成投资建议。
>
> 仓库：https://github.com/rongyaoidea/shigu （public）
> Release：https://github.com/rongyaoidea/shigu/releases/tag/v0.5.0
> v0.5.0 APK SHA-256：`13abd1fa0c15bed800469caa3bc9efcdeb4b2ca5e5b17bc48ceff91f1dda9ce2`

## 现在有什么

- **资产池**：沪深主板、创业板、科创板、北交所、ETF；约 6,500+ 个代码（随数据源变化）。
- **47 个因子**：RSRS 右偏/标准分/斜率/R²、LLT、均线趋势、距 20 日高、回撤、量价背离、换手/成交额稳定、球队硬币、隔夜距离、特质波动、MAX、偏度、峰度、Amihud、低波、Beta，以及传统动量/反转、当前 PE/PB/市值、上市天数、ROE、毛利率、净利率、营收/净利增速、资产负债率、EPS/BPS/每股经营现金流。
- **因子研究**：
  - z-score / 排名两种打分；
  - 单因子“体检”：Rank IC、ICIR、正 IC 比例、Top-Bottom 十分位、覆盖率；
  - **自定义因子公式**：用现有因子做 `+ - * / ^`、括号、abs/sqrt/log/min/max/clip，保存后按权重加入筛选/策略；
  - **因子相关性**：当前截面 Pearson 相关，列出高度重叠的因子对（|r|>0.7 标红）；
  - 个股卡显示因子分数贡献（为什么是它）；
  - 因子库支持搜索、分类、等权、权重归一化。
- **基础过滤**：
  - 企业性质：央企 / 地方国企 / 民企 / 外资 / 无实控人 / 未知；
  - ST 状态：正常 / ST / *ST / 退市整理；
  - 停牌、上市天数、板块、最低价、20 日均额、PE/PB 上限、市值区间。
  - 企业性质按东方财富 F10 的实际控制人 + 第一大股东名称推断，不是官方标签；个股详情会保留原始实控人/股东供复核。
- **筛选漏斗**：从全池到命中，逐步显示板块、价格/流动性、ST/停牌、企业性质、上市天数、估值/市值、资料完整、因子覆盖、TopK 截断的淘汰数量与原因。
- **组合层**：按每个策略 100 万名义本金合并，显示组合纸面、收益、现金、Top 10 合并持仓、重叠持仓、最大单票、策略日收益相关性。
- **策略对比**：勾选 2–4 个策略，对比回测/创建后收益、回撤、夏普、交易数、费用和共同持仓数。
- **调仓预览**：按当前目标 TopK、等权、整手、收盘价估算买入/卖出清单、金额、费用和换手；实际以调仓日开盘为准。
- **数据质量**：个股页标记“北交所不复权 / 停牌 / 资料未同步”，数据页显示北交所/ST/停牌数量；基础过滤可勾“只看资料完整”。
- **期权模拟 · 510050/510300**：
  - 只买 call/put，不卖裸期权；卖出只用于平仓/调仓；
  - 用 ETF 的 RSRS/LLT/均线/动量/量比等方向因子投票，内置 5 个方向策略；
  - 自选 ETF 方向因子保存为自定义信号；
  - 方向反转、合约到期或信号中性时卖出旧仓/买入新仓；
  - 目标行权价 ATM/±1/±2、1–20 张；显示权利金、最大亏损、盈亏平衡、剩余天数；
  - 数据：东方财富期权链 + 新浪期权日线；每日日线纸面盯市，不做历史期权回测。
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
- **回测**：收盘信号、次日开盘、整手 100 股（科创 200）、等权、初始资金 100 万、佣金 8bps + 卖出印花税 5bps + 过户费 + 滑点 5bps；含基准、超额、最大回撤、夏普、胜率、交易数、累计费用、持仓。
- **调仓**：只卖出跌出目标的持仓；仍在目标里的继续持有，不再“全部卖出再买回”。
- **纸面模拟**：创建后自动记录；净值曲线全量落库，最近 200 笔交易可查；模拟页显示组合纸面合计与平均收益。
- **成交规则**：收盘信号 → 次日开盘成交；开盘涨停不买、跌停不卖。模拟页“同步+核对”会先补当天日线再重算，避免只看本地旧数据误以为“没成交”；数据过期会红字提示“待核对至 X”。
- **策略卡双收益**：同一张卡同时显示“回测收益（过去 1 年 / 300 池）”和“创建后纸面收益”，以创建日作为中间分割线；避免只看回测或只看模拟。
- **数据**：新浪资产列表（稳定 symbol 排序）+ 腾讯前复权日线（web.ifzq.gtimg.cn）；北交所走新浪不复权兜底；东方财富 F10 基础资料/财务/股东批量分页；东方财富估值历史可选（可能限流）。
- **性能**：Panel 与因子值/截面因子按数据版本缓存；同一批策略刷新时复用，不再为每个策略重查 70–90MB 的面板。
- **数据安全**：列表低于上次 98% 时停止清理；同步可取消；状态变化历史 + 财报历史开始按日/按报告期落库。
- **备份**：数据页可把自定义策略/观察池导出为 JSON 复制到剪贴板，也可从剪贴板导入；不含行情/估值/纸面净值。

## 看设计

- 早期设计说明（纸墨语言、tokens、6 屏概念稿）：`DESIGN.md`
- 开源量化工具调研与信息架构：`docs/QUANT_UI_RESEARCH.md`
- 数据与模拟口径：`docs/DATA_AND_SIMULATION.md`
- 回测/纸面模拟假设：`docs/BACKTEST_ASSUMPTIONS.md`
- 因子目录（47 个）：`docs/FACTOR_CATALOG.md`
- 数据源与接口：`docs/DATA_SOURCES.md`
- 实现状态 / 已验证项 / 缺口：`docs/STATUS.md`
- 发布与 GitHub Secrets 签名：`docs/RELEASE_SIGNING.md`
- 高保真设计稿：`design/mockups.html`

### 真机截图

- `design/screens/app-factors.png` 因子页（股票/ETF/全部、TopK、存为策略）
- `design/screens/app-basic-filter.png` 基础过滤（企业性质/ST/上市天数/板块/价格流动性）
- `design/screens/app-factor-picker.png` 因子库与权重步进
- `design/screens/app-factor-picker-v2.png` 因子搜索、z-score/排名、等权、体检入口
- `design/screens/app-factor-stats.png` 单因子体检（Rank IC/ICIR/分层）
- `design/screens/app-custom-factor.png` 自定义因子公式编辑器（变量面板）
- `design/screens/app-custom-factor-added.png` 自定义因子加入筛选、权重调整
- `design/screens/app-factor-correlation.png` 因子相关性（高相关标红）
- `design/screens/app-strategies.png` 策略库（回测 + 创建后 + 编辑因子/持仓数）
- `design/screens/app-simulation.png` 模拟页（创建后收益、目标持仓、详情）
- `design/screens/app-simulation-v2.png` 组合纸面合计、调仓红标
- `design/screens/app-strategy-return-split.png` 策略卡：回测收益 / 创建日分割线 / 创建后收益
- `design/screens/app-funnel.png` 筛选漏斗（全池 → 价格/流动性 → 因子覆盖 → 命中）
- `design/screens/app-portfolio.png` 组合层：合并持仓、重叠、策略相关性
- `design/screens/app-rebalance-preview.png` 调仓预览：买卖清单、金额、费用、换手
- `design/screens/app-strategy-compare.png` 策略对比：回测/创建后/回撤/夏普/费用
- `design/screens/app-paper-detail.png` 纸面净值/实际持仓详情
- `design/screens/app-paper-detail-v2.png` 全量净值曲线 + 持仓
- `design/screens/app-strategy-alert.png` 调仓红标（本地测试注入的持仓差异，用于验证 UI）
- `design/screens/app-simulation-alert.png` 模拟页调仓提示（同上）
- `design/screens/app-stock-profile.png` 个股页基础资料（企业性质/实控人/财务快照）
- `design/screens/app-data-profile.png` 数据页基础资料覆盖与报告期
- `design/screens/app-data-profile-v2.png` 数据页 6,469 资产、96.6K 日线、19.5MB
- `design/screens/app-backup.png` 数据页备份：导出策略/观察到剪贴板
- `design/icon-preview.png` 手绘 A 股票据图标（512/192/96/48/32 与圆形遮罩预览）

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

首次启动自动拉“市值前 300（股票 + ETF）”，并补一次东方财富 F10 基础资料（企业性质/上市日期/最新报告期财务）；行情约 1–3 分钟，基础资料约 1–2 分钟。之后每日 16:10 自动更新当前池；基础资料可在“数据”页手动刷新。

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
- 企业性质是按实控人/第一大股东名称做的启发式分类，不是交易所或官方标签；F10 资料缺失时显示“未知”。
- 财务因子是最新报告期快照；历史回测使用当前值，存在前视偏差，界面会显式警告。
- ST / 停牌 / 上市天数 / 企业性质都是当前快照；已开始记录状态变化历史，但历史长度还不足以完全消除回测前视偏差。
- 纸面模拟目前仍是“从创建日重跑”，已持久化全部净值点和最近 200 笔交易；还不是严格冻结的逐日账本。
- 北交所日线不复权；成交额/换手为估算；尚未做行业中性、walk-forward、导出/导入。
- 不接券商、不下单；“模拟”是纸面组合，不是实盘。

## 设计/研究参考

- Microsoft Qlib：Data → Alpha/Factor → Portfolio Strategy → Backtest
- QuantConnect LEAN：Universe → Alpha → Portfolio Construction → Risk → Execution
- qteasy：Data → Strategy → Runtime（Backtest / Live）
- QuantDinger：Factor Lab → Strategy Evolution → Signal-only virtual accounts

详见 `docs/QUANT_UI_RESEARCH.md`。
