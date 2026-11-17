package com.shigu.core

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow

/**
 * 自定义因子：在现有 47 个因子上做简单算术。
 * 变量名用 FactorKey.name（如 RSRS_RIGHT、VOL20、ROE），也支持常用别名（RSRS、MKT_CAP）。
 * 支持 + - * / ^、括号、abs/sqrt/log/exp/min/max/clip/sign。
 */
data class CustomFactor(
    val id: String,
    val name: String,
    val expression: String,
    val higherIsBetter: Boolean = true,
    val unit: String = "",
    val description: String = "",
)

data class CustomFactorWeight(
    val factor: CustomFactor,
    val weight: Double = 0.2,
)

data class FactorVariable(
    val name: String,
    val label: String,
    val category: String,
)

object FactorVariables {
    private val aliases: Map<String, FactorKey> = buildMap {
        FactorKey.entries.forEach { key ->
            put(key.name.uppercase(), key)
            put(key.shortLabel.uppercase(), key)
        }
        put("MKT_CAP", FactorKey.MARKET_CAP)
        put("MARKETCAP", FactorKey.MARKET_CAP)
        put("MOM20", FactorKey.RET20)
        put("MOM60", FactorKey.RET60)
        put("MOM120", FactorKey.RET120)
        put("TURN", FactorKey.TURNOVER20)
        put("AMT", FactorKey.AMOUNT20)
        put("GROSS", FactorKey.GROSS_MARGIN)
        put("NET", FactorKey.NET_MARGIN)
        put("REV_GROWTH", FactorKey.REVENUE_GROWTH)
        put("PROFIT_GROWTH", FactorKey.PROFIT_GROWTH)
        put("DEBT", FactorKey.DEBT_RATIO)
    }

    val list: List<FactorVariable> = FactorKey.entries.map {
        FactorVariable(it.name, it.label, it.category)
    }

    fun resolve(name: String): FactorKey? = aliases[name.trim().uppercase()]

    fun value(panel: Panel, si: Int, i: Int, name: String): Double? {
        val key = resolve(name) ?: return null
        return FactorEngine.valueAt(panel, si, i, key)
    }
}

sealed interface FormulaExpr {
    fun eval(ctx: (String) -> Double?): Double?
}

private data class NumberExpr(val value: Double) : FormulaExpr {
    override fun eval(ctx: (String) -> Double?): Double = value
}

private data class VariableExpr(val name: String) : FormulaExpr {
    override fun eval(ctx: (String) -> Double?): Double? = ctx(name)
}

private data class UnaryExpr(val negative: Boolean, val child: FormulaExpr) : FormulaExpr {
    override fun eval(ctx: (String) -> Double?): Double? {
        val v = child.eval(ctx) ?: return null
        return if (negative) -v else v
    }
}

private data class BinaryExpr(
    val op: Char,
    val left: FormulaExpr,
    val right: FormulaExpr,
) : FormulaExpr {
    override fun eval(ctx: (String) -> Double?): Double? {
        val a = left.eval(ctx) ?: return null
        val b = right.eval(ctx) ?: return null
        val v = when (op) {
            '+' -> a + b
            '-' -> a - b
            '*' -> a * b
            '/' -> if (kotlin.math.abs(b) < 1e-12) return null else a / b
            '^' -> a.pow(b)
            else -> return null
        }
        return if (v.isFinite()) v else null
    }
}

private data class FunctionExpr(val name: String, val args: List<FormulaExpr>) : FormulaExpr {
    override fun eval(ctx: (String) -> Double?): Double? {
        val values = args.map { it.eval(ctx) ?: return null }
        val v = when (name.lowercase()) {
            "abs" -> if (values.size == 1) kotlin.math.abs(values[0]) else return null
            "sqrt" -> if (values.size == 1 && values[0] >= 0) kotlin.math.sqrt(values[0]) else return null
            "log" -> if (values.size == 1 && values[0] > 0) kotlin.math.ln(values[0]) else return null
            "exp" -> if (values.size == 1) kotlin.math.exp(values[0]) else return null
            "sign" -> if (values.size == 1) kotlin.math.sign(values[0]) else return null
            "min" -> if (values.size >= 2) values.minOrNull() else return null
            "max" -> if (values.size >= 2) values.maxOrNull() else return null
            "clip" -> if (values.size == 3) {
                val lo = minOf(values[1], values[2])
                val hi = maxOf(values[1], values[2])
                values[0].coerceIn(lo, hi)
            } else {
                return null
            }
            else -> return null
        }
        return if (v != null && v.isFinite()) v else null
    }
}

object FormulaParser {
    private val cache = ConcurrentHashMap<String, FormulaExpr>()

    fun parse(expression: String): FormulaExpr {
        val p = Parser(expression)
        val e = p.parseExpression()
        p.skipSpace()
        if (!p.atEnd()) throw IllegalArgumentException("公式有多余字符：${p.rest()}")
        return e
    }

    fun cached(expression: String): FormulaExpr = cache.getOrPut(expression) { parse(expression) }

    fun variables(expression: String): Set<String> {
        val p = Parser(expression)
        p.parseExpression()
        return p.variables
    }

    private class Parser(private val s: String) {
        private var pos = 0
        val variables = LinkedHashSet<String>()

        fun atEnd(): Boolean = pos >= s.length
        fun rest(): String = s.substring(pos).take(20)
        fun skipSpace() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun parseExpression(): FormulaExpr {
            var left = parseTerm()
            while (true) {
                skipSpace()
                val c = peek() ?: break
                if (c != '+' && c != '-') break
                pos++
                left = BinaryExpr(c, left, parseTerm())
            }
            return left
        }

        private fun parseTerm(): FormulaExpr {
            var left = parsePower()
            while (true) {
                skipSpace()
                val c = peek() ?: break
                if (c != '*' && c != '/') break
                pos++
                left = BinaryExpr(c, left, parsePower())
            }
            return left
        }

        private fun parsePower(): FormulaExpr {
            val left = parseUnary()
            skipSpace()
            if (peek() == '^') {
                pos++
                return BinaryExpr('^', left, parsePower())
            }
            return left
        }

        private fun parseUnary(): FormulaExpr {
            skipSpace()
            val c = peek()
            if (c == '+' || c == '-') {
                pos++
                return UnaryExpr(c == '-', parseUnary())
            }
            return parsePrimary()
        }

        private fun parsePrimary(): FormulaExpr {
            skipSpace()
            val c = peek() ?: throw IllegalArgumentException("公式不完整")
            if (c == '(') {
                pos++
                val e = parseExpression()
                skipSpace()
                if (peek() != ')') throw IllegalArgumentException("缺少右括号")
                pos++
                return e
            }
            if (c.isDigit() || c == '.') return parseNumber()
            if (c.isLetter() || c == '_') return parseIdentifierOrCall()
            throw IllegalArgumentException("无法识别字符：$c")
        }

        private fun parseNumber(): FormulaExpr {
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            val text = s.substring(start, pos)
            val v = text.toDoubleOrNull() ?: throw IllegalArgumentException("数字格式错误：$text")
            return NumberExpr(v)
        }

        private fun parseIdentifierOrCall(): FormulaExpr {
            val start = pos
            while (pos < s.length && (s[pos].isLetterOrDigit() || s[pos] == '_')) pos++
            val name = s.substring(start, pos)
            skipSpace()
            if (peek() != '(') {
                variables += name
                return VariableExpr(name)
            }
            pos++
            val args = ArrayList<FormulaExpr>()
            skipSpace()
            if (peek() == ')') {
                pos++
                return FunctionExpr(name, args)
            }
            while (true) {
                args += parseExpression()
                skipSpace()
                when (peek()) {
                    ',' -> pos++
                    ')' -> {
                        pos++
                        break
                    }
                    else -> throw IllegalArgumentException("函数参数缺少逗号或右括号")
                }
            }
            return FunctionExpr(name, args)
        }

        private fun peek(): Char? {
            skipSpace()
            return if (pos < s.length) s[pos] else null
        }
    }
}

object CustomFactorEngine {
    fun value(panel: Panel, si: Int, i: Int, factor: CustomFactor): Double? {
        if (factor.expression.isBlank()) return null
        val expr = runCatching { FormulaParser.cached(factor.expression) }.getOrNull() ?: return null
        return runCatching {
            expr.eval { name -> FactorVariables.value(panel, si, i, name) }
        }.getOrNull()
    }

    fun validate(expression: String): String? {
        return runCatching {
            val vars = FormulaParser.variables(expression)
            val unknown = vars.filter { FactorVariables.resolve(it) == null }
            if (unknown.isNotEmpty()) "未知变量：${unknown.joinToString(", ")}" else null
        }.getOrElse { it.message ?: "公式语法错误" }
    }
}
