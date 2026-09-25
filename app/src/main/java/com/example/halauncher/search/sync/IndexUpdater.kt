package com.example.halauncher.search.sync

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import com.example.halauncher.config.IndexUpdaterConfig
import com.example.halauncher.data.AppRepository
import com.example.halauncher.data.ContactProvider
import com.example.halauncher.data.FileSearchProvider
import com.example.halauncher.model.AppInfo
import com.example.halauncher.model.FileEntry
import com.example.halauncher.model.SearchCategory
import com.example.halauncher.search.SearchEngine
import com.example.halauncher.search.index.IndexChange
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What a flush changed, for whoever keeps derived state (the frequent layer, the app list). */
data class IndexUpdate(
    val changedCategories: Set<SearchCategory>,
    /** The freshly loaded app list, when apps were re-read (even if the index didn't change). */
    val installedApps: List<AppInfo>?,
)

/**
 * Keeps the disk search index up to date in the background.
 *
 * 1. **Low priority.** Everything runs on one dedicated thread at
 *    [IndexUpdaterConfig.THREAD_PRIORITY] (background by default), so the scheduler serves
 *    the UI and other apps first. Being one thread also means index writes never race.
 * 2. **Debounced, buffered, bulk.** [IndexChangeWatcher] records changes in [PendingChanges]. The
 *    loop waits until they've been quiet for a moment (see [flushDelayMillis]), then loads
 *    what changed and applies it all as one transaction.
 * 3. **Write-ahead log.** That transaction is non-exclusive on a WAL database, so searches keep
 *    reading throughout; a passive checkpoint afterwards keeps the log small.
 * 4. **Battery and heat.** [ResourceGuard] slows the loop down or pauses it. While paused, changes
 *    keep buffering and are flushed once conditions allow. A batch interrupted mid-way goes back
 *    into the buffer.
 * 5. **Only while the launcher is in front.** While it's stopped (another app is open, or the
 *    screen is off) changes are only recorded; nothing is read or written until it's started
 *    again ([setForeground]), when they're caught up on in one batch. [flushNow] catches up
 *    straight away, without the debounce, e.g. as search opens.
 * 6. **Write-ahead log folded in the background.** The log is checkpointed as the launcher goes
 *    to the background (see [IndexUpdaterConfig.CHECKPOINT_ON_BACKGROUND]), not after each flush.
 *
 * Each flush publishes an [IndexUpdate] on [updates].
 */
class IndexUpdater(
    context: Context,
    private val engine: SearchEngine,
    private val apps: AppRepository,
    private val contacts: ContactProvider,
    private val files: FileSearchProvider,
) {
    private val thread = HandlerThread(THREAD_NAME, IndexUpdaterConfig.THREAD_PRIORITY).apply { start() }
    private val handler = Handler(thread.looper)

    /** The updater's thread as a coroutine dispatcher; also used for work that must not race it. */
    val dispatcher: CoroutineDispatcher = Executor { handler.post(it) }.asCoroutineDispatcher()

    private val pending = PendingChanges()
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val guard = ResourceGuard(context)
    private val watcher = IndexChangeWatcher(
        context = context,
        handler = handler,
        pending = pending,
        canReadContacts = contacts::hasPermission,
        canReadFiles = files::hasAccess,
        onChange = ::wake,
    )

    private val _updates = MutableSharedFlow<IndexUpdate>(extraBufferCapacity = UPDATE_BUFFER)
    val updates: SharedFlow<IndexUpdate> = _updates.asSharedFlow()

    private var loop: Job? = null
    private var scope: CoroutineScope? = null

    /** Whether the launcher is started (visible); flushes wait for it. */
    private val foreground = MutableStateFlow(false)

    /** Starts watching for changes and processing them, until [stop] or [scope] ends. */
    fun start(scope: CoroutineScope) {
        if (loop != null) return
        this.scope = scope
        handler.post {
            guard.start(handler)
            watcher.start()
        }
        loop = scope.launch(dispatcher) { runLoop() }
    }

    /**
     * Asks for full re-syncs of [categories]. [urgent] ones (user-initiated, or catching up at
     * startup) skip the debounce wait and may run under battery saver.
     */
    fun requestSync(categories: Collection<SearchCategory>, urgent: Boolean) {
        categories.forEach { pending.requestFullSync(it, urgent) }
        wake()
    }

    /**
     * The launcher was started ([started] true) or stopped. Stopped, changes are only recorded;
     * started again, they're flushed. Going to the background also checkpoints the write-ahead log.
     */
    fun setForeground(started: Boolean) {
        Log.d(TAG, if (started) "Foreground: flushing allowed" else "Background: recording only")
        foreground.value = started
        if (!started && IndexUpdaterConfig.CHECKPOINT_ON_BACKGROUND) {
            scope?.launch(dispatcher) {
                val begun = SystemClock.elapsedRealtime()
                engine.checkpoint()
                Log.d(TAG, "Checkpointed in ${SystemClock.elapsedRealtime() - begun} ms")
            }
        }
    }

    /** Flushes whatever is pending now, skipping the debounce wait (e.g. search is opening). */
    fun flushNow() {
        pending.markUrgent()
        wake()
    }

    /** A permission changed: observe what's now readable and re-sync those categories now. */
    fun onAccessChanged(categories: Collection<SearchCategory>) {
        handler.post { watcher.refresh() }
        requestSync(categories, urgent = true)
    }

    fun stop() {
        loop?.cancel()
        loop = null
        handler.post {
            watcher.stop()
            guard.stop()
            thread.quitSafely()
        }
    }

    private fun wake() {
        wakeups.trySend(Unit)
    }

    private suspend fun runLoop() {
        while (currentCoroutineContext().isActive) {
            if (pending.isEmpty) wakeups.receive()

            // Debounce: keep collecting until the burst has been quiet long enough (or hit its cap).
            while (true) {
                val burst = pending.timing() ?: break
                val wait = flushDelayMillis(System.currentTimeMillis(), burst, guard.state.value.throttle)
                if (wait <= 0) break
                withTimeoutOrNull(wait) { wakeups.receive() }
            }

            // Guardrails: while in the background or paused, events keep buffering. Re-check
            // whenever conditions change or a new event arrives (it may be urgent, which battery
            // saver doesn't hold back).
            if (pending.isEmpty) continue
            if (!mayRunPending()) Log.i(TAG, "Holding: foreground=${foreground.value}, ${guard.state.value}")
            while (!mayRunPending()) {
                val urgent = pending.timing()?.urgent ?: false
                val allowed = combine(guard.state, foreground) { state, started -> started && state.allows(urgent) }
                merge(allowed.filter { it }.map { }, wakeups.receiveAsFlow()).first()
            }

            val batch = pending.drain() ?: continue
            if (!flush(batch)) {
                pending.restore(batch)
                Log.i(TAG, "Flush interrupted (${guard.state.value}); batch put back")
            }
        }
    }

    private fun mayRunPending(): Boolean =
        foreground.value && guard.state.value.allows(pending.timing()?.urgent ?: false)

    /**
     * Loads everything in [batch] from its sources, then writes it as one transaction. Conditions
     * (including still being in front) are checked before each source is read.
     *
     * @return false if conditions turned against running part-way (nothing has been written then).
     */
    private suspend fun flush(batch: Batch): Boolean {
        val started = SystemClock.elapsedRealtime()
        val changes = mutableListOf<IndexChange>()
        var reloadedApps: List<AppInfo>? = null

        val loads = buildList<() -> Unit> {
            if (SearchCategory.APP in batch.fullSync) add {
                apps.invalidate(batch.changedPackages)
                val loaded = apps.getInstalledApps()
                reloadedApps = loaded
                changes += IndexChange.ReplaceAll(SearchCategory.APP, loaded)
            }
            if (SearchCategory.CONTACT in batch.fullSync) add {
                changes += IndexChange.ReplaceAll(SearchCategory.CONTACT, contacts.loadEntries())
            }
            if (SearchCategory.FILE in batch.fullSync) {
                add { changes += IndexChange.ReplaceAll(SearchCategory.FILE, files.loadEntries()) }
            } else if (batch.fileIds.isNotEmpty()) add {
                // Files that still exist are refreshed; ids MediaStore no longer has were deleted.
                // Denylisted files are neither indexed nor removed: they exist, so they don't count
                // as deletions, and they're absent from entries, so they aren't upserted.
                val targeted = files.loadByIds(batch.fileIds)
                changes += IndexChange.Upsert(SearchCategory.FILE, targeted.entries)
                changes += IndexChange.Remove(
                    SearchCategory.FILE,
                    batch.fileIds.filter { it !in targeted.existingIds }.map(FileEntry::idFor),
                )
            }
        }
        for (load in loads) {
            val state = guard.state.value
            if (!state.allows(batch.urgent) || !foreground.value) return false
            load()
            if (state.throttle == Throttle.SLOW) delay(IndexUpdaterConfig.SLOW_MODE_STEP_PAUSE_MS)
        }

        val results = engine.applyChanges(changes)
        val changed = results.filterValues { it.changedAnything }.keys
        Log.d(
            TAG,
            "Flushed ${describe(batch)} in ${SystemClock.elapsedRealtime() - started} ms: " +
                results.entries.joinToString { (category, r) -> "$category +${r.inserted} ~${r.updated} -${r.removed}" },
        )
        if (changed.isNotEmpty() || reloadedApps != null) _updates.emit(IndexUpdate(changed, reloadedApps))
        return true
    }

    private fun describe(batch: Batch): String = buildList {
        if (batch.fullSync.isNotEmpty()) add("full ${batch.fullSync.joinToString()}")
        if (batch.fileIds.isNotEmpty()) add("${batch.fileIds.size} file(s)")
    }.joinToString(", ")

    private companion object {
        const val TAG = "IndexUpdater"
        const val THREAD_NAME = "IndexUpdater"
        const val UPDATE_BUFFER = 8
    }
}
