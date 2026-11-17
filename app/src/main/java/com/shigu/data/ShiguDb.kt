package com.shigu.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.shigu.core.Board
import com.shigu.core.CustomFactor
import com.shigu.core.DailyBar
import com.shigu.core.DataStats
import com.shigu.core.OwnerType
import com.shigu.core.StStatus
import com.shigu.core.StatusSnapshot
import com.shigu.core.Stock
import com.shigu.core.ValuationPoint
import com.shigu.core.WatchItem

class ShiguDb(private val appContext: Context) : SQLiteOpenHelper(appContext, "shigu.db", null, 5) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE stocks(
                code TEXT PRIMARY KEY, name TEXT NOT NULL, secid TEXT NOT NULL, board TEXT NOT NULL,
                price REAL, change_pct REAL, open REAL, high REAL, low REAL, prev_close REAL,
                volume REAL, amount REAL, turnover REAL, pe REAL, pb REAL,
                market_cap REAL, float_cap REAL, volume_ratio REAL, change_60 REAL, change_ytd REAL,
                is_st INTEGER NOT NULL DEFAULT 0,
                st_status TEXT NOT NULL DEFAULT 'NORMAL',
                suspended INTEGER NOT NULL DEFAULT 0,
                owner_type TEXT NOT NULL DEFAULT 'UNKNOWN',
                listed_date TEXT NOT NULL DEFAULT '',
                actual_holder TEXT NOT NULL DEFAULT '',
                first_holder TEXT NOT NULL DEFAULT '',
                first_holder_type TEXT NOT NULL DEFAULT '',
                first_hold_ratio REAL,
                report_date TEXT NOT NULL DEFAULT '',
                roe REAL, gross_margin REAL, net_margin REAL,
                revenue_growth REAL, profit_growth REAL, debt_ratio REAL,
                eps REAL, bps REAL, ocfps REAL,
                profile_updated_at INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL DEFAULT 0
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
                holdings_json TEXT NOT NULL, last_change_json TEXT, trades_json TEXT
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
        db.execSQL(
            """
            CREATE TABLE stock_status_history(
                code TEXT NOT NULL, date TEXT NOT NULL, st_status TEXT NOT NULL,
                owner_type TEXT NOT NULL, suspended INTEGER NOT NULL, listed_date TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(code, date)
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_status_history_date ON stock_status_history(date)")
        db.execSQL(
            """
            CREATE TABLE stock_status_current(
                code TEXT PRIMARY KEY, date TEXT NOT NULL, st_status TEXT NOT NULL,
                owner_type TEXT NOT NULL, suspended INTEGER NOT NULL, listed_date TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE fundamentals_history(
                code TEXT NOT NULL, report_date TEXT NOT NULL, notice_date TEXT NOT NULL DEFAULT '',
                roe REAL, gross_margin REAL, net_margin REAL, revenue_growth REAL, profit_growth REAL,
                debt_ratio REAL, eps REAL, bps REAL, ocfps REAL,
                PRIMARY KEY(code, report_date)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE custom_factors(
                id TEXT PRIMARY KEY, name TEXT NOT NULL, expression TEXT NOT NULL,
                higher_is_better INTEGER NOT NULL DEFAULT 1, unit TEXT NOT NULL DEFAULT '',
                description TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
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
        // v3：股票基础资料（企业性质/上市日期/财务快照）。旧数据保留，资料列先给默认值。
        if (oldVersion < 3) {
            val columns = listOf(
                "st_status TEXT NOT NULL DEFAULT 'NORMAL'",
                "suspended INTEGER NOT NULL DEFAULT 0",
                "owner_type TEXT NOT NULL DEFAULT 'UNKNOWN'",
                "listed_date TEXT NOT NULL DEFAULT ''",
                "actual_holder TEXT NOT NULL DEFAULT ''",
                "first_holder TEXT NOT NULL DEFAULT ''",
                "first_holder_type TEXT NOT NULL DEFAULT ''",
                "first_hold_ratio REAL",
                "report_date TEXT NOT NULL DEFAULT ''",
                "roe REAL",
                "gross_margin REAL",
                "net_margin REAL",
                "revenue_growth REAL",
                "profit_growth REAL",
                "debt_ratio REAL",
                "eps REAL",
                "bps REAL",
                "ocfps REAL",
                "profile_updated_at INTEGER NOT NULL DEFAULT 0",
            )
            columns.forEach { col ->
                runCatching { db.execSQL("ALTER TABLE stocks ADD COLUMN $col") }
            }
            // 旧库没有 st_status：先按现有简称回填，避免升级后 ST 全部显示“正常”。
            runCatching {
                db.execSQL(
                    "UPDATE stocks SET st_status = CASE " +
                        "WHEN name LIKE '%退%' THEN 'DELISTING' " +
                        "WHEN name LIKE '%*ST%' THEN 'STAR_ST' " +
                        "WHEN name LIKE '%ST%' THEN 'ST' " +
                        "ELSE 'NORMAL' END",
                )
            }
            runCatching {
                db.execSQL("UPDATE stocks SET suspended = CASE WHEN price <= 0 THEN 1 ELSE 0 END")
            }
        }
        // v4：纸面交易流水 + 时点状态快照（只记录变化，供未来无前视回测使用）。
        if (oldVersion < 4) {
            runCatching { db.execSQL("ALTER TABLE paper_state ADD COLUMN trades_json TEXT") }
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS stock_status_history(
                    code TEXT NOT NULL, date TEXT NOT NULL, st_status TEXT NOT NULL,
                    owner_type TEXT NOT NULL, suspended INTEGER NOT NULL, listed_date TEXT NOT NULL DEFAULT '',
                    PRIMARY KEY(code, date)
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_status_history_date ON stock_status_history(date)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS stock_status_current(
                    code TEXT PRIMARY KEY, date TEXT NOT NULL, st_status TEXT NOT NULL,
                    owner_type TEXT NOT NULL, suspended INTEGER NOT NULL, listed_date TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS fundamentals_history(
                    code TEXT NOT NULL, report_date TEXT NOT NULL, notice_date TEXT NOT NULL DEFAULT '',
                    roe REAL, gross_margin REAL, net_margin REAL, revenue_growth REAL, profit_growth REAL,
                    debt_ratio REAL, eps REAL, bps REAL, ocfps REAL,
                    PRIMARY KEY(code, report_date)
                )
                """.trimIndent(),
            )
        }
        if (oldVersion < 5) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS custom_factors(
                    id TEXT PRIMARY KEY, name TEXT NOT NULL, expression TEXT NOT NULL,
                    higher_is_better INTEGER NOT NULL DEFAULT 1, unit TEXT NOT NULL DEFAULT '',
                    description TEXT NOT NULL DEFAULT '', created_at INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
        }
    }

    // ---------------- stocks ----------------

    fun upsertStocks(stocks: List<Stock>) {
        if (stocks.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            // 行情只覆盖行情列；企业性质/财务等资料列由 updateProfiles 单独写，避免每次同步被清空。
            val insert = db.compileStatement(
                "INSERT OR IGNORE INTO stocks(code,name,secid,board,price,change_pct,open,high,low,prev_close,volume,amount,turnover,pe,pb,market_cap,float_cap,volume_ratio,change_60,change_ytd,is_st,st_status,suspended,updated_at) " +
                    "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            )
            val update = db.compileStatement(
                "UPDATE stocks SET name=?,secid=?,board=?,price=?,change_pct=?,open=?,high=?,low=?,prev_close=?,volume=?,amount=?,turnover=?,pe=?,pb=?,market_cap=?,float_cap=?,volume_ratio=?,change_60=?,change_ytd=?,is_st=?,st_status=?,suspended=?,updated_at=? WHERE code=?",
            )
            stocks.forEach { s ->
                insert.clearBindings()
                insert.bindString(1, s.code)
                insert.bindString(2, s.name)
                insert.bindString(3, s.secid)
                insert.bindString(4, s.board.name)
                insert.bindDouble(5, s.price)
                insert.bindDouble(6, s.changePct)
                insert.bindDouble(7, s.open)
                insert.bindDouble(8, s.high)
                insert.bindDouble(9, s.low)
                insert.bindDouble(10, s.prevClose)
                insert.bindDouble(11, s.volume)
                insert.bindDouble(12, s.amount)
                insert.bindDouble(13, s.turnover)
                bindNullable(insert, 14, s.pe)
                bindNullable(insert, 15, s.pb)
                bindNullable(insert, 16, s.marketCap)
                bindNullable(insert, 17, s.floatCap)
                bindNullable(insert, 18, s.volumeRatio)
                bindNullable(insert, 19, s.change60)
                bindNullable(insert, 20, s.changeYtd)
                insert.bindLong(21, if (s.isSt) 1 else 0)
                insert.bindString(22, s.stStatus.name)
                insert.bindLong(23, if (s.suspended) 1 else 0)
                insert.bindLong(24, s.updatedAt)
                insert.executeInsert()

                update.clearBindings()
                update.bindString(1, s.name)
                update.bindString(2, s.secid)
                update.bindString(3, s.board.name)
                update.bindDouble(4, s.price)
                update.bindDouble(5, s.changePct)
                update.bindDouble(6, s.open)
                update.bindDouble(7, s.high)
                update.bindDouble(8, s.low)
                update.bindDouble(9, s.prevClose)
                update.bindDouble(10, s.volume)
                update.bindDouble(11, s.amount)
                update.bindDouble(12, s.turnover)
                bindNullable(update, 13, s.pe)
                bindNullable(update, 14, s.pb)
                bindNullable(update, 15, s.marketCap)
                bindNullable(update, 16, s.floatCap)
                bindNullable(update, 17, s.volumeRatio)
                bindNullable(update, 18, s.change60)
                bindNullable(update, 19, s.changeYtd)
                update.bindLong(20, if (s.isSt) 1 else 0)
                update.bindString(21, s.stStatus.name)
                update.bindLong(22, if (s.suspended) 1 else 0)
                update.bindLong(23, s.updatedAt)
                update.bindString(24, s.code)
                update.executeUpdateDelete()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 只写基础资料列，不动行情。 */
    fun updateProfiles(profiles: List<StockProfile>, now: Long = System.currentTimeMillis()) {        if (profiles.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val st = db.compileStatement(
                "UPDATE stocks SET owner_type=?,listed_date=?,actual_holder=?,first_holder=?,first_holder_type=?,first_hold_ratio=?," +
                    "report_date=?,roe=?,gross_margin=?,net_margin=?,revenue_growth=?,profit_growth=?,debt_ratio=?,eps=?,bps=?,ocfps=?," +
                    "profile_updated_at=? WHERE code=?",
            )
            profiles.forEach { p ->
                st.clearBindings()
                st.bindString(1, p.ownerType.name)
                st.bindString(2, p.listedDate)
                st.bindString(3, p.actualHolder)
                st.bindString(4, p.firstHolder)
                st.bindString(5, p.firstHolderType)
                bindNullable(st, 6, p.firstHoldRatio)
                st.bindString(7, p.reportDate)
                bindNullable(st, 8, p.roe)
                bindNullable(st, 9, p.grossMargin)
                bindNullable(st, 10, p.netMargin)
                bindNullable(st, 11, p.revenueGrowth)
                bindNullable(st, 12, p.profitGrowth)
                bindNullable(st, 13, p.debtRatio)
                bindNullable(st, 14, p.eps)
                bindNullable(st, 15, p.bps)
                bindNullable(st, 16, p.ocfps)
                st.bindLong(17, now)
                st.bindString(18, p.code)
                st.executeUpdateDelete()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 财报历史：按报告期保存，未来用公告日做 as-of 回测。 */
    fun upsertFundamentals(list: List<FinanceProfile>) {
        if (list.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            val st = db.compileStatement(
                "INSERT OR REPLACE INTO fundamentals_history(code,report_date,notice_date,roe,gross_margin,net_margin," +
                    "revenue_growth,profit_growth,debt_ratio,eps,bps,ocfps) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            )
            list.forEach { p ->
                st.clearBindings()
                st.bindString(1, p.code)
                st.bindString(2, p.reportDate)
                st.bindString(3, p.noticeDate)
                bindNullable(st, 4, p.roe)
                bindNullable(st, 5, p.grossMargin)
                bindNullable(st, 6, p.netMargin)
                bindNullable(st, 7, p.revenueGrowth)
                bindNullable(st, 8, p.profitGrowth)
                bindNullable(st, 9, p.debtRatio)
                bindNullable(st, 10, p.eps)
                bindNullable(st, 11, p.bps)
                bindNullable(st, 12, p.ocfps)
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
        val current = countStocks()
        if (keepCodes.size < 3000) return
        if (current > 3000 && keepCodes.size < current * 0.98) return
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
        val name = c.getString(c.getColumnIndexOrThrow("name"))
        return Stock(
            code = c.getString(c.getColumnIndexOrThrow("code")),
            name = name,
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
            stStatus = runCatching {
                StStatus.valueOf(c.getString(c.getColumnIndexOrThrow("st_status")))
            }.getOrDefault(StStatus.fromName(name)),
            suspended = c.getInt(c.getColumnIndexOrThrow("suspended")) == 1,
            updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
            ownerType = runCatching {
                OwnerType.valueOf(c.getString(c.getColumnIndexOrThrow("owner_type")))
            }.getOrDefault(OwnerType.UNKNOWN),
            listedDate = c.getString(c.getColumnIndexOrThrow("listed_date")),
            actualHolder = c.getString(c.getColumnIndexOrThrow("actual_holder")),
            firstHolder = c.getString(c.getColumnIndexOrThrow("first_holder")),
            firstHolderType = c.getString(c.getColumnIndexOrThrow("first_holder_type")),
            firstHoldRatio = dn("first_hold_ratio"),
            reportDate = c.getString(c.getColumnIndexOrThrow("report_date")),
            roe = dn("roe"),
            grossMargin = dn("gross_margin"),
            netMargin = dn("net_margin"),
            revenueGrowth = dn("revenue_growth"),
            profitGrowth = dn("profit_growth"),
            debtRatio = dn("debt_ratio"),
            eps = dn("eps"),
            bps = dn("bps"),
            ocfps = dn("ocfps"),
            profileUpdatedAt = c.getLong(c.getColumnIndexOrThrow("profile_updated_at")),
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

    fun countProfiles(): Long = scalar("SELECT COUNT(*) FROM stocks WHERE profile_updated_at > 0")

    // ---------------- 自定义因子 ----------------

    fun getCustomFactors(): List<CustomFactor> {
        val out = ArrayList<CustomFactor>()
        readableDatabase.rawQuery(
            "SELECT id,name,expression,higher_is_better,unit,description FROM custom_factors ORDER BY created_at DESC",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out += CustomFactor(
                    id = c.getString(0),
                    name = c.getString(1),
                    expression = c.getString(2),
                    higherIsBetter = c.getInt(3) == 1,
                    unit = c.getString(4),
                    description = c.getString(5),
                )
            }
        }
        return out
    }

    fun upsertCustomFactor(factor: CustomFactor, now: Long = System.currentTimeMillis()) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO custom_factors(id,name,expression,higher_is_better,unit,description,created_at) VALUES(?,?,?,?,?,?,?)",
            arrayOf<Any?>(
                factor.id,
                factor.name,
                factor.expression,
                if (factor.higherIsBetter) 1 else 0,
                factor.unit,
                factor.description,
                now,
            ),
        )
    }

    fun deleteCustomFactor(id: String) {
        writableDatabase.delete("custom_factors", "id=?", arrayOf(id))
    }

    // ---------------- 时点状态 ----------------

    /** 只记录发生变化的状态，避免每天 6k 行把库撑大。 */
    fun recordStatusSnapshot(stocks: List<Stock>, date: String) {
        if (stocks.isEmpty() || date.isBlank()) return
        val current = HashMap<String, StatusSnapshot>(stocks.size)
        readableDatabase.rawQuery(
            "SELECT code,date,st_status,owner_type,suspended,listed_date FROM stock_status_current",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                val code = c.getString(0)
                current[code] = StatusSnapshot(
                    code = code,
                    date = c.getString(1),
                    stStatus = runCatching { StStatus.valueOf(c.getString(2)) }.getOrDefault(StStatus.NORMAL),
                    ownerType = runCatching { OwnerType.valueOf(c.getString(3)) }.getOrDefault(OwnerType.UNKNOWN),
                    suspended = c.getInt(4) == 1,
                    listedDate = c.getString(5),
                )
            }
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val history = db.compileStatement(
                "INSERT OR REPLACE INTO stock_status_history(code,date,st_status,owner_type,suspended,listed_date) VALUES(?,?,?,?,?,?)",
            )
            val latest = db.compileStatement(
                "INSERT OR REPLACE INTO stock_status_current(code,date,st_status,owner_type,suspended,listed_date) VALUES(?,?,?,?,?,?)",
            )
            stocks.forEach { s ->
                val prev = current[s.code]
                val changed = prev == null ||
                    prev.stStatus != s.stStatus ||
                    prev.ownerType != s.ownerType ||
                    prev.suspended != s.suspended ||
                    prev.listedDate != s.listedDate
                if (changed) {
                    history.clearBindings()
                    history.bindString(1, s.code)
                    history.bindString(2, date)
                    history.bindString(3, s.stStatus.name)
                    history.bindString(4, s.ownerType.name)
                    history.bindLong(5, if (s.suspended) 1 else 0)
                    history.bindString(6, s.listedDate)
                    history.executeInsert()
                }
                latest.clearBindings()
                latest.bindString(1, s.code)
                latest.bindString(2, date)
                latest.bindString(3, s.stStatus.name)
                latest.bindString(4, s.ownerType.name)
                latest.bindLong(5, if (s.suspended) 1 else 0)
                latest.bindString(6, s.listedDate)
                latest.executeInsert()
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getStatusHistory(codes: Collection<String>): Map<String, List<StatusSnapshot>> {
        val out = HashMap<String, MutableList<StatusSnapshot>>()
        if (codes.isEmpty()) return out
        codes.chunked(400).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            readableDatabase.rawQuery(
                "SELECT code,date,st_status,owner_type,suspended,listed_date FROM stock_status_history " +
                    "WHERE code IN ($placeholders) ORDER BY code,date",
                chunk.toTypedArray(),
            ).use { c ->
                while (c.moveToNext()) {
                    val code = c.getString(0)
                    out.getOrPut(code) { ArrayList() } += StatusSnapshot(
                        code = code,
                        date = c.getString(1),
                        stStatus = runCatching { StStatus.valueOf(c.getString(2)) }.getOrDefault(StStatus.NORMAL),
                        ownerType = runCatching { OwnerType.valueOf(c.getString(3)) }.getOrDefault(OwnerType.UNKNOWN),
                        suspended = c.getInt(4) == 1,
                        listedDate = c.getString(5),
                    )
                }
            }
        }
        return out
    }

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
        val tradesJson: String?,
    )

    fun getPaperState(strategyId: String): PaperState? {
        readableDatabase.rawQuery(
            "SELECT strategy_id,updated_at,last_trade_date,equity,benchmark,cash,holdings_json,last_change_json,trades_json " +
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
                tradesJson = if (c.isNull(8)) null else c.getString(8),
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
        tradesJson: String? = null,
    ) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO paper_state(strategy_id,updated_at,last_trade_date,equity,benchmark,cash,holdings_json,last_change_json,trades_json) " +
                "VALUES(?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(strategyId, now, lastTradeDate, equity, benchmark, cash, holdingsJson, lastChangeJson, tradesJson),
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
            profileCount = countProfiles().toInt(),
            profileReportDate = getMeta("profile_report_date").orEmpty(),
            bseCount = scalar("SELECT COUNT(*) FROM stocks WHERE board='BSE'").toInt(),
            stCount = scalar("SELECT COUNT(*) FROM stocks WHERE st_status<>'NORMAL'").toInt(),
            suspendedCount = scalar("SELECT COUNT(*) FROM stocks WHERE suspended=1").toInt(),
        )
    }

    companion object {
        const val BENCHMARK = "000300"
    }
}
