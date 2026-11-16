package com.shigu.core

/**
 * 企业性质推断。
 *
 * 数据源：东方财富 F10 的“实际控制人” + 第一大股东名称/类型。
 * 这是一个**启发式分类**，不是交易所或官方标签：
 * - 实控人名称里出现国务院/中央企业/中央金融关键词 → 央企；
 * - 出现地方国资/财政/政府/城投等关键词 → 地方国企；
 * - 自然人 → 民企；
 * - “无”/空 → 结合第一大股东判断，基金/保险/信托/券商/托管账户 → 无实控人；
 * - 其余公司 → 民企（无法确认时宁可保守，不冒充国企）。
 *
 * 详情页会同时展示原始实控人和第一大股东，方便人工复核。
 */
object OwnershipClassifier {

    private val CENTRAL_KEYWORDS = listOf(
        "国务院", "中华人民共和国财政部", "财政部", "中央汇金", "中国国新", "中国诚通",
        "国家开发投资", "国家能源投资", "国家电力投资", "国家集成电路产业投资基金",
        "全国社会保障基金", "中国证券金融", "国家外汇管理局", "中国投资有限责任公司",
        "中国国家铁路", "中国邮政", "中国烟草", "中国石油", "中国石化", "中国海油",
        "国家电网", "南方电网", "中国华能", "中国大唐", "中国华电", "国家能源集团",
        "中国三峡", "中国长江三峡", "中国核工业", "中国广核", "中国中化", "中粮",
        "招商局", "华润", "中国保利", "中国中车", "中国中铁", "中国铁建", "中国交建",
        "中国建筑", "中国电建", "中国能建", "中国移动", "中国电信", "中国联通",
        "中国电子科技", "中国电子信息", "中国航空工业", "中国航天", "中国航空发动机",
        "中国船舶", "中国兵器", "中国五矿", "中国铝业", "中国宝武", "鞍钢",
        "中国建材", "中国医药", "中国机械工业", "中国中信", "中国光大", "中国通用技术",
        "中国农业发展", "中国林业", "中国旅游", "华侨城", "南光", "中国节能",
        "中国有色", "中国稀土", "中国黄金", "中国盐业", "中国储备粮", "中国中煤",
        "中国煤炭科工", "中国化学工程", "中国安能", "中国物流", "中国国检",
        "中国航空油料", "中国航空器材", "中国科学院", "中国工程物理研究院",
        "中华全国供销合作总社", "中国国投", "中国电子", "中国北方工业", "中国盐湖",
        "中国电气装备", "中国商飞", "中国融通", "中国检验认证", "中国国际工程咨询",
        "中国建设科技", "中国宝原", "中国中检", "中国航发",
    )

    /** 地方国资/政府平台常见词。中央关键词优先匹配。 */
    private val LOCAL_KEYWORDS = listOf(
        "国有资产监督管理", "国有资产管理", "国有资产经营", "国有资本", "国资委",
        "财政厅", "财政局", "人民政府", "管理委员会", "管委会", "省委宣传部", "宣传部",
        "大学", "报业集团", "广播电视", "地铁集团", "城投", "交投", "建投", "产投",
        "国投", "建设投资", "产业投资", "金融控股", "能源集团", "水务集团", "港口集团",
        "高速集团", "交通集团", "供销合作总社",
        // 常见地方国企集团（避免把“深圳市科汇通投资控股”这类民企误判为国企）
        "深圳市投资控股", "深圳市地铁", "深圳能源", "深圳港", "深业", "特发", "赛格",
        "深投控", "北京电子控股", "北京国有资本运营", "上海国有资本投资", "上海国际集团",
        "天津泰达投资控股", "天津津联", "重庆渝富", "广东省广晟", "广州越秀", "广州金控",
        "珠海华发", "珠海港控股", "江苏国信", "江苏交通控股", "浙江省能源", "浙江交通投资",
        "山东高速", "山东能源", "山东重工", "晋能控股", "山西焦煤", "河南投资", "湖北宏泰",
        "湖南财信", "四川发展", "云南能投", "贵州黔晟", "甘肃国投", "青海国投",
        "宁夏国有资本", "新疆投资发展", "河北建投", "福建投资", "厦门国贸", "安徽国元",
        "江西建材", "广西投资", "北部湾国际港务", "陕西投资", "陕西煤业化工", "青岛华通",
        "青岛国信", "烟台国丰", "无锡产业发展", "苏州创元", "常州投资", "合肥建设投资",
        "南京公用", "武汉金融控股", "长沙通程", "成都环境投资", "成都高新投资", "西安曲江",
        "曲江新区", "雄安集团", "浦东投资", "张江集团", "张江高科", "临港", "上实", "光明食品", "百联",
        "锦江", "东浩兰生", "申通地铁", "久事", "海信", "厦大",
    )

    private val FOREIGN_KEYWORDS = listOf(
        "香港", "开曼", "维尔京", "百慕大", "新加坡", "美国", "日本", "韩国", "台湾",
        "英国", "德国", "法国", "瑞士", "荷兰", "加拿大", "澳大利亚", "意大利", "西班牙",
        "瑞典", "丹麦", "挪威", "芬兰", "爱尔兰", "卢森堡", "马来西亚", "印度",
        "阿联酋", "沙特", "以色列", "巴西", "俄罗斯", "国际金融公司", "嘉士伯", "外资", "外商",
    )

    private val NOMINEE_KEYWORDS = listOf(
        "香港中央结算", "HKSCC", "中央结算", "证券账户", "破产企业财产处置专用账户",
        "客户信用交易担保证券账户", "专用账户",
    )

    private val FUND_TYPE_KEYWORDS = listOf(
        "基金", "保险", "信托", "证券", "私募", "QFII", "社保", "年金", "理财",
        "资产管理", "银行",
    )

    private val NO_CONTROLLER_VALUES = setOf("", "无", "无实际控制人", "-", "—", "none", "null")

    /** 实控人名称以“中国”开头且像集团/总公司时，默认按央企处理；这些是已知非央企的例外。 */
    private val CENTRAL_PREFIX_EXCEPTIONS = listOf(
        "中国平安", "中国宝安", "中国泛海", "中国民生", "中国恒大", "中国奥园", "中国忠旺",
        "中国高速传动", "中国生物制药", "中国旺旺", "中国飞鹤", "中国联塑", "中国泰康",
        "中国太保", "中国财险", "中国海外发展", "中国金茂", "中国海外宏洋",
    )

    fun classify(actualHolder: String?, firstHolder: String?, firstHolderType: String?): OwnerType {
        val actual = actualHolder.orEmpty().trim()
        if (actual.isNotEmpty() && actual.lowercase() !in NO_CONTROLLER_VALUES) {
            if (isNominee(actual)) return OwnerType.NO_CONTROLLER
            return classifyName(actual) ?: OwnerType.PRIVATE
        }

        val first = firstHolder.orEmpty().trim()
        if (first.isEmpty()) return OwnerType.UNKNOWN
        if (isNominee(first)) return OwnerType.NO_CONTROLLER
        if (isFundLike(firstHolderType)) return OwnerType.NO_CONTROLLER
        if (first.contains("合伙企业")) return OwnerType.NO_CONTROLLER

        return classifyName(first) ?: OwnerType.PRIVATE
    }

    private fun classifyName(raw: String): OwnerType? {
        val name = raw.trim()
        if (name.isEmpty()) return null
        if (CENTRAL_KEYWORDS.any { name.contains(it) }) return OwnerType.CENTRAL_SOE
        if (LOCAL_KEYWORDS.any { name.contains(it) }) return OwnerType.LOCAL_SOE
        // 自然人要放在“国家/地区关键词”之前：韩国龙、张江波这类姓名不应被判成外企/地方国企。
        if (isPersonLike(name)) return OwnerType.PRIVATE
        if (FOREIGN_KEYWORDS.any { name.contains(it) }) return OwnerType.FOREIGN
        if (isCentralCompanyPrefix(name)) return OwnerType.CENTRAL_SOE
        return null
    }

    private fun isCentralCompanyPrefix(name: String): Boolean {
        if (!name.startsWith("中国")) return false
        if (CENTRAL_PREFIX_EXCEPTIONS.any { name.contains(it) }) return false
        return name.contains("集团") || name.contains("总公司") || name.contains("有限公司")
    }

    private fun isPersonLike(raw: String): Boolean {
        val parts = raw.split(',', '，', '、', ';', '；').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return false
        return parts.all { p ->
            p.length in 2..5 && p.all { it in '\u4e00'..'\u9fa5' || it == '·' }
        }
    }

    private fun isNominee(name: String): Boolean = NOMINEE_KEYWORDS.any { name.contains(it, ignoreCase = true) }

    private fun isFundLike(type: String?): Boolean {
        val t = type.orEmpty().trim()
        if (t.isEmpty()) return false
        return FUND_TYPE_KEYWORDS.any { t.contains(it) }
    }
}
