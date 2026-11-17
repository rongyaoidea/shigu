# 数据源与接口

> 公开接口会变、会限流；这里记录当前实现用到的参数与坑，便于以后排查。

## 1. 新浪：资产列表

```
https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/
  Market_Center.getHQNodeData?page=1&num=100&sort=symbol&asc=1&node=hs_a
```

- `hs_a`：沪深 A 股，**当前也包含北交所**（9 开头）。
- `etf_hq_fund`：ETF 列表。
- `hs_bjs`：北交所列表（与 hs_a 有重叠，代码用 `seen` 去重）。
- **必须用 `sort=symbol&asc=1`**。用 `sort=changepercent` 分页时行情在变，会重复/漏股票；实测同一天 unique 只有 5291/5579。
- 列表接口偶尔短页；实现会继续翻到空页，不因单页 <100 提前结束。
- 安全阈值：新列表 <3000 或 <上次的 98% 时停止 prune，保留旧代码。

## 2. 腾讯：前复权日线

```
https://proxy.finance.qq.com/ifzqgtimg/appstock/app/fqkline/get?param=sh600036,day,,,300,qfq
```

- 返回 `qfqday`/`day` 数组：`[date, open, close, high, low, volume(手)]`。
- `volume × 100` = 股；`amount = 股数 × close` 是**估算**，不是真实成交额。
- `turnover = 股数 / 流通股本 ×100`，流通股本用新浪 `nmc / price` 估算；ETF 的“流通股本”语义不准确。
- 北交所（`bj`）腾讯不支持，走新浪不复权日线。

## 3. 新浪：北交所日线（不复权）

```
https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/
  CN_MarketData.getKLineData?symbol=bj920xxx&scale=240&ma=no&datalen=300
```

- 只有 OHLCV；不复权。
- 送转/拆股会造成价格断层，技术因子需谨慎。

## 4. 东方财富 F10：基础资料

### 企业资料

```
https://datacenter.eastmoney.com/securities/api/data/v1/get
  ?reportName=RPT_F10_BASIC_ORGINFO
  &columns=SECUCODE,SECURITY_CODE,SECURITY_NAME_ABBR,LISTING_DATE,ACTUAL_HOLDER
  &filter=(SECURITY_TYPE_CODE="058001001")
  &pageNumber=1&pageSize=500&sortColumns=SECURITY_CODE&sortTypes=1
```

- 返回上市日期、实际控制人。
- 约 17 页（500/页）。

### 主要财务指标

```
reportName=RPT_F10_FINANCE_MAINFINADATA
columns=...,REPORT_DATE,NOTICE_DATE,ROEJQ,XSMLL,XSJLL,
        TOTALOPERATEREVETZ,PARENTNETPROFITTZ,ZCFZL,EPSJB,BPS,MGJYXJJE
filter=(REPORT_DATE='2026-09-30')
```

- 报告期由 `resolveReportDate()` 从最近 8 个季度末里挑“行数 ≥1000”的第一个。
- 财务历史写入 `fundamentals_history`，用公告日 `NOTICE_DATE` 为未来 as-of 回测做准备。

### 第一大股东

```
reportName=RPT_F10_EH_FREEHOLDERS
columns=SECUCODE,SECURITY_CODE,END_DATE,HOLDER_NAME,HOLDER_TYPE,HOLD_RATIO,HOLDER_RANK
filter=(HOLDER_RANK=1)(END_DATE='2026-09-30')
```

- 用于辅助判断企业性质；与实控人一起写入 `stocks` 和 `stock_status_history`。

### 字符编码

- 接口返回 UTF-8；Windows PowerShell 控制台按 GBK 显示会乱码，**不是数据坏**。
- Android 端统一 `String(bytes, Charsets.UTF_8)` 解析。

## 5. 东方财富：估值历史

```
reportName=RPT_VALUEANALYSIS_DET
columns=ALL&filter=(SECURITY_CODE="600036")
&pageNumber=1&pageSize=1000&sortColumns=TRADE_DATE&sortTypes=-1
```

- PE_TTM / PB_MRQ / 总市值 / 收盘价。
- 仅回测池前 800 只手动同步；可能限流。

## 6. 企业性质启发式

- 实控人名称含国务院/中央企业/中央金融关键词 → 央企。
- 含地方国资/财政/政府/城投/交投/地铁等关键词 → 地方国企。
- 自然人 → 民企。
- “无”/空 → 结合第一大股东：国资关键词 → 国企；基金/保险/信托/券商/托管账户 → 无实控人；其余默认民企。
- 已知坑：姓名可能命中地区词（韩国龙、张江波），已将自然人判断放在地区关键词之前；仍可能有边界样本。
- 详情页保留原始实控人/第一大股东，建议人工复核。

## 7. 数据质量状态

- `stock_status_history`：只在 ST/企业性质/停牌/上市日期变化时写一行。
- `stock_status_current`：每只股票的最新状态，用于快速比较。
- `fundamentals_history`：按报告期保存，等待未来接入 as-of 回测。
- `stock` 表的 `profile_updated_at` 表示基础资料覆盖。
