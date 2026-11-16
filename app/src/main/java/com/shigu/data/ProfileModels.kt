package com.shigu.data

import com.shigu.core.OwnerType
import com.shigu.core.OwnershipClassifier

data class OrgProfile(
    val code: String,
    val name: String,
    val listedDate: String,
    val actualHolder: String,
)

data class HolderProfile(
    val code: String,
    val holderName: String,
    val holderType: String,
    val holdRatio: Double?,
)

data class FinanceProfile(
    val code: String,
    val reportDate: String,
    val roe: Double?,
    val grossMargin: Double?,
    val netMargin: Double?,
    val revenueGrowth: Double?,
    val profitGrowth: Double?,
    val debtRatio: Double?,
    val eps: Double?,
    val bps: Double?,
    val ocfps: Double?,
)

/** 数据库里只存合并后的基础资料；原始实控人/第一大股东也保留，便于详情页复核。 */
data class StockProfile(
    val code: String,
    val ownerType: OwnerType,
    val listedDate: String,
    val actualHolder: String,
    val firstHolder: String,
    val firstHolderType: String,
    val firstHoldRatio: Double?,
    val reportDate: String,
    val roe: Double?,
    val grossMargin: Double?,
    val netMargin: Double?,
    val revenueGrowth: Double?,
    val profitGrowth: Double?,
    val debtRatio: Double?,
    val eps: Double?,
    val bps: Double?,
    val ocfps: Double?,
)

object ProfileAssembler {
    fun assemble(
        orgs: List<OrgProfile>,
        holders: Map<String, HolderProfile>,
        finances: Map<String, FinanceProfile>,
    ): List<StockProfile> {
        val orgByCode = orgs.associateBy { it.code }
        val codes = LinkedHashSet<String>(orgs.size + finances.size)
        orgs.forEach { codes += it.code }
        holders.keys.forEach { codes += it }
        finances.keys.forEach { codes += it }

        return codes.map { code ->
            val org = orgByCode[code]
            val holder = holders[code]
            val fin = finances[code]
            StockProfile(
                code = code,
                ownerType = OwnershipClassifier.classify(
                    actualHolder = org?.actualHolder,
                    firstHolder = holder?.holderName,
                    firstHolderType = holder?.holderType,
                ),
                listedDate = org?.listedDate?.take(10).orEmpty(),
                actualHolder = org?.actualHolder.orEmpty(),
                firstHolder = holder?.holderName.orEmpty(),
                firstHolderType = holder?.holderType.orEmpty(),
                firstHoldRatio = holder?.holdRatio,
                reportDate = fin?.reportDate?.take(10).orEmpty(),
                roe = fin?.roe,
                grossMargin = fin?.grossMargin,
                netMargin = fin?.netMargin,
                revenueGrowth = fin?.revenueGrowth,
                profitGrowth = fin?.profitGrowth,
                debtRatio = fin?.debtRatio,
                eps = fin?.eps,
                bps = fin?.bps,
                ocfps = fin?.ocfps,
            )
        }
    }
}
