package com.example.halauncher.ui.home

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.halauncher.config.ClockConfig
import com.example.halauncher.config.PinPickerConfig
import com.example.halauncher.config.SearchLayersConfig
import com.example.halauncher.data.AppRepository
import com.example.halauncher.data.ContactProvider
import com.example.halauncher.data.FileSearchProvider
import com.example.halauncher.data.LauncherSettingsStore
import com.example.halauncher.data.PinnedAppsStore
import com.example.halauncher.data.QuickActionAppStore
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.model.SearchEntry
import com.example.halauncher.search.SearchEngine
import com.example.halauncher.search.Trie
import com.example.halauncher.search.index.AppEntryCodec
import com.example.halauncher.search.index.ContactEntryCodec
import com.example.halauncher.search.index.FileEntryCodec
import com.example.halauncher.search.index.InteractionLog
import com.example.halauncher.search.index.SearchDatabase
import com.example.halauncher.search.index.SearchEntryCodecs
import com.example.halauncher.search.index.SearchIndex
import com.example.halauncher.search.ranking.ContextMatchRanker
import com.example.halauncher.search.sync.IndexUpdater
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Why search is open: to find and open something, or to choose an app for a home-screen button. */
enum class SearchPurpose {
    FIND,

    /** Choosing the money button's app: apps only, and tapping a result assigns it. */
    PICK_MONEY_APP,

    /** Choosing the reminders button's app: apps only, and tapping a result assigns it. */
    PICK_REMINDERS_APP,
}

/** What search shows: instant frequent-layer matches, then ranked disk matches under a divider. */
data class SearchResults(
    val frequent: List<SearchEntry> = emptyList(),
    val more: List<SearchEntry> = emptyList(),
    /** The disk layer hasn't answered the current query yet. */
    val isSearchingDisk: Boolean = false,
) {
    val isEmpty: Boolean get() = frequent.isEmpty() && more.isEmpty()
    fun firstOrNull(): SearchEntry? = frequent.firstOrNull() ?: more.firstOrNull()
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application)
    private val contactProvider = ContactProvider(application)
    private val settingsStore = LauncherSettingsStore(application)
    private val fileProvider = FileSearchProvider(application) { settingsStore.reduceIndexSize }

    private val searchEngine = run {
        val database = SearchDatabase(application)
        val codecs = SearchEntryCodecs(
            listOf(AppEntryCodec(appRepository::cachedApp), ContactEntryCodec, FileEntryCodec),
        )
        SearchEngine(
            index = SearchIndex(database, codecs),
            interactions = InteractionLog(
                database,
                retentionDays = SearchLayersConfig.INTERACTION_RETENTION_DAYS,
                pruneIntervalMillis = TimeUnit.HOURS.toMillis(SearchLayersConfig.INTERACTION_PRUNE_INTERVAL_HOURS),
            ),
            ranker = ContextMatchRanker(),
        )
    }

    /**
     * Keeps the disk index current in the background on a low-priority thread. All index writes
     * and frequent-layer rebuilds run on its [IndexUpdater.dispatcher], so they never overlap.
     */
    private val indexUpdater = IndexUpdater(application, searchEngine, appRepository, contactProvider, fileProvider)

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Whether contacts / files were readable when last checked; a change means re-sync. */
    private val accessSeen = mutableMapOf<SearchCategory, Boolean>()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    /** Every launchable app, A–Z: seeds the frequent layer and fills the pin picker. */
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val pinnedAppsStore = PinnedAppsStore(application)
    private val _pinnedPackages = MutableStateFlow(pinnedAppsStore.load())
    /** Package names pinned to the sphere, in pin order (including any currently uninstalled). */
    val pinnedPackages: StateFlow<List<String>> = _pinnedPackages.asStateFlow()

    /**
     * The pinned apps that are installed, in pin order, at most [PinPickerConfig.MAX_PINNED]:
     * what the sphere shows.
     */
    val pinnedApps: StateFlow<List<AppInfo>> =
        combine(_pinnedPackages, _installedApps) { packages, apps ->
            val byPackage = apps.associateBy { it.packageName }
            packages.mapNotNull(byPackage::get).take(PinPickerConfig.MAX_PINNED)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val quickActionAppStore = QuickActionAppStore(application)
    private val _moneyAppPackage = MutableStateFlow(quickActionAppStore.load(QuickActionAppStore.MONEY_SLOT))

    /** The app the money button opens, or null if none is chosen (or it's been uninstalled). */
    val moneyApp: StateFlow<AppInfo?> =
        combine(_moneyAppPackage, _installedApps) { packageName, apps ->
            packageName?.let { wanted -> apps.firstOrNull { it.packageName == wanted } }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _remindersAppPackage = MutableStateFlow(quickActionAppStore.load(QuickActionAppStore.REMINDERS_SLOT))

    /**
     * The app the reminders button opens, or null if none is chosen (or it's been uninstalled),
     * in which case the button falls back to the device's default reminders app.
     */
    val remindersApp: StateFlow<AppInfo?> =
        combine(_remindersAppPackage, _installedApps) { packageName, apps ->
            packageName?.let { wanted -> apps.firstOrNull { it.packageName == wanted } }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _searchPurpose = MutableStateFlow(SearchPurpose.FIND)
    val searchPurpose: StateFlow<SearchPurpose> = _searchPurpose.asStateFlow()

    private val _isPinPickerOpen = MutableStateFlow(false)
    val isPinPickerOpen: StateFlow<Boolean> = _isPinPickerOpen.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    /** Whether the long-press settings panel is showing. */
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _isHowToOpen = MutableStateFlow(false)
    /** Whether the "How to use this app" card is showing (on first launch, or opened from settings). */
    val isHowToOpen: StateFlow<Boolean> = _isHowToOpen.asStateFlow()

    private val _settingsLocked = MutableStateFlow(settingsStore.settingsLocked)
    /**
     * Whether settings are locked: the settings panel greys out its other options, and the
     * reminders/money buttons and app sphere stop offering their customisation.
     */
    val settingsLocked: StateFlow<Boolean> = _settingsLocked.asStateFlow()

    private val _doubleTapToLock = MutableStateFlow(settingsStore.doubleTapToLock)
    /** Whether a double-tap on the empty home screen locks the phone. */
    val doubleTapToLock: StateFlow<Boolean> = _doubleTapToLock.asStateFlow()

    private val _reduceIndexSize = MutableStateFlow(settingsStore.reduceIndexSize)
    /** Whether the file index leaves out denylisted folders to stay small. */
    val reduceIndexSize: StateFlow<Boolean> = _reduceIndexSize.asStateFlow()

    private val _hapticIntensity = MutableStateFlow(settingsStore.hapticIntensity)
    /** Strength of the haptic tick, 0f (off)–1f (strongest); set from the settings slider. */
    val hapticIntensity: StateFlow<Float> = _hapticIntensity.asStateFlow()

    private val _defaultSearchCategory = MutableStateFlow(settingsStore.defaultSearchCategory)
    /** The category search opens filtered to, or null for "All"; set from the settings panel. */
    val defaultSearchCategory: StateFlow<SearchCategory?> = _defaultSearchCategory.asStateFlow()

    /**
     * The in-memory frequent layer. The published trie is only read and changed on the main
     * thread: per-keystroke search runs there, and opened entries are inserted there in place.
     * Rebuilds make a new trie off the main thread and publish it once it's complete.
     */
    private val frequentLayer = MutableStateFlow(Trie<SearchEntry>())

    /** Bumped on each in-place insert, so search re-runs although the trie object is the same. */
    private val frequentLayerVersion = MutableStateFlow(0)

    /**
     * Incremental search over the current [frequentLayer], so each keystroke walks only what
     * changed. Main thread only, like the layer; replaced when a rebuild publishes a new trie.
     */
    private var frequentCursor: Trie<SearchEntry>.Cursor? = null

    /** Serialises publishing a rebuild with in-place inserts, so a rebuild can't drop one. */
    private val layerMutex = Mutex()

    /**
     * Entries opened this session, in order. They're added to the frequent layer the moment
     * they're opened and kept through any rebuild until the next launch, when their recorded
     * history decides whether they stay.
     */
    private val openedThisSession = MutableStateFlow<List<SearchEntry>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isSearchOpen = MutableStateFlow(false)
    val isSearchOpen: StateFlow<Boolean> = _isSearchOpen.asStateFlow()

    private val _categoryFilter = MutableStateFlow(_defaultSearchCategory.value)
    /** The category search is limited to, or null for all of them. */
    val categoryFilter: StateFlow<SearchCategory?> = _categoryFilter.asStateFlow()

    private val _availableCategories = MutableStateFlow<List<SearchCategory>>(emptyList())
    /** Categories that have at least one indexed entry, in declaration order; the filter offers only these. */
    val availableCategories: StateFlow<List<SearchCategory>> = _availableCategories.asStateFlow()

    private val _showFilesPrompt = MutableStateFlow(false)
    /** Whether search should offer to enable file search (no access yet, and not dismissed). */
    val showFilesPrompt: StateFlow<Boolean> = _showFilesPrompt.asStateFlow()

    /**
     * Frequent-layer matches at once on every change; then, if nothing has changed for
     * [SearchLayersConfig.DISK_SEARCH_DELAY_MS] (the user paused and opened nothing), ranked
     * disk matches too. A new keystroke cancels a pending or running disk search. With nothing
     * typed there are no disk matches, so the disk isn't touched at all.
     *
     * Kept up to date while search is closed too (for the blank query it resets to), so the
     * results are already there as a swipe starts bringing search in; for a blank query that's
     * only the in-memory frequent layer.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<SearchResults> =
        combine(
            _searchQuery,
            _categoryFilter,
            frequentLayer,
            frequentLayerVersion,
        ) { query, category, layer, version ->
            SearchInput(query, category, layer, version)
        }.transformLatest { input ->
            val cursor = frequentCursor?.takeIf { it.trie === input.layer }
                ?: input.layer.cursor().also { frequentCursor = it }
            val frequent = searchEngine.quickSearch(cursor, input.query, input.category)
            val blank = input.query.isBlank()
            // With nothing typed the disk layer normally stays out of it (only recents show). But
            // with the Apps category selected, list every app after the recents, so someone who
            // just wants to browse all their apps can.
            val listAll = blank && input.category == SearchCategory.APP
            if (blank && !listAll) {
                emit(SearchResults(frequent = frequent))
                return@transformLatest
            }
            emit(SearchResults(frequent = frequent, isSearchingDisk = true))
            delay(SearchLayersConfig.DISK_SEARCH_DELAY_MS)
            val shownIds = frequent.mapTo(mutableSetOf()) { it.id }
            val more = withContext(Dispatchers.IO) {
                val started = SystemClock.elapsedRealtime()
                // A blank query has nothing to rank against, so list everything A–Z instead.
                if (blank) {
                    searchEngine.listAll(input.category, excludeIds = shownIds).also {
                        Log.d(PERF_TAG, "listAll ${input.category}: ${it.size} in ${SystemClock.elapsedRealtime() - started} ms")
                    }
                } else {
                    searchEngine.deepSearch(input.query, input.category, excludeIds = shownIds).also {
                        Log.d(PERF_TAG, "deepSearch '${input.query}' ${input.category}: ${it.size} in ${SystemClock.elapsedRealtime() - started} ms")
                    }
                }
            }
            emit(SearchResults(frequent = frequent, more = more))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, SearchResults())

    private data class SearchInput(
        val query: String,
        val category: SearchCategory?,
        val layer: Trie<SearchEntry>,
        val layerVersion: Int,
    )

    /**
     * The time, read when the screen starts showing it and then only on the system's minute
     * tick (ACTION_TIME_TICK, sent as each minute starts), or when the clock, time zone or
     * 12/24-hour setting is changed. Nothing runs between minutes.
     */
    val time: StateFlow<String> = minuteChanges(application).map {
        val pattern = if (DateFormat.is24HourFormat(application)) {
            ClockConfig.TIME_PATTERN_24H
        } else {
            ClockConfig.TIME_PATTERN_12H
        }
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(ClockConfig.STOP_TICKING_AFTER_MS), "")

    /**
     * E.g. "Wednesday, 24 September", ordered for the device locale. Read when the screen starts
     * showing it, then at each local midnight, and never otherwise.
     */
    val date: StateFlow<String> = flow {
        while (true) {
            val locale = Locale.getDefault()
            val pattern = DateFormat.getBestDateTimePattern(locale, ClockConfig.DATE_SKELETON)
            emit(SimpleDateFormat(pattern, locale).format(Date()))
            // A wake a moment early (the timer doesn't follow wall-clock adjustments) only
            // re-reads the same date and waits the remaining moment.
            delay(millisUntilNextMidnight())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(ClockConfig.STOP_TICKING_AFTER_MS), "")

    init {
        SearchCategory.entries.forEach { accessSeen[it] = hasAccess(it) }
        // The index persists across launches, so the frequent layer is ready from it at once...
        viewModelScope.launch(indexUpdater.dispatcher) {
            _installedApps.value = appRepository.getInstalledApps()
            rebuildFrequentLayer()
            publishCategories()
        }
        // ...then the updater catches up on anything that changed while the launcher wasn't running,
        // and keeps the index current from then on.
        indexUpdater.start(viewModelScope)
        indexUpdater.requestSync(SearchCategory.entries, urgent = true)
        viewModelScope.launch {
            indexUpdater.updates.collect { update ->
                withContext(indexUpdater.dispatcher) {
                    update.installedApps?.let { _installedApps.value = it }
                    rebuildFrequentLayer()
                    publishCategories()
                }
            }
        }
        refreshFilesPrompt()
        // The first time the launcher ever opens, greet the user with the how-to card, then
        // remember it's been shown so it doesn't come back on its own.
        if (!settingsStore.onboardingSeen) {
            _isHowToOpen.value = true
            settingsStore.onboardingSeen = true
        }
    }

    override fun onCleared() {
        indexUpdater.stop()
    }

    /**
     * Whether to show the contacts permission dialog: only if it isn't granted and hasn't
     * been asked before, so declining doesn't bring it back on every search. After that
     * the user can still grant it in system Settings.
     */
    fun shouldRequestContactsPermission(): Boolean =
        !contactProvider.hasPermission() && !prefs.getBoolean(KEY_ASKED_CONTACTS, false)

    fun onContactsPermissionResult() {
        prefs.edit { putBoolean(KEY_ASKED_CONTACTS, true) }
        resyncIfAccessChanged()
    }

    /**
     * The launcher is visible ([started] true) or not. While it isn't, index changes are only
     * recorded; they're caught up on once it's back (and the write-ahead log is folded in as it
     * leaves).
     */
    fun onForegroundChanged(started: Boolean) {
        indexUpdater.setForeground(started)
    }

    /** The launcher came back to the foreground, e.g. from the "All files access" screen. */
    fun onResume() {
        refreshFilesPrompt()
        resyncIfAccessChanged()
    }

    fun dismissFilesPrompt() {
        prefs.edit { putBoolean(KEY_DISMISSED_FILES_PROMPT, true) }
        refreshFilesPrompt()
    }

    private fun refreshFilesPrompt() {
        _showFilesPrompt.value = !fileProvider.hasAccess() && !prefs.getBoolean(KEY_DISMISSED_FILES_PROMPT, false)
    }

    private fun hasAccess(category: SearchCategory): Boolean = when (category) {
        SearchCategory.APP -> true
        SearchCategory.CONTACT -> contactProvider.hasPermission()
        SearchCategory.FILE -> fileProvider.hasAccess()
    }

    /**
     * After a permission is granted (here or in system Settings) or revoked, the updater starts or
     * stops watching that source and re-syncs it straight away.
     */
    private fun resyncIfAccessChanged() {
        val changed = SearchCategory.entries.filter { accessSeen[it] != hasAccess(it) }
        if (changed.isEmpty()) return
        changed.forEach { accessSeen[it] = hasAccess(it) }
        indexUpdater.onAccessChanged(changed)
    }

    /** Re-ranks the frequent layer from history, keeping everything opened this session. */
    private suspend fun rebuildFrequentLayer() {
        val started = SystemClock.elapsedRealtime()
        val built = searchEngine.buildFrequentLayer(seed = _installedApps.value, pinned = openedThisSession.value)
        Log.d(PERF_TAG, "Frequent layer built in ${SystemClock.elapsedRealtime() - started} ms")
        layerMutex.withLock {
            // Anything opened while the rebuild ran is added before publishing. The new trie
            // isn't shared yet, so inserting here, off the main thread, is safe.
            openedThisSession.value.forEach { searchEngine.addToFrequentLayer(built, it) }
            frequentLayer.value = built
        }
    }

    private fun publishCategories() {
        val categories = searchEngine.availableCategories()
        _availableCategories.value = categories
        // The default stays even before its category is indexed, so it isn't lost at startup.
        val filter = _categoryFilter.value
        if (filter !in categories && filter != _defaultSearchCategory.value) _categoryFilter.value = null
    }

    /**
     * The user opened [entry] from search or the pinned sphere: remember it (for ranking and the
     * next frequent-layer build) and go back to the plain home screen.
     */
    fun onEntryOpened(entry: SearchEntry) {
        openedThisSession.update { opened -> if (opened.any { it.id == entry.id }) opened else opened + entry }
        // Joins the frequent layer now, in place on the main thread where the layer is searched,
        // so it answers instantly from the next keystroke on.
        viewModelScope.launch(Dispatchers.Main.immediate) {
            layerMutex.withLock {
                if (searchEngine.addToFrequentLayer(frequentLayer.value, entry)) frequentLayerVersion.update { it + 1 }
            }
        }
        viewModelScope.launch(Dispatchers.IO) { searchEngine.recordOpen(entry) }
        resetHome()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun openSearch() {
        _isPinPickerOpen.value = false
        _isSettingsOpen.value = false
        _searchPurpose.value = SearchPurpose.FIND
        _categoryFilter.value = _defaultSearchCategory.value
        _isSearchOpen.value = true
        // Whatever changed since it was last flushed is about to be searched.
        indexUpdater.flushNow()
        resyncIfAccessChanged()
    }

    /** Opens search limited to apps, so the user can choose the money button's app. */
    fun openMoneyAppPicker() {
        if (_settingsLocked.value) return
        _isPinPickerOpen.value = false
        _isSettingsOpen.value = false
        _searchPurpose.value = SearchPurpose.PICK_MONEY_APP
        _categoryFilter.value = SearchCategory.APP
        _isSearchOpen.value = true
        indexUpdater.flushNow()
    }

    /** Opens search limited to apps, so the user can choose the reminders button's app. */
    fun openRemindersAppPicker() {
        if (_settingsLocked.value) return
        _isPinPickerOpen.value = false
        _isSettingsOpen.value = false
        _searchPurpose.value = SearchPurpose.PICK_REMINDERS_APP
        _categoryFilter.value = SearchCategory.APP
        _isSearchOpen.value = true
        indexUpdater.flushNow()
    }

    /** The user chose [entry] while search was open for picking; assigns it and closes search. */
    fun onEntryPicked(entry: SearchEntry) {
        if (entry is AppInfo) {
            when (_searchPurpose.value) {
                SearchPurpose.PICK_MONEY_APP -> {
                    quickActionAppStore.save(QuickActionAppStore.MONEY_SLOT, entry.packageName)
                    _moneyAppPackage.value = entry.packageName
                }
                SearchPurpose.PICK_REMINDERS_APP -> {
                    quickActionAppStore.save(QuickActionAppStore.REMINDERS_SLOT, entry.packageName)
                    _remindersAppPackage.value = entry.packageName
                }
                SearchPurpose.FIND -> {}
            }
        }
        closeSearch()
    }

    fun clearMoneyApp() {
        if (_settingsLocked.value) return
        quickActionAppStore.clear(QuickActionAppStore.MONEY_SLOT)
        _moneyAppPackage.value = null
    }

    /** Forgets the chosen reminders app; the button reverts to the device's default. */
    fun clearRemindersApp() {
        if (_settingsLocked.value) return
        quickActionAppStore.clear(QuickActionAppStore.REMINDERS_SLOT)
        _remindersAppPackage.value = null
    }

    fun openPinPicker() {
        if (_settingsLocked.value) return
        closeSearch()
        _isSettingsOpen.value = false
        _isPinPickerOpen.value = true
    }

    fun closePinPicker() {
        _isPinPickerOpen.value = false
    }

    /** Opens the settings panel (from a home-screen long-press), closing any other overlay. */
    fun openSettings() {
        closeSearch()
        _isPinPickerOpen.value = false
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    /** Opens the "How to use this app" card, closing any other overlay (e.g. from the settings panel). */
    fun openHowTo() {
        closeSearch()
        _isPinPickerOpen.value = false
        _isSettingsOpen.value = false
        _isHowToOpen.value = true
    }

    fun closeHowTo() {
        _isHowToOpen.value = false
    }

    /** Locks or unlocks settings. The lock toggle itself stays usable when locked, so it can be turned off. */
    fun setSettingsLocked(locked: Boolean) {
        settingsStore.settingsLocked = locked
        _settingsLocked.value = locked
    }

    fun setDoubleTapToLock(enabled: Boolean) {
        settingsStore.doubleTapToLock = enabled
        _doubleTapToLock.value = enabled
    }

    /** Sets the haptic tick strength (0f–1f). Haptics read it on their next buzz, so it applies at once. */
    fun setHapticIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0f, 1f)
        settingsStore.hapticIntensity = clamped
        _hapticIntensity.value = clamped
    }

    /**
     * Turns the "reduce index size" setting on or off, then re-indexes files so it takes effect:
     * turning it on drops denylisted files from the index, turning it off brings them back.
     */
    fun setReduceIndexSize(enabled: Boolean) {
        if (_reduceIndexSize.value == enabled) return
        settingsStore.reduceIndexSize = enabled
        _reduceIndexSize.value = enabled
        indexUpdater.requestSync(listOf(SearchCategory.FILE), urgent = true)
    }

    /**
     * Pins [app] (at the end) or unpins it, saving immediately. Pinning does nothing once
     * [PinPickerConfig.MAX_PINNED] installed apps are pinned; the picker disables it then too.
     */
    fun togglePinned(app: AppInfo) {
        val pinning = app.packageName !in _pinnedPackages.value
        if (pinning && pinnedApps.value.size >= PinPickerConfig.MAX_PINNED) return
        val updated = _pinnedPackages.updateAndGet { packages ->
            if (pinning) packages + app.packageName else packages - app.packageName
        }
        pinnedAppsStore.save(updated)
    }

    /**
     * Puts the pinned apps in [order] (package names, as arranged in the picker), saving
     * immediately. Pins missing from [order] (e.g. uninstalled apps) keep their place after it.
     */
    fun reorderPinned(order: List<String>) {
        val updated = _pinnedPackages.updateAndGet { packages ->
            val reordered = order.filter { it in packages }
            reordered + (packages - reordered.toSet())
        }
        pinnedAppsStore.save(updated)
    }

    /** Back to the bare home screen: closes search, the pin picker, the settings panel and the how-to card. */
    fun resetHome() {
        closeSearch()
        closePinPicker()
        closeSettings()
        closeHowTo()
    }

    fun onCategoryFilterChanged(category: SearchCategory?) {
        // Picking an app for a button stays limited to apps.
        if (_searchPurpose.value != SearchPurpose.FIND) return
        _categoryFilter.value = category
    }

    fun closeSearch() {
        _isSearchOpen.value = false
        _searchPurpose.value = SearchPurpose.FIND
        _searchQuery.value = ""
        _categoryFilter.value = _defaultSearchCategory.value
    }

    /** Sets the category search opens filtered to (null for "All"); applies the next time search opens. */
    fun setDefaultSearchCategory(category: SearchCategory?) {
        settingsStore.defaultSearchCategory = category
        _defaultSearchCategory.value = category
    }

    private companion object {
        const val PREFS_NAME = "home"
        const val PERF_TAG = "Perf"
        const val KEY_ASKED_CONTACTS = "asked_contacts_permission"
        const val KEY_DISMISSED_FILES_PROMPT = "dismissed_files_prompt"
    }

    /**
     * Emits once on collection, then whenever the displayed minute may have changed: the system's
     * minute tick, or the clock, time zone or 12/24-hour setting being changed. The receiver is
     * registered only while collected.
     */
    private fun minuteChanges(context: Context): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // System broadcasts still arrive at a receiver that isn't exported.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate()

    /** Milliseconds from now to the next local midnight (at least 1). */
    private fun millisUntilNextMidnight(): Long {
        val now = Calendar.getInstance()
        val midnight = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return (midnight.timeInMillis - now.timeInMillis).coerceAtLeast(1)
    }
}
