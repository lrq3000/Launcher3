/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
import android.content.Context
import android.util.SparseArray
import com.android.launcher3.*
import com.android.launcher3.model.*
import com.android.launcher3.model.data.*
import com.android.launcher3.util.Executors.*

private fun snapshot(guard: Any?, vararg ids: Int): WorkspaceData {
    val mutable = WorkspaceData.MutableWorkspaceData()
    val items = SparseArray<ItemInfo>()
    ids.forEach { id -> items.put(id, ItemInfo().apply { this.id = id; container = -100; screenId = if (id % 2 == 0) 0 else 1 }) }
    val replace = mutable.javaClass.methods.firstOrNull { it.name == "replaceDataMap" && it.parameterCount == 2 }
    if (guard != null && replace != null) replace.invoke(mutable, items, guard.javaClass.getMethod("capture").invoke(guard))
    else mutable.replaceDataMap(items)
    return mutable.copy()
}

fun main(args: Array<String>) {
    val modes = if (args.size > 1) listOf(args[1]) else listOf("legacy", "simplified", "repository")
    for (mode in modes) {
      for (phase in listOf("blocked-ui", "inflation", "deferred", "late-entry")) {
        val guard = try { Class.forName("com.android.launcher3.model.WorkspaceBindingGuard").getConstructor().newInstance() } catch (_: ClassNotFoundException) { null }
        val old = snapshot(guard, 42, 43)
        val launcher = Launcher()
        val callback = ExtractedCallbacks(launcher)
        Flags.simplified = mode != "legacy"
        val data = BgDataModel(old)
        val context = Context().apply { servicesAllowed = false }
        val binder = BaseLauncherBinder(context, LauncherModel(), data, AllAppsList(), arrayOf(callback))
        fun invalidate() {
            guard?.javaClass?.getMethod("beginRemoval")?.invoke(guard)
            guard?.javaClass?.getMethod("finishRemoval")?.invoke(guard)
        }
        var freshAlreadyEntered = false
        if (phase == "inflation") launcher.onInflate = Runnable { launcher.onInflate = Runnable {}; invalidate() }
        if (phase == "late-entry") launcher.onEntry = Runnable {
            launcher.onEntry = Runnable {}
            invalidate()
            val fresh = snapshot(guard, 44, 45)
            if (mode == "repository") callback.bindRepositorySnapshot(fresh) else {
                data.itemsIdMap = fresh
                BaseLauncherBinder(context, LauncherModel(), data, AllAppsList(), arrayOf(callback))
                    .bindWorkspace(true, false)
            }
            freshAlreadyEntered = true
        }
        if (mode == "repository") callback.bindRepositorySnapshot(old) else binder.bindWorkspace(true, false)
        // Receipt while main/UI-helper/model inflation queues are blocked. No new binder has entered yet.
        if (phase == "deferred") {
            MAIN_EXECUTOR.drain()
            if (mode != "legacy") { UI_HELPER_EXECUTOR.drain(); MAIN_EXECUTOR.drain() }
        }
        if (phase == "blocked-ui" || phase == "deferred") invalidate()
        launcher.visible.clear() // A removal callback may already have cleared previously bound views.
        fun drain() { repeat(4) { MAIN_EXECUTOR.drain(); UI_HELPER_EXECUTOR.drain(); MODEL_EXECUTOR.drain() } }
        drain()
        launcher.pending.toList().forEach { it.executeAllAndDestroy() }
        check(42 !in launcher.visible && 43 !in launcher.visible) { "$mode/$phase delivered a removed item from an old async snapshot after receipt" }
        if (!freshAlreadyEntered) check(launcher.finished == 0 && ItemInstallQueue.resumes == 0) { "$mode/$phase stale completion resumed install queue" }
        if (freshAlreadyEntered) {
            check(launcher.visible.toSet() == setOf(44, 45) && launcher.finished == 1) { "$mode late old entry cancelled the fresh session" }
            launcher.visible.clear(); launcher.finished = 0; ItemInstallQueue.resumes = 0
        }
        val fresh = snapshot(guard, 44, 45)
        if (mode == "repository") callback.bindRepositorySnapshot(fresh) else {
            data.itemsIdMap = fresh
            BaseLauncherBinder(context, LauncherModel(), data, AllAppsList(), arrayOf(callback)).bindWorkspace(true, false)
        }
        drain()
        check(launcher.visible.toSet() == setOf(44, 45) && launcher.finished == 1) { "$mode fresh binding did not finish healthy items" }
        if (mode == "legacy") check(ItemInstallQueue.resumes == 1)
        ItemInstallQueue.resumes = 0
        println("PASS real async delivery: $mode/$phase invalidation, query-free delivery and fresh completion")
      }
    }
    val guardClass = Class.forName("com.android.launcher3.model.WorkspaceBindingGuard")
    val guard = guardClass.getConstructor().newInstance()
    val begin = guardClass.methods.firstOrNull { it.name == "beginRemoval" }
    begin?.invoke(guard)
    val during = snapshot(guard, 42)
    val token = during.javaClass.getMethod("getBindingToken").invoke(during)
    check(!(token.javaClass.getMethod("isCurrent").invoke(token) as Boolean)) {
        "Snapshot captured after receipt but before journaling adopted the new generation"
    }
    guardClass.getMethod("finishRemoval").invoke(guard)
    check(!(token.javaClass.getMethod("isCurrent").invoke(token) as Boolean)) { "Pending snapshot became authorized when journaling finished" }
    val fresh = snapshot(guard, 44).let { it.javaClass.getMethod("getBindingToken").invoke(it) }
    check(fresh.javaClass.getMethod("isCurrent").invoke(fresh) as Boolean)
    begin!!.invoke(guard)
    begin.invoke(guard)
    guardClass.getMethod("finishRemoval").invoke(guard)
    val overlapping = snapshot(guard, 46).let { it.javaClass.getMethod("getBindingToken").invoke(it) }
    check(!(overlapping.javaClass.getMethod("isCurrent").invoke(overlapping) as Boolean)) { "First completed removal reopened an overlapping receipt barrier" }
    guardClass.getMethod("finishRemoval").invoke(guard)
    check(!(overlapping.javaClass.getMethod("isCurrent").invoke(overlapping) as Boolean))
    println("PASS receipt/journal barrier: intervening snapshots stay invalid; post-record snapshot is fresh")
    Flags.simplified = true
    val before = guardClass.getMethod("capture").invoke(guard) as WorkspaceBindingGuard.Token
    ExtractedLoaderCompletion(before).commit() // Queue resume has been posted, but UI remains blocked.
    begin.invoke(guard)
    guardClass.getMethod("finishRemoval").invoke(guard)
    MAIN_EXECUTOR.drain()
    check(ItemInstallQueue.resumes == 0) { "Old loader completion resumed queue after uninstall receipt" }
    ExtractedLoaderCompletion(before).commit() // A commit which itself arrives late must be inert too.
    MAIN_EXECUTOR.drain()
    check(ItemInstallQueue.resumes == 0)
    ExtractedLoaderCompletion(guardClass.getMethod("capture").invoke(guard) as WorkspaceBindingGuard.Token).commit()
    MAIN_EXECUTOR.drain()
    check(ItemInstallQueue.resumes == 1) { "Fresh simplified/repository loader did not resume the install queue" }
    ItemInstallQueue.resumes = 0
    println("PASS real loader completion: queued and late stale resumes rejected; fresh queue resume finishes")
}
