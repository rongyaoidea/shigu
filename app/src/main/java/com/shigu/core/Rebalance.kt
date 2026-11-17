package com.shigu.core

import kotlin.math.floor

data class PreviewOrder(
    val code: String,
    val name: String,
    val side: String,
    val shares: Long,
    val price: Double,
    val amount: Double,
    val fee: Double,
    val reason: String,
)

data class RebalancePlan(
    val equity: Double,
    val targetCount: Int,
    val orders: List<PreviewOrder>,
    val buyAmount: Double,
    val sellAmount: Double,
    val estimatedFees: Double,
    val turnoverPct: Double,
)

object RebalanceEstimator {

    /** 按当前目标 TopK、等权、整手，估算下一调仓日需要的买卖清单。 */
    fun estimate(config: StrategyConfig, since: SinceCreationResult): RebalancePlan {
        val equity = 1_000_000.0 * (1.0 + since.returnPct)
        val picks = since.picks
        val holdings = since.portfolio?.holdings.orEmpty()
        val current = holdings.associateBy { it.code }
        val targetCodes = picks.map { it.code }.toSet()
        val targetValue = if (picks.isNotEmpty()) equity / picks.size else 0.0
        val orders = ArrayList<PreviewOrder>()

        picks.forEach { pick ->
            val lot = if (pick.board == Board.STAR) 200L else 100L
            val price = pick.price
            if (price <= 0) return@forEach
            val targetShares = if (targetValue > 0) {
                floor(targetValue / price / lot).toLong() * lot
            } else 0L
            val held = current[pick.code]?.shares ?: 0L
            val delta = targetShares - held
            if (delta > 0) {
                orders += order(config, pick.code, pick.name, "买入", delta, price, "新进/加仓")
            } else if (delta < 0) {
                orders += order(config, pick.code, pick.name, "卖出", -delta, price, "目标减仓")
            }
        }

        holdings.filter { it.code !in targetCodes }.forEach { h ->
            if (h.shares > 0) {
                orders += order(config, h.code, h.name, "卖出", h.shares, h.lastPrice, "跌出目标")
            }
        }

        val buys = orders.filter { it.side == "买入" }
        val sells = orders.filter { it.side == "卖出" }
        val buyAmount = buys.sumOf { it.amount }
        val sellAmount = sells.sumOf { it.amount }
        return RebalancePlan(
            equity = equity,
            targetCount = picks.size,
            orders = sells + buys,
            buyAmount = buyAmount,
            sellAmount = sellAmount,
            estimatedFees = orders.sumOf { it.fee },
            turnoverPct = if (equity > 0) (buyAmount + sellAmount) / (2.0 * equity) else 0.0,
        )
    }

    private fun order(
        config: StrategyConfig,
        code: String,
        name: String,
        side: String,
        shares: Long,
        price: Double,
        reason: String,
    ): PreviewOrder {
        val slip = config.slippageBps / 10_000.0
        val exec = if (side == "买入") price * (1.0 + slip) else price * (1.0 - slip)
        val amount = exec * shares
        val feeBps = if (side == "买入") {
            config.costBps + config.transferFeeBps
        } else {
            config.costBps + config.transferFeeBps + config.stampDutyBps
        }
        return PreviewOrder(
            code = code,
            name = name,
            side = side,
            shares = shares,
            price = exec,
            amount = amount,
            fee = amount * feeBps / 10_000.0,
            reason = reason,
        )
    }
}
