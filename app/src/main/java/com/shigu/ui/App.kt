package com.shigu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shigu.ui.screens.BacktestScreen
import com.shigu.ui.screens.DataScreen
import com.shigu.ui.screens.ScreenerScreen
import com.shigu.ui.screens.StockScreen
import com.shigu.ui.screens.WatchScreen
import com.shigu.ui.sketch.SketchDivider
import com.shigu.ui.theme.ShiguPalette
import kotlinx.coroutines.delay

private data class Tab(
    val route: String,
    val label: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    Tab("pick", "因子", "因子", "手动筛选 · 因子不是策略", Icons.Filled.FilterAlt),
    Tab("backtest", "策略", "策略", "内置 / 我的策略 · 回测 + 模拟", Icons.Filled.Assessment),
    Tab("watch", "模拟", "模拟", "策略模拟 + 个股观察", Icons.Filled.Visibility),
    Tab("data", "数据", "数据", "本地行情 · 延迟约 15 分钟", Icons.Filled.Storage),
)

@Composable
fun ShiguAppUi() {
    val vm: MainViewModel = viewModel()
    val state by vm.state.collectAsState()
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "pick"
    val isStock = route.startsWith("stock/")
    val active = tabs.firstOrNull { tab ->
        backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
    } ?: tabs.first()
    val hasStrategyAlert = state.strategyChanges.any { it.hasChange }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(3600)
            vm.clearMessage()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(ShiguPalette.Paper)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (!isStock) {
            AppHeader(active)
        }
        Box(Modifier.weight(1f)) {
            NavHost(
                navController = nav,
                startDestination = "pick",
                modifier = Modifier.fillMaxSize(),
            ) {
                composable("pick") {
                    ScreenerScreen(
                        state = state,
                        onOpenStock = { nav.navigate("stock/$it") },
                        onPreset = vm::selectScreenPreset,
                        onPool = vm::setScreenPool,
                        onAssetFilter = vm::setAssetFilter,
                        onApplyFactors = vm::applyFactors,
                        onApplyFilters = vm::applyFilters,
                        onAnalyzeFactor = vm::analyzeFactor,
                        onCloseFactorStats = vm::clearFactorStats,
                        onAnalyzeCorrelations = vm::analyzeCorrelations,
                        onCloseCorrelations = vm::clearCorrelations,
                        onSaveCustomFactor = vm::saveCustomFactor,
                        onDeleteCustomFactor = vm::deleteCustomFactor,
                        onAddCustomFactor = vm::addCustomFactorToScreen,
                        onRemoveCustomFactor = vm::removeCustomFactorFromScreen,
                        onCustomFactorWeight = vm::setCustomFactorWeight,
                        onSaveStrategy = vm::saveCustom,
                        onRun = vm::runScreen,
                        onQuickSync = vm::quickSync,
                    )
                }
                composable("backtest") {
                    BacktestScreen(
                        state = state,
                        onSelectStrategy = vm::selectStrategy,
                        onRun = vm::runBacktest,
                        onSaveCustom = vm::saveCustom,
                        onUpdateCustom = vm::updateCustom,
                        onDelete = vm::deleteStrategy,
                        onOpenStock = { nav.navigate("stock/$it") },
                        onWarm = vm::warmSummaries,
                        onRefreshSim = vm::refreshSimulations,
                        onGoFactors = { nav.navigate("pick") },
                    )
                }
                composable("watch") {
                    WatchScreen(
                        state = state,
                        onOpenStock = { nav.navigate("stock/$it") },
                        onRemove = { vm.toggleWatch(it) },
                        onWarm = vm::refreshSimulations,
                        onWarmSummaries = vm::warmSummaries,
                        onForceRefresh = vm::refreshPaperNow,
                        onOpenPaper = vm::loadPaperDetail,
                        onClosePaper = vm::clearPaperDetail,
                    )
                }
                composable("data") {
                    DataScreen(
                        state = state,
                        onQuick = vm::quickSync,
                        onDaily = vm::dailySync,
                        onFull = vm::fullSync,
                        onValuation = vm::valuationSync,
                        onProfile = vm::profileSync,
                        onCancelSync = vm::cancelSync,
                        onAutoUpdate = vm::setAutoUpdate,
                        onDailyUniverse = vm::setDailyUniverse,
                        onExportBackup = vm::exportBackup,
                        onImportBackup = vm::importBackup,
                    )
                }
                composable("stock/{code}") { entry ->
                    val code = entry.arguments?.getString("code") ?: return@composable
                    StockScreen(
                        code = code,
                        state = state,
                        onBack = { nav.popBackStack() },
                        onLoad = vm::loadStock,
                        onToggleWatch = vm::toggleWatch,
                    )
                }
            }
            if (state.message != null) {
                MessageBar(
                    text = state.message!!,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        if (!isStock) {
            BottomBar(
                tabs = tabs,
                activeRoute = active.route,
                alertRoutes = if (hasStrategyAlert) setOf("backtest", "watch") else emptySet(),
                onSelect = { tab ->
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
    }
}

@Composable
private fun AppHeader(active: Tab) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 120.dp)
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "拾股",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.4).sp,
                color = ShiguPalette.Ink,
            )
            Spacer(Modifier.weight(1f))
            Text(active.title, fontSize = 11.sp, color = ShiguPalette.InkSoft)
        }
        Text(active.subtitle, fontSize = 10.sp, color = ShiguPalette.InkSoft)
        Spacer(Modifier.height(4.dp))
        SketchDivider(seed = "app-header-${active.route}", ink = ShiguPalette.InkFaint)
    }
}

@Composable
private fun BottomBar(
    tabs: List<Tab>,
    activeRoute: String,
    alertRoutes: Set<String>,
    onSelect: (Tab) -> Unit,
) {
    Column {
        SketchDivider(ink = ShiguPalette.InkFaint, seed = "bottom-divider")
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .background(ShiguPalette.Paper),
        ) {
            tabs.forEach { tab ->
                val active = tab.route == activeRoute
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        Modifier
                            .background(
                                if (active) ShiguPalette.Highlighter else Color.Transparent,
                                RoundedCornerShape(4.dp),
                            )
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = tab.label,
                            modifier = Modifier.size(19.dp),
                            tint = if (active) ShiguPalette.Ink else ShiguPalette.InkSoft,
                        )
                        if (tab.route in alertRoutes) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(5.dp)
                                    .background(ShiguPalette.Alert, androidx.compose.foundation.shape.CircleShape),
                            )
                        }
                    }
                    Text(
                        tab.label,
                        fontSize = 10.sp,
                        color = if (active) ShiguPalette.Ink else ShiguPalette.InkSoft,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageBar(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = ShiguPalette.Ink,
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 2.dp,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = ShiguPalette.Paper,
            fontSize = 11.sp,
        )
    }
}
