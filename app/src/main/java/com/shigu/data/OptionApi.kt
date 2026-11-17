package com.shigu.data

import com.shigu.core.DailyBar
import com.shigu.core.OptionContract
import com.shigu.core.OptionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.concurrent.TimeUnit

/**
 * ETF 期权数据：
 * - 合约/快照：东方财富期权列表（m:10，沪市期权）；
 * - 日线：新浪 StockOptionDaylineService JSONP。
 * 只取 510050 / 510300 两个标的。
 */
class OptionApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build(),
) {
    companion object {
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
        private val CODE = Regex("\\d+")
    }

    suspend fun fetchChain(): List<OptionContract> = withContext(Dispatchers.IO) {
        val today = LocalDate.now()
        val out = ArrayList<OptionContract>(800)
        var page = 1
        while (page <= 20) {
            val url = "https://push2.eastmoney.com/api/qt/clist/get?pn=$page&pz=100&po=1&np=1&fltt=2&invt=2" +
                "&fid=f3&fs=m:10&fields=f12,f13,f14,f2,f3,f5,f6,f108,f161,f162"
            val root = JSONObject(get(url, "https://quote.eastmoney.com/"))
            val arr = root.optJSONObject("data")?.optJSONArray("diff") ?: JSONArray()
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                if (o.optInt("f13", 0) != 10) continue
                val code = o.optString("f12")
                if (!code.startsWith("100") || !code.matches(CODE)) continue
                val name = o.optString("f14")
                val underlying = when {
                    name.contains("50ETF") -> "510050"
                    name.contains("300ETF") -> "510300"
                    else -> continue
                }
                val type = when {
                    name.contains("购") -> OptionType.CALL
                    name.contains("沽") -> OptionType.PUT
                    else -> continue
                }
                val strike = o.optDouble("f161", Double.NaN)
                if (strike.isNaN() || strike <= 0) continue
                val remaining = o.optInt("f162", 0)
                val expiry = if (remaining > 0) {
                    today.plusDays(remaining.toLong()).toString()
                } else {
                    expiryFromName(name, today)
                }
                out += OptionContract(
                    code = code,
                    underlying = underlying,
                    name = name,
                    type = type,
                    strike = strike,
                    expiry = expiry,
                    remainingDays = remaining,
                    last = o.optDouble("f2", 0.0),
                    changePct = o.optDouble("f3", 0.0),
                    volume = o.optDouble("f5", 0.0),
                    amount = o.optDouble("f6", 0.0),
                    openInterest = o.optDouble("f108", 0.0),
                    updatedAt = System.currentTimeMillis(),
                )
            }
            if (arr.length() < 100) break
            page++
            Thread.sleep(100L)
        }
        out.sortedWith(compareBy({ it.underlying }, { it.expiry }, { it.type }, { it.strike }))
    }

    suspend fun fetchDaily(code: String, limit: Int = 180): List<DailyBar> = withContext(Dispatchers.IO) {
        val url = "https://stock.finance.sina.com.cn/futures/api/jsonp_v2.php//StockOptionDaylineService.getSymbolInfo" +
            "?symbol=CON_OP_$code"
        val text = get(url, "https://stock.finance.sina.com.cn/option/quotes.html")
        val start = text.indexOf('(')
        val end = text.lastIndexOf(')')
        if (start < 0 || end <= start) return@withContext emptyList()
        val arr = JSONArray(text.substring(start + 1, end))
        val out = ArrayList<DailyBar>(arr.length())
        var prev = Double.NaN
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val date = o.optString("d")
            val open = o.optString("o").toDoubleOrNull() ?: continue
            val high = o.optString("h").toDoubleOrNull() ?: open
            val low = o.optString("l").toDoubleOrNull() ?: open
            val close = o.optString("c").toDoubleOrNull() ?: continue
            val volume = o.optString("v").toDoubleOrNull() ?: 0.0
            val pct = if (!prev.isNaN() && prev > 0) (close / prev - 1.0) * 100.0 else 0.0
            out += DailyBar(
                code = code,
                date = date,
                open = open,
                close = close,
                high = high,
                low = low,
                volume = volume,
                amount = volume * close,
                pct = pct,
                turnover = 0.0,
            )
            prev = close
        }
        out.sortedBy { it.date }.takeLast(limit)
    }

    /** 东方财富前复权日线，用于 5 年期权回测；腾讯接口只给约 641 条。 */
    suspend fun fetchHistory(secid: String, code: String, limit: Int = 1600): List<DailyBar> = withContext(Dispatchers.IO) {
        val url = "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=$secid&klt=101&fqt=1" +
            "&lmt=$limit&end=20500101&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
        val root = JSONObject(get(url, "https://quote.eastmoney.com/"))
        val arr = root.optJSONObject("data")?.optJSONArray("klines") ?: JSONArray()
        val out = ArrayList<DailyBar>(arr.length())
        for (i in 0 until arr.length()) {
            val p = arr.optString(i).split(",")
            if (p.size < 11) continue
            val open = p[1].toDoubleOrNull() ?: continue
            val close = p[2].toDoubleOrNull() ?: continue
            val high = p[3].toDoubleOrNull() ?: close
            val low = p[4].toDoubleOrNull() ?: close
            val hands = p[5].toDoubleOrNull() ?: 0.0
            val amount = p[6].toDoubleOrNull() ?: 0.0
            val pct = p[8].toDoubleOrNull() ?: 0.0
            val turnover = p[10].toDoubleOrNull() ?: 0.0
            out += DailyBar(
                code = code,
                date = p[0],
                open = open,
                close = close,
                high = high,
                low = low,
                volume = hands * 100.0,
                amount = amount,
                pct = pct,
                turnover = turnover,
            )
        }
        out.sortedBy { it.date }
    }

    suspend fun fetchEtfHistory(underlying: String, limit: Int = 1600): List<DailyBar> =
        fetchHistory("1.$underlying", underlying, limit)

    suspend fun fetchIndexHistory(code: String = "000300", limit: Int = 1600): List<DailyBar> =
        fetchHistory("1.$code", code, limit)

    private fun expiryFromName(name: String, today: LocalDate): String {
        val m = Regex("(\\d{1,2})月").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: return ""
        var date = LocalDate.of(today.year, m, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.WEDNESDAY))
        if (date.isBefore(today)) {
            date = LocalDate.of(today.year + 1, m, 1).with(TemporalAdjusters.dayOfWeekInMonth(4, DayOfWeek.WEDNESDAY))
        }
        return date.toString()
    }

    private fun get(url: String, referer: String): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", referer)
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            return resp.body?.string() ?: error("empty body")
        }
    }
}
