package com.shigu.core

/**
 * 因子库。方向由 [higherIsBetter] 决定；权重永远为正，
 * 打分时 direction 会乘到 z-score 上。
 *
 * 类别：趋势 / 量价 / 风险 / 反转 / 估值。
 * 传统动量、反转保留在库里，但不作为主打策略——主打策略用 RSRS、
 * LLT、球队硬币、量价稳定、特质波动等更新一代的因子。
 */
enum class FactorKey(
    val label: String,
    val shortLabel: String,
    val higherIsBetter: Boolean,
    val unit: String,
    val category: String,
    val description: String,
    val currentOnly: Boolean = false,
    val crossSectional: Boolean = false,
    /** 只有当前快照、没有历史序列；回测里只能当常数用，必须提示前视偏差。 */
    val snapshotOnly: Boolean = false,
) {
    // —— 阻力支撑 / 趋势 ——
    RSRS_RIGHT("RSRS右偏", "RSRS", true, "", "趋势", "光大 RSRS：18 日 high~low 回归斜率的 60 日标准分 × R² × 斜率；越高越强。"),
    RSRS_Z("RSRS标准分", "RSRSz", true, "", "趋势", "RSRS 斜率相对自身 60 日历史的 z-score；衡量支撑/阻力的相对强弱。"),
    RSRS_SLOPE("RSRS斜率", "斜率", true, "", "趋势", "18 日最高价对最低价回归的斜率；越高表示支撑相对阻力更强。"),
    RSRS_R2("RSRS拟合", "R²", true, "", "趋势", "RSRS 回归的决定系数；拟合越好，信号越可信。"),
    LLT_GAP("LLT趋势", "LLT", true, "%", "趋势", "低延迟趋势线（α=2/31，约 30 日）与收盘价的偏离；越大越强。"),
    MA_TREND("均线趋势", "MA20/60", true, "×", "趋势", "MA20 / MA60 - 1，衡量中短期均线相对位置。"),
    MA60_GAP("距MA60", "MA60", true, "%", "趋势", "收盘价相对 60 日均线的偏离。"),
    HIGH20_GAP("距20日高", "20日高", true, "%", "趋势", "收盘价距离 20 日最高价的百分比，越接近 0 越强。"),
    DRAWDOWN60("60日回撤", "回撤", true, "%", "趋势", "收盘价相对 60 日最高收盘的回撤，越接近 0 越好。"),

    // —— 量价 / 微观结构 ——
    VOLUME_RATIO("量能", "量比", true, "×", "量价", "5 日均量 / 20 日均量，放量趋势确认。"),
    VOLUME_PRICE_DIV("量价背离", "量价", true, "", "量价", "20 日收益与成交量变化的相关系数取负；越高越背离。"),
    TURNOVER_STABILITY("换手稳定", "换手稳", false, "", "量价", "20 日换手率 std/mean；越低越稳，拥挤度越低。"),
    AMOUNT_STABILITY("成交额稳定", "额稳", false, "", "量价", "20 日成交额 std/mean；越低越稳。"),
    TEAM_COIN("球队硬币", "球队", false, "", "量价", "方正金工：按波动率/换手率翻转日间、日内、隔夜反转后合成；越低越好。", false, true),
    OVERNIGHT_DIST("隔夜距离", "隔夜", false, "", "量价", "隔夜收益相对市场均值的绝对偏离，20 日均值；越低越平静。", false, true),
    INTERDAY_REV("日间反转", "日间", false, "%", "量价", "20 日日间收益均值；越低越好（旧因子，保留在库）。"),
    INTRADAY_REV("日内反转", "日内", false, "%", "量价", "20 日日内收益（收盘/开盘-1）均值；越低越好。"),
    TURNOVER20("低换手", "换手", false, "%", "量价", "20 日平均换手率；越低越不拥挤。"),
    AMOUNT20("流动性", "成交额", true, "亿", "量价", "20 日平均成交额；作为流动性过滤或正向因子。"),
    AMIHUD20("流动性冲击", "Amihud", false, "", "量价", "|收益|/成交额 的 20 日均值；越低冲击越小。"),

    // —— 风险 / 波动 ——
    VOL20("20日低波", "低波20", false, "%", "风险", "20 日年化波动率；越低越好。"),
    VOL60("60日低波", "低波60", false, "%", "风险", "60 日年化波动率；越低越好。"),
    ATR20("ATR20", "ATR", false, "%", "风险", "20 日平均真实波幅 / 收盘；越低越稳。"),
    IDIO_VOL("特质波动", "特质波", false, "%", "风险", "相对沪深300 的 60 日残差波动率；越低越好。"),
    BETA60("低Beta", "Beta", false, "", "风险", "60 日对沪深300 的 beta；越低越防御。"),
    MAX_RET20("彩票MAX", "MAX", false, "%", "风险", "20 日最大单日涨幅；越低越不彩票化。"),
    SKEW20("偏度", "偏度", false, "", "风险", "20 日收益偏度；越高越彩票化，因此方向为负。"),
    KURT20("峰度", "峰度", false, "", "风险", "20 日收益峰度；越低越稳。"),

    // —— 传统动量/反转（只放因子库，不做主打策略）——
    RET5("5日反转", "5日", false, "%", "反转", "过去 5 个交易日涨幅越低越好；旧因子，作为对照。"),
    RET20("20日动量", "20日", true, "%", "反转", "过去 20 个交易日涨幅越高越好；A 股中期动量偏弱。"),
    RET60("60日动量", "60日", true, "%", "反转", "过去 60 个交易日涨幅，衡量中期趋势。"),
    RET120("120日动量", "120日", true, "%", "反转", "过去 120 个交易日涨幅，接近 6 个月动量。"),
    RET250("长期反转", "250日", false, "%", "反转", "过去 250 个交易日涨幅越低越好；长期反转/价值代理。"),
    RSI14("RSI14", "RSI", false, "", "反转", "14 日 RSI，越低越接近超卖。"),

    // —— 当前估值（仅当前截面，历史回测需估值历史）——
    PE("低PE", "PE", false, "倍", "估值", "当前 PE-TTM，越低越好；历史回测需要估值历史。", true),
    PB("低PB", "PB", false, "倍", "估值", "当前 PB，越低越好；历史回测需要估值历史。", true),
    MARKET_CAP("小市值", "市值", false, "亿", "估值", "当前总市值，越小越好；A 股小市值因子阶段性有效，风险也高。", true),

    // —— 基础属性 ——
    LISTED_DAYS("上市天数", "上市", true, "天", "基础", "上市自然日；越成熟越少次新股波动。可按上市日过滤。"),

    // —— 基本面（最新报告期快照；历史回测前视偏差）——
    ROE("高ROE", "ROE", true, "%", "基本面", "最新报告期加权 ROE；越高盈利能力越强。", true, false, true),
    GROSS_MARGIN("高毛利", "毛利", true, "%", "基本面", "最新报告期毛利率；越高定价能力越强。", true, false, true),
    NET_MARGIN("高净利率", "净利率", true, "%", "基本面", "最新报告期销售净利率；越高费用/成本控制越好。", true, false, true),
    REVENUE_GROWTH("营收增速", "营收", true, "%", "基本面", "最新报告期营业收入同比增速。", true, false, true),
    PROFIT_GROWTH("净利增速", "净利", true, "%", "基本面", "最新报告期归母净利润同比增速。", true, false, true),
    DEBT_RATIO("低负债", "负债率", false, "%", "基本面", "最新报告期资产负债率；越低财务风险越小。", true, false, true),
    EPS("高EPS", "EPS", true, "元", "基本面", "最新报告期基本每股收益。", true, false, true),
    BPS("高BPS", "BPS", true, "元", "基本面", "最新报告期每股净资产。", true, false, true),
    OCFPS("现金流", "每股现金", true, "元", "基本面", "最新报告期每股经营活动现金流；越高盈利质量越好。", true, false, true),
    ;

    companion object {
        val technical: List<FactorKey> = entries.filter { !it.currentOnly }
        val fundamental: List<FactorKey> = entries.filter { it.currentOnly }
        val categories: List<String> = entries.map { it.category }.distinct()
        /** 适合单只 ETF 的时序因子（不含截面/财务/估值/上市天数）。 */
        val etfUsable: List<FactorKey> = entries.filter {
            !it.currentOnly && !it.snapshotOnly && !it.crossSectional &&
                it.category in setOf("趋势", "量价", "风险", "反转")
        }

        /** 只用于判断涨跌方向的 ETF 因子：趋势/反转 + 量比。 */
        val etfDirectional: List<FactorKey> = entries.filter {
            !it.currentOnly && !it.snapshotOnly && !it.crossSectional &&
                (it.category in setOf("趋势", "反转") || it == VOLUME_RATIO)
        }
    }
}
