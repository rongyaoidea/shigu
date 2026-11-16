package com.shigu.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.shigu.core.Board
import com.shigu.core.DailyBar
import com.shigu.core.DataStats
import com.shigu.core.Stock
import com.shigu.core.ValuationPoint
import com.shigu.core.WatchItem

class ShiguDb(private val appContext: Context) : SQLiteOpenHelper(appContext, "shigu.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE stocks(
                code TEXT PRIMARY KEY, name TEXT NOT NULL, secid TEXT NOT NULL, board TEXT NOT NULL,
                price REAL, change_pct REAL, open REAL, high REAL, low REAL, prev_close REAL,
                volume REAL, amount REAL, turnover REAL, pe REAL, pb REAL,
                market_cap REAL, float_cap REAL, volume_ratio REAL, change_60 REAL, change_ytd REAL,
                is_st INTEGER NOT NULL DEFAULT 0, updated_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE bars(
                code TEXT NOT NULL, date TEXT NOT NULL,
                open REAL, close REAL, high REAL, low REAL, volume REAL, amount REAL, pct REAL, turnover REAL,
                PRIMARY KEY(code, date)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_bars_date ON bars(date)")
        db.execSQL(
            """
            CREATE TABLE valuation(
                code TEXT NOT NULL, date TEXT NOT NULL, pe REAL, pb REAL, market_cap REAL, close REAL,
                PRIMARY KEY(code, date)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_valuation_date ON valuation(date)")
        db.execSQL(
            """
            CREATE TABLE watchlist(
                code TEXT PRIMARY KEY, added_at INTEGER NOT NULL, entry_price REAL NOT NULL,
                thesis TEXT NOT NULL DEFAULT '', group_name TEXT NOT NULL DEFAULT '核心'
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT)")
        db.execSQL(
            """
            CREATE TABLE strategies(
                id TEXT PRIMARY KEY, name TEXT NOT NULL, builtin INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL, config_json TEXT, last_run_at INTEGER,
                last_backtest_json TEXT, last_since_json TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE backtest_runs(
                id INTEGER PRIMARY KEY AUTOINCREMENT, strategy_id TEXT NOT NULL, created_at INTEGER NOT NULL,
                stats_json TEXT, equity_json TEXT, trades_json TEXT, picks_json TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE paper_state(
                strategy_id TEXT PRIMARY KEY, updated_at INTEGER NOT NULL, last_trade_date TEXT NOT NULL,
                equity REAL NOT NULL, benchmark REAL NOT NULL, cash REAL NOT NULL,
                holdings_json TEXT NOT NULL, last_change_json TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE paper_equity(
                strategy_id TEXT NOT NULL, date TEXT NOT NULL, equity REAL NOT NULL, benchmark REAL NOT NULL,
                PRIMARY KEY(strategy_id, date)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_paper_equity_strategy ON paper_equity(strategy_id, date)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v2：增加策略纸面组合表；保留已有股票、日线、观察和策略。
        if (oldVersion < 2) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS paper_state(
                    strategy_id TEXT PRIMARY KEY, updated_at INTEGER NOT NULL, last_trade_date TEXT NOT NULL,
                    equity REAL NOT NULL, benchmark REAL NOT NULL, cash REAL NOT NULL,
                    holdings_json TEXT NOT NULL, last_change_json TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS paper_equity(
                    strategy_id TEXT NOT NULL, date TEXT NOT NULL, equity REAL NOT NULL, benchmark REAL NOT NULL,
                    PRIMARY KEY(strategy_id, date)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_paper_equity_strategy ON paper_equity(strategy_id, date)")
        }
    }

    // ---------------- stocks ----------------

    fun upsertStocks(stocks: List<Stock>) {
        if (stocks.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val st = db.compileStatement(
                "INSERT OR REPLACE INTO stocks(code,name,secid,board,price,change_pct,open,high,low,prev_close,volume,amount,turnover,pe,pb,market_cap,float_cap,volume_ratio,change_60,change_ytd,is_st,updated_at) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            )
            stocks.forEach { s ->
                st.clearBindings()
                st.bindString(1, s.code)
                st.bindString(2, s.name)
                st.bindString(3, s.secid)
                st.bindString(4, s.board.name)
                st.bindDouble(5, s.price)
                st.bindDouble(6, s.changePct)
                st.bindDouble(7, s.open)
                st.bindDouble(8, s.high)
                st.bindDouble(9, s.low)
                st.bindDouble(10, s.prevClose)
                st.bindDouble(11, s.volume)
                st.bindDouble(12, s.amount)
                st.bindDouble(13, s.turnover)
                bindNullable(st, 14, s.pe)
                bindNullable(st, 15, s.pb)
                bindNullable(st, 16, s.marketCap)
                bindNullable(st, 17, s.floatCap)
                bindNullable(st, 18, s.volumeRatio)
                bindNullable(st, 19, s.change60)
                bindNullable(st, 20, s.changeYtd)
                st.bindLong(21, if (s.isSt) 1 else 0)
                st.bindLong(22, s.updatedAt)
                st.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getStocks(): List<Stock> {
        val out = ArrayList<Stock>(6000)
        readableDatabase.rawQuery("SELECT * FROM stocks ORDER BY market_cap DESC", null).use { c ->
            while (c.moveToNext()) out += readStock(c)
        }
        return out
    }

    /** 用最新资产列表清理退市/改名/被剔除的旧代码；保留用户数据和日线文件，避免误删。 */
    fun pruneStocks(keepCodes: Collection<String>) {
        if (keepCodes.size < 3000) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("CREATE TEMP TABLE IF NOT EXISTS keep_codes(code TEXT PRIMARY KEY)")
            db.execSQL("DELETE FROM keep_codes")
            val st = db.compileStatement("INSERT OR IGNORE INTO keep_codes(code) VALUES(?)")
            keepCodes.forEach {
                st.clearBindings()
                st.bindString(1, it)
                st.executeInsert()
            }
            db.execSQL("DELETE FROM stocks WHERE code NOT IN (SELECT code FROM keep_codes)")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getStock(code: String): Stock? {
        readableDatabase.rawQuery("SELECT * FROM stocks WHERE code=?", arrayOf(code)).use { c ->
            return if (c.moveToFirst()) readStock(c) else null
        }
    }

    private fun readStock(c: android.database.Cursor): Stock {
        fun d(name: String): Double = c.getDouble(c.getColumnIndexOrThrow(name))
        fun dn(name: String): Double? {
            val i = c.getColumnIndexOrThrow(name)
            return if (c.isNull(i)) null else c.getDouble(i)
        }
        return Stock(
            code = c.getString(c.getColumnIndexOrThrow("code")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            secid = c.getString(c.getColumnIndexOrThrow("secid")),
            board = runCatching { Board.valueOf(c.getString(c.getColumnIndexOrThrow("board"))) }.getOrDefault(Board.SZ_MAIN),
            price = d("price"),
            changePct = d("change_pct"),
            open = d("open"),
            high = d("high"),
            low = d("low"),
            prevClose = d("prev_close"),
            volume = d("volume"),
            amount = d("amount"),
            turnover = d("turnover"),
            pe = dn("pe"),
            pb = dn("pb"),
            marketCap = dn("market_cap"),
            floatCap = dn("float_cap"),
            volumeRatio = dn("volume_ratio"),
            change60 = dn("change_60"),
            changeYtd = dn("change_ytd"),
            isSt = c.getInt(c.getColumnIndexOrThrow("is_st")) == 1,
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
        )
    }

    private fun bindNullable(st: android.database.sqlite.SQLiteStatement, index: Int, v: Double?) {
        if (v == null || v.isNaN()) st.bindNull(index) else st.bindDouble(index, v)
    }

    // ---------------- bars ----------------

    fun upsertBars(bars: List<DailyBar>) {
        if (bars.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val st = db.compileStatement(
                "INSERT OR REPLACE INTO bars(code,date,open,close,high,low,volume,amount,pct,turnover) VALUES(?,?,?,?,?,?,?,?,?,?)",
            )
            bars.forEach { b ->
                st.clearBindings()
                st.bindString(1, b.code)
                st.bindString(2, b.date)
                st.bindDouble(3, b.open)
                st.bindDouble(4, b.close)
                st.bindDouble(5, b.high)
                st.bindDouble(6, b.low)
                st.bindDouble(7, b.volume)
                st.bindDouble(8, b.amount)
                st.bindDouble(9, b.pct)
                st.bindDouble(10, b.turnover)
                st.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getBars(code: String, limit: Int = 300): List<DailyBar> {
        val out = ArrayList<DailyBar>(limit)
        readableDatabase.rawQuery(
            "SELECT * FROM bars WHERE code=? ORDER BY date DESC LIMIT ?",
            arrayOf(code, limit.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                out += DailyBar(
                    code = c.getString(0),
                    date = c.getString(1),
                    open = c.getDouble(2),
                    close = c.getDouble(3),
                    high = c.getDouble(4),
                    low = c.getDouble(5),
                    volume = c.getDouble(6),
                    amount = c.getDouble(7),
                    pct = c.getDouble(8),
                    turnover = c.getDouble(9),
                )
            }
        }
        out.reverse()
        return out
    }

    fun getBenchmarkBars(limit: Int = 700): List<DailyBar> = getBars(BENCHMARK, limit)

    fun getBarsForCodes(codes: Collection<String>, startDate: String): Map<String, List<DailyBar>> {
        val result = HashMap<String, MutableList<DailyBar>>()
        if (codes.isEmpty()) return result
        val db = readableDatabase
        codes.chunked(400).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val args = ArrayList<String>(chunk.size + 1)
            args += chunk
            args += startDate
            db.rawQuery(
                "SELECT code,date,open,close,high,low,volume,amount,pct,turnover FROM bars " +
                    "WHERE code IN ($placeholders) AND date >= ? ORDER BY code,date",
                args.toTypedArray(),
            ).use { c ->
                while (c.moveToNext()) {
                    val code = c.getString(0)
                    result.getOrPut(code) { ArrayList() } += DailyBar(
                        code = code,
                        date = c.getString(1),
                        open = c.getDouble(2),
                        close = c.getDouble(3),
                        high = c.getDouble(4),
                        low = c.getDouble(5),
                        volume = c.getDouble(6),
                        amount = c.getDouble(7),
                        pct = c.getDouble(8),
                        turnover = c.getDouble(9),
                    )
                }
            }
        }
        return result
    }

    fun getLastBarDate(code: String): String? {
        readableDatabase.rawQuery("SELECT MAX(date) FROM bars WHERE code=?", arrayOf(code)).use { c ->
            return if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
        }
    }

    fun countBars(): Long = scalar("SELECT COUNT(*) FROM bars")

    fun countStocks(): Long = scalar("SELECT COUNT(*) FROM stocks")

    fun countValuations(): Long = scalar("SELECT COUNT(*) FROM valuation")

    fun lastTradeDate(): String {
        readableDatabase.rawQuery("SELECT MAX(date) FROM bars WHERE code=?", arrayOf(BENCHMARK)).use { c ->
            return if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else ""
        }
    }

    private fun scalar(sql: String): Long {
        readableDatabase.rawQuery(sql, null).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else 0L
        }
    }

    // ---------------- valuation ----------------

    fun upsertValuations(list: List<ValuationPoint>) {
        if (list.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val st = db.compileStatement(
                "INSERT OR REPLACE INTO valuation(code,date,pe,pb,market_cap,close) VALUES(?,?,?,?,?,?)",
            )
            list.forEach { v ->
                st.clearBindings()
                st.bindString(1, v.code)
                st.bindString(2, v.date)
                bindNullable(st, 3, v.pe)
                bindNullable(st, 4, v.pb)
                bindNullable(st, 5, v.marketCap)
                bindNullable(st, 6, v.close)
                st.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getValuationsForCodes(codes: Collection<String>, startDate: String): Map<String, List<ValuationPoint>> {
        val result = HashMap<String, MutableList<ValuationPoint>>()
        if (codes.isEmpty()) return result
        val db = readableDatabase
        codes.chunked(400).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val args = ArrayList<String>(chunk.size + 1)
            args += chunk
            args += startDate
            db.rawQuery(
                "SELECT code,date,pe,pb,market_cap,close FROM valuation WHERE code IN ($placeholders) AND date >= ? ORDER BY code,date",
                args.toTypedArray(),
            ).use { c ->
                while (c.moveToNext()) {
                    val code = c.getString(0)
                    result.getOrPut(code) { ArrayList() } += ValuationPoint(
                        code = code,
                        date = c.getString(1),
                        pe = if (c.isNull(2)) null else c.getDouble(2),
                        pb = if (c.isNull(3)) null else c.getDouble(3),
                        marketCap = if (c.isNull(4)) null else c.getDouble(4),
                        close = if (c.isNull(5)) null else c.getDouble(5),
                    )
                }
            }
        }
        return result
    }

    fun hasValuationHistory(code: String): Boolean {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM valuation WHERE code=?", arrayOf(code)).use { c ->
            return c.moveToFirst() && c.getLong(0) > 30
        }
    }

    // ---------------- watchlist ----------------

    fun addWatch(code: String, entryPrice: Double, thesis: String, group: String = "核心") {
        val db = writableDatabase
        db.execSQL(
            "INSERT OR REPLACE INTO watchlist(code,added_at,entry_price,thesis,group_name) VALUES(?,?,?,?,?)",
            arrayOf<Any?>(code, System.currentTimeMillis(), entryPrice, thesis, group),
        )
    }

    fun removeWatch(code: String) {
        writableDatabase.delete("watchlist", "code=?", arrayOf(code))
    }

    fun isWatched(code: String): Boolean {
        readableDatabase.rawQuery("SELECT 1 FROM watchlist WHERE code=?", arrayOf(code)).use { c ->
            return c.moveToFirst()
        }
    }

    fun getWatchlistCodes(): List<String> {
        val out = ArrayList<String>()
        readableDatabase.rawQuery("SELECT code FROM watchlist ORDER BY added_at DESC", null).use { c ->
            while (c.moveToNext()) out += c.getString(0)
        }
        return out
    }

    fun getWatchlist(): List<WatchItem> {
        val out = ArrayList<WatchItem>()
        readableDatabase.rawQuery(
            "SELECT w.code,w.added_at,w.entry_price,w.thesis,w.group_name," +
                "s.name,s.price,s.change_pct,s.board FROM watchlist w LEFT JOIN stocks s ON s.code=w.code " +
                "ORDER BY w.added_at DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val code = c.getString(0)
                out += WatchItem(
                    code = code,
                    name = if (c.isNull(5)) code else c.getString(5),
                    addedAt = c.getLong(1),
                    entryPrice = c.getDouble(2),
                    thesis = c.getString(3),
                    group = c.getString(4),
                    price = if (c.isNull(6)) 0.0 else c.getDouble(6),
                    changePct = if (c.isNull(7)) 0.0 else c.getDouble(7),
                    board = runCatching { Board.valueOf(c.getString(8)) }.getOrDefault(Board.SZ_MAIN),
                )
            }
        }
        return out
    }

    // ---------------- paper simulation ----------------

    data class PaperState(
        val strategyId: String,
        val updatedAt: Long,
        val lastTradeDate: String,
        val equity: Double,
        val benchmark: Double,
        val cash: Double,
        val holdingsJson: String,
        val lastChangeJson: String?,
    )

    fun getPaperState(strategyId: String): PaperState? {
        readableDatabase.rawQuery(
            "SELECT strategy_id,updated_at,last_trade_date,equity,benchmark,cash,holdings_json,last_change_json " +
                "FROM paper_state WHERE strategy_id=?",
            arrayOf(strategyId),
        ).use { c ->
            if (!c.moveToFirst()) return null
            return PaperState(
                strategyId = c.getString(0),
                updatedAt = c.getLong(1),
                lastTradeDate = c.getString(2),
                equity = c.getDouble(3),
                benchmark = c.getDouble(4),
                cash = c.getDouble(5),
                holdingsJson = c.getString(6),
                lastChangeJson = if (c.isNull(7)) null else c.getString(7),
            )
        }
    }

    fun upsertPaperState(
        strategyId: String,
        now: Long,
        lastTradeDate: String,
        equity: Double,
        benchmark: Double,
        cash: Double,
        holdingsJson: String,
        lastChangeJson: String?,
    ) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO paper_state(strategy_id,updated_at,last_trade_date,equity,benchmark,cash,holdings_json,last_change_json) " +
                "VALUES(?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(strategyId, now, lastTradeDate, equity, benchmark, cash, holdingsJson, lastChangeJson),
        )
    }

    fun insertPaperEquity(strategyId: String, date: String, equity: Double, benchmark: Double) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO paper_equity(strategy_id,date,equity,benchmark) VALUES(?,?,?,?)",
            arrayOf<Any?>(strategyId, date, equity, benchmark),
        )
    }

    // ---------------- meta ----------------

    fun putMeta(key: String, value: String) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO meta(key,value) VALUES(?,?)", arrayOf(key, value))
    }

    fun getMeta(key: String): String? {
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key=?", arrayOf(key)).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    // ---------------- strategies ----------------

    data class StrategyRow(
        val id: String,
        val name: String,
        val builtin: Boolean,
        val createdAt: Long,
        val configJson: String?,
        val lastBacktestJson: String?,
        val lastSinceJson: String?,
    )

    fun getStrategyRows(): List<StrategyRow> {
        val out = ArrayList<StrategyRow>()
        readableDatabase.rawQuery("SELECT id,name,builtin,created_at,config_json,last_backtest_json,last_since_json FROM strategies", null).use { c ->
            while (c.moveToNext()) {
                out += StrategyRow(
                    id = c.getString(0),
                    name = c.getString(1),
                    builtin = c.getInt(2) == 1,
                    createdAt = c.getLong(3),
                    configJson = if (c.isNull(4)) null else c.getString(4),
                    lastBacktestJson = if (c.isNull(5)) null else c.getString(5),
                    lastSinceJson = if (c.isNull(6)) null else c.getString(6),
                )
            }
        }
        return out
    }

    fun upsertStrategy(id: String, name: String, builtin: Boolean, createdAt: Long, configJson: String?) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO strategies(id,name,builtin,created_at,config_json) VALUES(?,?,?,?,?)",
            arrayOf<Any?>(id, name, if (builtin) 1 else 0, createdAt, configJson),
        )
    }

    fun updateStrategyRun(id: String, now: Long, backtestJson: String?, sinceJson: String?) {
        writableDatabase.execSQL(
            "UPDATE strategies SET last_run_at=?, last_backtest_json=?, last_since_json=? WHERE id=?",
            arrayOf<Any?>(now, backtestJson, sinceJson, id),
        )
    }

    fun deleteStrategy(id: String) {
        writableDatabase.delete("strategies", "id=?", arrayOf(id))
    }

    fun updateStrategyConfig(id: String, name: String, createdAt: Long, configJson: String) {
        writableDatabase.execSQL(
            "UPDATE strategies SET name=?, created_at=?, config_json=? WHERE id=?",
            arrayOf<Any?>(name, createdAt, configJson, id),
        )
    }

    /** 重置一个策略的纸面模拟（编辑策略后从今天重新开始）。 */
    fun deletePaperState(id: String) {
        writableDatabase.delete("paper_state", "strategy_id=?", arrayOf(id))
        writableDatabase.delete("paper_equity", "strategy_id=?", arrayOf(id))
    }

    fun getPaperEquity(id: String): List<com.shigu.core.EquityPoint> {
        val out = ArrayList<com.shigu.core.EquityPoint>()
        readableDatabase.rawQuery(
            "SELECT date,equity,benchmark FROM paper_equity WHERE strategy_id=? ORDER BY date",
            arrayOf(id),
        ).use { c ->
            while (c.moveToNext()) {
                out += com.shigu.core.EquityPoint(c.getString(0), c.getDouble(1), c.getDouble(2))
            }
        }
        return out
    }

    fun stats(): DataStats {
        val lastSync = getMeta("last_sync")?.toLongOrNull() ?: 0L
        val dbFile = appContext.getDatabasePath("shigu.db")
        val bytes = runCatching { dbFile.length() }.getOrDefault(0L)
        return DataStats(
            stockCount = countStocks().toInt(),
            barCount = countBars(),
            valuationCount = countValuations(),
            lastSync = lastSync,
            lastTradeDate = lastTradeDate(),
            dbBytes = bytes,
        )
    }

    companion object {
        const val BENCHMARK = "000300"
    }
}
