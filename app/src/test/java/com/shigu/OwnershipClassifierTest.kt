package com.shigu

import com.shigu.core.OwnerType
import com.shigu.core.OwnershipClassifier
import com.shigu.core.StStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OwnershipClassifierTest {

    @Test
    fun `央企与地方国企按名称关键词区分`() {
        assertEquals(OwnerType.CENTRAL_SOE, OwnershipClassifier.classify("国务院国有资产监督管理委员会", null, null))
        assertEquals(OwnerType.CENTRAL_SOE, OwnershipClassifier.classify("中国华润有限公司", null, null))
        assertEquals(OwnerType.CENTRAL_SOE, OwnershipClassifier.classify("中国科学院控股有限公司", null, null))
        assertEquals(OwnerType.LOCAL_SOE, OwnershipClassifier.classify("上海市国有资产监督管理委员会", null, null))
        assertEquals(OwnerType.LOCAL_SOE, OwnershipClassifier.classify("北京电子控股有限责任公司", null, null))
        assertEquals(OwnerType.LOCAL_SOE, OwnershipClassifier.classify("深圳市投资控股有限公司", null, null))
    }

    @Test
    fun `自然人与民营公司归为民企`() {
        assertEquals(OwnerType.PRIVATE, OwnershipClassifier.classify("何享健", null, null))
        assertEquals(OwnerType.PRIVATE, OwnershipClassifier.classify("丁芃,郑列列", null, null))
        assertEquals(OwnerType.PRIVATE, OwnershipClassifier.classify("深圳市科汇通投资控股有限公司", null, null))
        assertEquals(OwnerType.PRIVATE, OwnershipClassifier.classify("中国平安保险(集团)股份有限公司", null, null))
    }

    @Test
    fun `无实控人时结合第一大股东判断`() {
        assertEquals(
            OwnerType.LOCAL_SOE,
            OwnershipClassifier.classify("无", "深圳市地铁集团有限公司", "其它"),
        )
        assertEquals(
            OwnerType.NO_CONTROLLER,
            OwnershipClassifier.classify("无", "香港中央结算(代理人)有限公司", "其它"),
        )
        assertEquals(
            OwnerType.NO_CONTROLLER,
            OwnershipClassifier.classify("无", "中国平安保险(集团)股份有限公司-集团本级-自有资金", "保险公司"),
        )
        assertEquals(
            OwnerType.PRIVATE,
            OwnershipClassifier.classify("无", "林文杰", "个人"),
        )
    }

    @Test
    fun `自然人姓名不应被地区关键词误判`() {
        assertEquals(OwnerType.PRIVATE, OwnershipClassifier.classify("韩国龙,薛黎曦", null, null))
        assertEquals(OwnerType.PRIVATE, OwnershipClassifier.classify("张江波,张江平", null, null))
        assertEquals(
            OwnerType.PRIVATE,
            OwnershipClassifier.classify("无", "江苏国泰国际贸易有限公司", "其它"),
        )
    }

    @Test
    fun `外资与未知`() {
        assertEquals(OwnerType.FOREIGN, OwnershipClassifier.classify("嘉士伯基金会", null, null))
        assertEquals(OwnerType.UNKNOWN, OwnershipClassifier.classify("", null, null))
    }

    @Test
    fun `ST 状态从简称解析`() {
        assertEquals(StStatus.NORMAL, StStatus.fromName("平安银行"))
        assertEquals(StStatus.ST, StStatus.fromName("ST康佳A"))
        assertEquals(StStatus.STAR_ST, StStatus.fromName("*ST康佳A"))
        assertEquals(StStatus.DELISTING, StStatus.fromName("康佳退"))
    }
}
