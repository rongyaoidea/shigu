# 开源量化工具的功能分区与布局调研

> 目的：给「拾股」定一个不自嗨的信息架构。因子是研究素材，策略是交易规则，回测与模拟是同一套引擎的两种时间视角。

## 1. 参考对象

| 工具 | 类型 | 它的核心分层 | 对拾股的启发 |
|---|---|---|---|
| **Microsoft Qlib** | 开源 AI 量化平台 | DataServer → Data Handler / Dataset → Forecast Model（Alpha）→ Portfolio Strategy（TopkDropout）→ Backtest / Recorder → Online Serving | “因子/信号”和“组合策略”必须分开；回测与线上服务共用同一套决策接口 |
| **QuantConnect LEAN** | 开源事件驱动引擎 | Universe Selection → Alpha Model → Portfolio Construction → Risk Management → Execution | 一条策略 = 选池 + 信号 + 组合构建 + 风控 + 执行，不只是“因子打分” |
| **qteasy** | 开源策略运行时 | DataSource / DataType → BaseStrategy（声明数据窗口）→ Operator（backtest / live / optimize） | 策略声明所需数据与窗口，运行时注入；回测与实盘用同一个 `realize()` |
| **QuantDinger** | 开源 AI Trading OS | Factor Lab（单次面板、IC/换手/成本评估）→ Strategy Evolution（参数搜索）→ Signal-only virtual accounts（纸面订单/净值） | “Factor Lab”与“策略演化”分栏；创建后的信号账户自动记录虚拟成交与净值 |
| **JoinQuant / RiceQuant** | 国内量化平台 | 研究 → 策略 → 回测 → 模拟 → 交易 → 数据 | 国内用户熟悉“回测/模拟/交易”三级；自用工具应把“交易”拿掉，保留“模拟” |

### 关键结论

1. **因子不是策略**。因子只产生横截面分数；策略必须补齐：资产池、买卖规则、持仓数、调仓频率、成本、风控。
2. **回测与模拟共用引擎**。回测是历史区间重放；模拟是从策略创建日开始的向前重放。两者只有时间窗口不同，不能写两套逻辑。
3. **创建即模拟**。策略保存后立刻记录创建时间、首期持仓与净值，之后每日更新；不是“再点一次运行”。
4. **数据/因子/策略/模拟四层**。数据层负责完整资产池（股票 + ETF）与点时数据；因子层负责研究；策略层负责规则；模拟层负责纸面组合与复盘。
5. **Top-K 是策略参数**。买入分数最高的多少只，属于组合构建，不属于因子本身。

## 2. 拾股的映射

```
数据（Data）
  └─ 股票 + ETF 列表、日线、估值快照、沪深300基准
        ↓
因子（Factor）
  └─ 手动筛选 / 因子库 / 排序 / 解释
        ↓  “存为策略”
策略（Strategy）
  └─ 资产池 + 因子权重 + Top-K + 调仓 + 止损/止盈 + 成本
        ↓
回测（Backtest）        模拟（Paper）
  └─ 历史区间重放          └─ 创建日 → 今天，每日更新
        ↓
复盘 / 观察
```

### 四个 tab

| Tab | 只做什么 | 不做什么 |
|---|---|---|
| **因子** | 选因子、调权重、看 Top-K 候选、读因子解释 | 不自动产生买卖，不展示“策略收益” |
| **策略** | 内置策略、我的策略、参数、回测、研究依据 | 不把单次筛选结果伪装成策略 |
| **模拟** | 所有已创建策略的纸面净值/持仓；个股观察池 | 不接券商、不下单 |
| **数据** | 同步、覆盖、自动更新、数据口径 | 不展示荐股观点 |

### 策略对象

```text
Strategy = Universe(股票/ETF/全部, 池大小)
         + Factors(因子 + 权重 + 方向)
         + TopK(买入分数最高的 N 只)
         + Rebalance(每 N 个交易日)
         + Risk(止损/止盈/最大持有)
         + Execution(收盘信号, 次日开盘, 成本/整手)
```

保存后：

- `created_at` 写入数据库；
- 首个调仓日生成纸面持仓；
- 每个交易日 16:10 后更新“创建后收益”、基准、持仓；
- 回测收益与创建后收益分开展示，不混成一个“总收益”。

## 3. 界面布局的取舍

- **不采用** Qlib 的 YAML/代码式工作流：手机端不适合写配置文件；把“工作流”压缩成四个 tab。
- **不采用** LEAN 的五段式全暴露：普通自用用户不需要自定义 Execution Model；内置“次日开盘、整手、成本 bps”即可。
- **采用** QuantDinger 的 Factor Lab 思路：因子页支持单次筛选和解释，不直接承诺收益。
- **采用** Qlib TopkDropout 的核心：策略层明确“持仓 Top-K”和“调仓日”，并把它做成滑杆/芯片，而不是藏在代码里。
- **采用** qteasy 的“同一套运行时”：回测与模拟共用 `BacktestEngine`，只是 `fromDate` 不同。

## 4. 因子库（当前 37 个）

| 组 | 因子 |
|---|---|
| 趋势/阻力 | RSRS 右偏、RSRS 标准分、RSRS 斜率、RSRS R²、LLT、MA20/60、距 MA60、距 20 日高、60 日回撤 |
| 量价/微观 | 量比、量价背离、换手稳定、成交额稳定、球队硬币、隔夜距离、日间/日内反转、低换手、成交额、Amihud |
| 风险 | 20/60 日波动、ATR、特质波动、Beta、MAX、偏度、峰度 |
| 传统动量/反转 | 5/20/60/120/250 日、RSI14（保留在库，不做主打策略） |
| 估值 | PE、PB、市值（当前截面；历史回测需要估值历史） |

主打策略：

1. RSRS 右偏趋势（光大 + 广发 LLT）
2. 球队硬币（方正金工）
3. 量价稳定（华西 + 国盛）
4. 低特质波动·彩票回避（JAM + MAX/偏度）
5. LLT 低延迟趋势（广发）
6. 新因子复合（RSRS + 球队硬币 + 量价 + 低特质波动）

## 5. 数据与偏差

- 股票 + ETF：新浪列表；日线：腾讯（前复权）。
- 成交额/换手为日线估算：`volume × close`、`volume / floatShares`；与交易所口径有差异。
- 回测池是“当前仍在交易”的资产，存在幸存者偏差；已退市资产不在池内。
- 估值历史来自东方财富，可能限流；价值因子的历史回测在缺少数据时会降权并提示。
- 回测：收盘信号、次日开盘、整手、等权、成本 bps；不含冲击成本和涨跌停排队细节（有一字板跳过）。

## 6. 参考链接

- Qlib 架构：https://github.com/microsoft/qlib / https://qlib.readthedocs.io/
- Qlib TopkDropoutStrategy：https://github.com/microsoft/qlib/blob/main/docs/component/strategy.rst
- QuantConnect LEAN Algorithm Framework：https://www.quantconnect.com/docs/v2/writing-algorithms/algorithm-framework/overview
- LEAN 引擎：https://www.quantconnect.com/docs/v2/writing-algorithms/key-concepts/algorithm-engine
- qteasy 架构：https://qteasy.readthedocs.io/en/stable/design/00-overview.html
- QuantDinger：https://github.com/OpenByteInc/QuantDinger

> 以上为功能分区的调研，不构成对任何平台收益或产品的推荐。
