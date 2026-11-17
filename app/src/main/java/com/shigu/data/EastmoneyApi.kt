package com.shigu.data

import com.shigu.core.Board
import com.shigu.core.DailyBar
import com.shigu.core.StStatus
import com.shigu.core.Stock
import com.shigu.core.ValuationPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.YearMonth
import java.util.concurrent.TimeUnit

class EastmoneyApi(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(listOf(Protocol.HTTP_1_1))
        .build(),
) {
    @Volatile
    private var useUrlConnection = false

    @Volatile
    private var cachedReportDate: String? = null

    companion object {
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
    }

    suspend fun fetchStockList(): List<Stock> = withContext(Dispatchers.IO) {
        val out = ArrayList<Stock>(6000)
        val seen = HashSet<String>(6000)
        var page = 1
        while (page <= 90) {
            val url = "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/" +
                "Market_Center.getHQNodeData?page=$page&num=100&sort=symbol&asc=1&node=hs_a&symbol=&_s_r_a=page"
            val arr = JSONArray(get(url, referer = "https://finance.sina.com.cn/"))
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val code = o.optString("code")
                if (!code.matches(Regex("\\d{6}")) || code in seen) continue
                val name = o.optString("name").trim()
                if (name.isEmpty()) continue
                val symbol = o.optString("symbol").ifBlank {
                    if (code.startsWith("6")) "sh$code" else "sz$code"
                }
                val board = boardOf(code, if (symbol.startsWith("sh")) 1 else 0)
                if (board == Board.INDEX) continue
                seen += code
                out += Stock(
                    code = code,
                    name = name,
                    secid = symbol,
                    board = board,
                    price = o.optString("trade").toDoubleOrNull() ?: 0.0,
                    changePct = o.optString("changepercent").toDoubleOrNull() ?: 0.0,
                    open = o.optString("open").toDoubleOrNull() ?: 0.0,
                    high = o.optString("high").toDoubleOrNull() ?: 0.0,
                    low = o.optString("low").toDoubleOrNull() ?: 0.0,
                    prevClose = o.optString("settlement").toDoubleOrNull() ?: 0.0,
                    volume = o.optString("volume").toDoubleOrNull() ?: 0.0,
                    amount = o.optString("amount").toDoubleOrNull() ?: 0.0,
                    turnover = o.optString("turnoverratio").toDoubleOrNull() ?: 0.0,
                    pe = o.optString("per").toDoubleOrNull(),
                    pb = o.optString("pb").toDoubleOrNull(),
                    marketCap = (o.optString("mktcap").toDoubleOrNull() ?: 0.0) * 10_000.0,
                    floatCap = (o.optString("nmc").toDoubleOrNull() ?: 0.0) * 10_000.0,
                    volumeRatio = null,
                    change60 = null,
                    changeYtd = null,
                    stStatus = StStatus.fromName(name),
                    suspended = isSuspended(o),
                    updatedAt = System.currentTimeMillis(),
                )
            }
            page++
            delay(60)
        }

        // ETF 池：新浪 etf_hq_fund（约 1000+ 只）。RSRS/趋势同样可应用。
        var etfPage = 1
        while (etfPage <= 40) {
            val url = "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/" +
                "Market_Center.getHQNodeData?page=$etfPage&num=100&sort=symbol&asc=1&node=etf_hq_fund&symbol=&_s_r_a=page"
            val arr = JSONArray(get(url, referer = "https://finance.sina.com.cn/"))
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val code = o.optString("code")
                if (!code.matches(Regex("\\d{6}")) || code in seen) continue
                val name = o.optString("name").trim()
                if (name.isEmpty()) continue
                // 股票策略的 ETF 池剔除货币/短融/债券型，避免平坦序列占满 TopK。
                if (listOf("保证金", "货币", "短融", "日利", "债").any { name.contains(it) }) continue
                val symbol = o.optString("symbol").ifBlank {
                    if (code.startsWith("5")) "sh$code" else "sz$code"
                }
                seen += code
                out += Stock(
                    code = code,
                    name = name,
                    secid = symbol,
                    board = Board.ETF,
                    price = o.optString("trade").toDoubleOrNull() ?: 0.0,
                    changePct = o.optString("changepercent").toDoubleOrNull() ?: 0.0,
                    open = o.optString("open").toDoubleOrNull() ?: 0.0,
                    high = o.optString("high").toDoubleOrNull() ?: 0.0,
                    low = o.optString("low").toDoubleOrNull() ?: 0.0,
                    prevClose = o.optString("settlement").toDoubleOrNull() ?: 0.0,
                    volume = o.optString("volume").toDoubleOrNull() ?: 0.0,
                    amount = o.optString("amount").toDoubleOrNull() ?: 0.0,
                    turnover = o.optString("turnoverratio").toDoubleOrNull() ?: 0.0,
                    pe = o.optString("per").toDoubleOrNull(),
                    pb = o.optString("pb").toDoubleOrNull(),
                    marketCap = (o.optString("mktcap").toDoubleOrNull() ?: 0.0) * 10_000.0,
                    floatCap = (o.optString("nmc").toDoubleOrNull() ?: 0.0) * 10_000.0,
                    volumeRatio = null,
                    change60 = null,
                    changeYtd = null,
                    stStatus = StStatus.NORMAL,
                    suspended = isSuspended(o),
                    updatedAt = System.currentTimeMillis(),
                )
            }
            etfPage++
            delay(60)
        }

        // 北交所：新浪 hs_bjs；腾讯 fqkline 不支持 bj，日线走新浪不复权兜底。
        var bjPage = 1
        while (bjPage <= 10) {
            val url = "https://vip.stock.finance.sina.com.cn/quotes_service/api/json_v2.php/" +
                "Market_Center.getHQNodeData?page=$bjPage&num=100&sort=symbol&asc=1&node=hs_bjs&symbol=&_s_r_a=page"
            val arr = JSONArray(get(url, referer = "https://finance.sina.com.cn/"))
            if (arr.length() == 0) break
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val code = o.optString("code")
                if (!code.matches(Regex("\\d{6}")) || code in seen) continue
                val name = o.optString("name").trim()
                if (name.isEmpty()) continue
                val symbol = o.optString("symbol").ifBlank { "bj$code" }
                seen += code
                out += Stock(
                    code = code,
                    name = name,
                    secid = symbol,
                    board = Board.BSE,
                    price = o.optString("trade").toDoubleOrNull() ?: 0.0,
                    changePct = o.optString("changepercent").toDoubleOrNull() ?: 0.0,
                    open = o.optString("open").toDoubleOrNull() ?: 0.0,
                    high = o.optString("high").toDoubleOrNull() ?: 0.0,
                    low = o.optString("low").toDoubleOrNull() ?: 0.0,
                    prevClose = o.optString("settlement").toDoubleOrNull() ?: 0.0,
                    volume = o.optString("volume").toDoubleOrNull() ?: 0.0,
                    amount = o.optString("amount").toDoubleOrNull() ?: 0.0,
                    turnover = o.optString("turnoverratio").toDoubleOrNull() ?: 0.0,
                    pe = o.optString("per").toDoubleOrNull(),
                    pb = o.optString("pb").toDoubleOrNull(),
                    marketCap = (o.optString("mktcap").toDoubleOrNull() ?: 0.0) * 10_000.0,
                    floatCap = (o.optString("nmc").toDoubleOrNull() ?: 0.0) * 10_000.0,
                    volumeRatio = null,
                    change60 = null,
                    changeYtd = null,
                    stStatus = StStatus.fromName(name),
                    suspended = isSuspended(o),
                    updatedAt = System.currentTimeMillis(),
                )
            }
            bjPage++
            delay(60)
        }

        out.sortByDescending { it.marketCap ?: 0.0 }
        out
    }

    /**
     * 腾讯日线（前复权）。qfqday 只给 OHLCV：
     * - volume 单位是手，统一乘 100 变股；
     * - amount 用 volume × close 估算（没有成交额字段）；
     * - turnover 用当前流通股本估算（floatShares 由 Sina 的 nmc / price 得到）。
     */
    suspend fun fetchDailyBars(
        secid: String,
        limit: Int,
        floatShares: Double = 0.0,
    ): List<DailyBar> = withContext(Dispatchers.IO) {
        val symbol = tencentSymbol(secid)
        val code = symbol.removePrefix("sh").removePrefix("sz").removePrefix("bj")
        if (symbol.startsWith("bj")) {
            return@withContext fetchSinaDailyBars(symbol, code, limit, floatShares)
        }
        val url = "https://web.ifzq.gtimg.cn/appstock/app/fqkline/get?param=$symbol,day,,,$limit,qfq"
        val root = JSONObject(get(url, referer = "https://gu.qq.com/"))
        val node = root.optJSONObject("data")?.optJSONObject(symbol) ?: return@withContext emptyList()
        val arr = node.optJSONArray("qfqday") ?: node.optJSONArray("day") ?: JSONArray()
        val out = ArrayList<DailyBar>(arr.length())
        var prevClose = Double.NaN
        for (i in 0 until arr.length()) {
            val p = arr.optJSONArray(i) ?: continue
            if (p.length() < 6) continue
            val date = p.optString(0)
            val open = p.optString(1).toDoubleOrNull() ?: continue
            val close = p.optString(2).toDoubleOrNull() ?: continue
            val high = p.optString(3).toDoubleOrNull() ?: close
            val low = p.optString(4).toDoubleOrNull() ?: close
            val hands = p.optString(5).toDoubleOrNull() ?: 0.0
            val shares = hands * 100.0
            val pct = if (!prevClose.isNaN() && prevClose > 0) (close / prevClose - 1.0) * 100.0 else 0.0
            val turnover = if (floatShares > 0) shares / floatShares * 100.0 else 0.0
            out += DailyBar(
                code = code,
                date = date,
                open = open,
                close = close,
                high = high,
                low = low,
                volume = shares,
                amount = shares * close,
                pct = pct,
                turnover = turnover,
            )
            prevClose = close
        }
        out.sortBy { it.date }
        out
    }

    suspend fun fetchBenchmark(limit: Int = 700): List<DailyBar> {
        val tencent = runCatching { fetchDailyBars("sh000300", limit) }.getOrNull()
        val bars = if (!tencent.isNullOrEmpty()) {
            tencent
        } else {
            // 基准指数没有复权问题，腾讯失败时用新浪兜底，避免整个同步因基准失败。
            runCatching { fetchSinaDailyBars("sh000300", ShiguDb.BENCHMARK, limit, 0.0) }.getOrDefault(emptyList())
        }
        return bars.map { it.copy(code = ShiguDb.BENCHMARK) }
    }

    private fun fetchSinaDailyBars(
        symbol: String,
        code: String,
        limit: Int,
        floatShares: Double,
    ): List<DailyBar> {
        val url = "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/" +
            "CN_MarketData.getKLineData?symbol=$symbol&scale=240&ma=no&datalen=$limit"
        val arr = JSONArray(get(url, referer = "https://finance.sina.com.cn/"))
        val out = ArrayList<DailyBar>(arr.length())
        var prev = Double.NaN
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val date = o.optString("day")
            val open = o.optString("open").toDoubleOrNull() ?: continue
            val close = o.optString("close").toDoubleOrNull() ?: continue
            val high = o.optString("high").toDoubleOrNull() ?: close
            val low = o.optString("low").toDoubleOrNull() ?: close
            val volume = o.optString("volume").toDoubleOrNull() ?: 0.0
            val pct = if (!prev.isNaN() && prev > 0) (close / prev - 1.0) * 100.0 else 0.0
            val turnover = if (floatShares > 0) volume / floatShares * 100.0 else 0.0
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
                turnover = turnover,
            )
            prev = close
        }
        return out
    }

    /**
     * 东方财富估值历史：PE_TTM / PB_MRQ / 总市值 / 收盘价。
     * 1000 条约 4 年；只取第一页。
     */
    suspend fun fetchValuationHistory(code: String, pageSize: Int = 1000): List<ValuationPoint> =
        withContext(Dispatchers.IO) {
            val url = "https://datacenter-web.eastmoney.com/api/data/v1/get?reportName=RPT_VALUEANALYSIS_DET" +
                "&columns=ALL&filter=(SECURITY_CODE%3D%22$code%22)&pageNumber=1&pageSize=$pageSize" +
                "&sortColumns=TRADE_DATE&sortTypes=-1"
            val root = JSONObject(get(url, referer = "https://data.eastmoney.com/"))
            val arr = root.optJSONObject("result")?.optJSONArray("data") ?: JSONArray()
            val out = ArrayList<ValuationPoint>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val date = o.optString("TRADE_DATE").take(10)
                if (date.length != 10) continue
                out += ValuationPoint(
                    code = code,
                    date = date,
                    pe = o.d("PE_TTM"),
                    pb = o.d("PB_MRQ"),
                    marketCap = o.d("TOTAL_MARKET_CAP"),
                    close = o.d("CLOSE_PRICE"),
                )
            }
            out.sortBy { it.date }
            out
        }

    /**
     * 基础资料同步：东方财富 F10 批量接口。
     * 一次 500 条分页，A 股约 17 页；比逐只请求轻很多。
     */
    suspend fun fetchOrgProfiles(
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<OrgProfile> = withContext(Dispatchers.IO) {
        val out = ArrayList<OrgProfile>(8500)
        fetchDataPages(
            reportName = "RPT_F10_BASIC_ORGINFO",
            columns = "SECUCODE,SECURITY_CODE,SECURITY_NAME_ABBR,LISTING_DATE,ACTUAL_HOLDER",
            filter = "(SECURITY_TYPE_CODE=\"058001001\")",
            sortColumns = "SECURITY_CODE",
            onProgress = onProgress,
        ) { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val code = o.optString("SECURITY_CODE")
                if (!code.matches(Regex("\\d{6}"))) continue
                out += OrgProfile(
                    code = code,
                    name = o.optString("SECURITY_NAME_ABBR", ""),
                    listedDate = o.optString("LISTING_DATE", "").take(10),
                    actualHolder = o.optString("ACTUAL_HOLDER", "").trim(),
                )
            }
        }
        out
    }

    /** 找最近一个已经披露、且覆盖足够多股票的财报期（优先一季报/中报/三季报/年报）。 */
    suspend fun resolveReportDate(): String = withContext(Dispatchers.IO) {
        cachedReportDate?.let { return@withContext it }
        val today = LocalDate.now()
        val candidates = ArrayList<String>(8)
        var year = today.year
        var quarter = 4
        while (candidates.size < 8) {
            val month = quarter * 3
            val date = LocalDate.of(year, month, YearMonth.of(year, month).lengthOfMonth())
            if (!date.isAfter(today)) candidates += date.toString()
            quarter--
            if (quarter == 0) {
                quarter = 4
                year--
            }
        }
        for (date in candidates) {
            val count = reportCount("RPT_F10_FINANCE_MAINFINADATA", "(REPORT_DATE='$date')")
            if (count >= 1000) {
                cachedReportDate = date
                return@withContext date
            }
        }
        (candidates.firstOrNull() ?: "").also { cachedReportDate = it }
    }

    suspend fun fetchFinanceProfiles(
        reportDate: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): List<FinanceProfile> = withContext(Dispatchers.IO) {
        val out = ArrayList<FinanceProfile>(7000)
        fetchDataPages(
            reportName = "RPT_F10_FINANCE_MAINFINADATA",
            columns = "SECUCODE,SECURITY_CODE,SECURITY_NAME_ABBR,REPORT_DATE,NOTICE_DATE,ROEJQ,XSMLL,XSJLL,TOTALOPERATEREVETZ,PARENTNETPROFITTZ,ZCFZL,EPSJB,BPS,MGJYXJJE",
            filter = "(REPORT_DATE='$reportDate')",
            sortColumns = "SECURITY_CODE",
            onProgress = onProgress,
        ) { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val code = o.optString("SECURITY_CODE")
                if (!code.matches(Regex("\\d{6}"))) continue
                out += FinanceProfile(
                    code = code,
                    reportDate = o.optString("REPORT_DATE", reportDate).take(10),
                    noticeDate = o.optString("NOTICE_DATE", "").take(10),
                    roe = o.d("ROEJQ"),
                    grossMargin = o.d("XSMLL"),
                    netMargin = o.d("XSJLL"),
                    revenueGrowth = o.d("TOTALOPERATEREVETZ"),
                    profitGrowth = o.d("PARENTNETPROFITTZ"),
                    debtRatio = o.d("ZCFZL"),
                    eps = o.d("EPSJB"),
                    bps = o.d("BPS"),
                    ocfps = o.d("MGJYXJJE"),
                )
            }
        }
        out
    }

    suspend fun fetchHolderProfiles(
        reportDate: String,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): Map<String, HolderProfile> = withContext(Dispatchers.IO) {
        val out = HashMap<String, HolderProfile>(7000)
        fetchDataPages(
            reportName = "RPT_F10_EH_FREEHOLDERS",
            columns = "SECUCODE,SECURITY_CODE,END_DATE,HOLDER_NAME,HOLDER_TYPE,HOLD_RATIO,HOLDER_RANK",
            filter = "(HOLDER_RANK=1)(END_DATE='$reportDate')",
            sortColumns = "SECURITY_CODE",
            onProgress = onProgress,
        ) { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val code = o.optString("SECURITY_CODE")
                if (!code.matches(Regex("\\d{6}")) || code in out) continue
                out[code] = HolderProfile(
                    code = code,
                    holderName = o.optString("HOLDER_NAME", "").trim(),
                    holderType = o.optString("HOLDER_TYPE", "").trim(),
                    holdRatio = o.d("HOLD_RATIO"),
                )
            }
        }
        out
    }

    private fun reportCount(reportName: String, filter: String): Long {
        val url = buildDataUrl(reportName, "SECURITY_CODE", filter, "SECURITY_CODE", 1, 1)
        val root = JSONObject(get(url, referer = "https://emweb.securities.eastmoney.com/"))
        return root.optJSONObject("result")?.optLong("count", 0L) ?: 0L
    }

    private fun fetchDataPages(
        reportName: String,
        columns: String,
        filter: String,
        sortColumns: String,
        onProgress: (done: Int, total: Int) -> Unit,
        onPage: (JSONArray) -> Unit,
    ) {
        var page = 1
        var pages = 1
        var rows = 0
        while (page <= pages && page <= 80) {
            val url = buildDataUrl(reportName, columns, filter, sortColumns, page, 500)
            val root = JSONObject(get(url, referer = "https://emweb.securities.eastmoney.com/"))
            val result = root.optJSONObject("result") ?: break
            pages = result.optInt("pages", 1).coerceAtLeast(1)
            val arr = result.optJSONArray("data") ?: JSONArray()
            onPage(arr)
            rows += arr.length()
            onProgress(rows, pages * 500)
            if (arr.length() == 0) break
            page++
            Thread.sleep(120L)
        }
    }

    private fun buildDataUrl(
        reportName: String,
        columns: String,
        filter: String,
        sortColumns: String,
        page: Int,
        pageSize: Int,
    ): String {
        val encodedFilter = URLEncoder.encode(filter, "UTF-8")
        return "https://datacenter.eastmoney.com/securities/api/data/v1/get" +
            "?reportName=$reportName&columns=$columns&filter=$encodedFilter" +
            "&pageNumber=$page&pageSize=$pageSize&sortColumns=$sortColumns&sortTypes=1"
    }

    private fun get(url: String, referer: String = "https://quote.eastmoney.com/"): String {
        if (!useUrlConnection) {
            try {
                return getOkHttp(url, referer)
            } catch (_: Exception) {
                // 某些网络/HTTP2 组合下 push2his 会直接断流；切到 HttpURLConnection。
                useUrlConnection = true
            }
        }
        return getUrlConnection(url, referer)
    }

    private fun getOkHttp(url: String, referer: String): String {
        var last: Exception? = null
        repeat(2) { attempt ->
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", UA)
                    .header("Referer", referer)
                    .header("Accept", "application/json,text/plain,*/*")
                    .header("Accept-Encoding", "identity")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body?.string() ?: error("empty body")
                    if (body.startsWith("{")) return body
                    error("unexpected body")
                }
            } catch (e: Exception) {
                last = e
                Thread.sleep(250L * (attempt + 1))
            }
        }
        throw last ?: IllegalStateException("request failed")
    }

    private fun getUrlConnection(url: String, referer: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 25000
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Referer", referer)
        conn.setRequestProperty("Accept", "application/json,text/plain,*/*")
        conn.setRequestProperty("Accept-Encoding", "identity")
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val bytes = conn.inputStream.readBytes()
            return String(bytes, Charsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    private fun isSuspended(o: JSONObject): Boolean {
        val price = o.optString("trade").toDoubleOrNull() ?: 0.0
        val volume = o.optString("volume").toDoubleOrNull() ?: 0.0
        val amount = o.optString("amount").toDoubleOrNull() ?: 0.0
        return price <= 0.0 || (volume <= 0.0 && amount <= 0.0)
    }

    private fun boardOf(code: String, market: Int): Board = when {
        code.startsWith("688") || code.startsWith("689") -> Board.STAR
        code.startsWith("300") || code.startsWith("301") -> Board.CHINEXT
        code.startsWith("8") || code.startsWith("4") || code.startsWith("92") -> Board.BSE
        market == 1 -> Board.SH_MAIN
        else -> Board.SZ_MAIN
    }

    private fun tencentSymbol(secid: String): String {
        if (secid.startsWith("sh") || secid.startsWith("sz") || secid.startsWith("bj")) return secid
        val code = secid.substringAfterLast(".")
        val market = secid.substringBefore(".")
        return when {
            market == "1" -> "sh$code"
            code.startsWith("6") -> "sh$code"
            code.startsWith("4") || code.startsWith("8") || code.startsWith("92") -> "bj$code"
            else -> "sz$code"
        }
    }

    private fun JSONObject.d(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val v = optDouble(key, Double.NaN)
        return if (v.isNaN()) null else v
    }
}
