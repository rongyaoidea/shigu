package com.shigu

import android.app.Application
import com.shigu.data.EastmoneyApi
import com.shigu.data.MarketRepository
import com.shigu.data.PaperRepository
import com.shigu.data.Settings
import com.shigu.data.ShiguDb
import com.shigu.data.StrategyRepository
import com.shigu.data.SyncManager
import com.shigu.data.SyncScheduler

class ShiguApp : Application() {
    lateinit var db: ShiguDb
        private set
    lateinit var api: EastmoneyApi
        private set
    lateinit var settings: Settings
        private set
    lateinit var syncManager: SyncManager
        private set
    lateinit var market: MarketRepository
        private set
    lateinit var strategies: StrategyRepository
        private set
    lateinit var paper: PaperRepository
        private set

    override fun onCreate() {
        super.onCreate()
        db = ShiguDb(this)
        api = EastmoneyApi()
        settings = Settings(this)
        syncManager = SyncManager(db, api, settings)
        market = MarketRepository(db)
        strategies = StrategyRepository(db, market)
        paper = PaperRepository(db, market)
        strategies.ensureBuiltins()
        SyncScheduler.schedule(this, settings)
    }
}
