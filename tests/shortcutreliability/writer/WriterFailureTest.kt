/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
import android.content.Context
import com.android.launcher3.LauncherModel
import com.android.launcher3.celllayout.CellPosMapper
import com.android.launcher3.model.*
import com.android.launcher3.model.data.*
import com.android.launcher3.ui.LauncherUiStateNotifier
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor

private class Notifier : LauncherUiStateNotifier {
    val changes = mutableListOf<IModelWriter.ChangeLog>()
    override fun addCallback(callback: BgDataModel.Callbacks) {}
    override fun removeCallback(callback: BgDataModel.Callbacks) {}
    override fun notifyItemModifiedOptimistically(item: ItemInfo) {}
    override fun notifyModelChanged(changeLog: IModelWriter.ChangeLog, owner: BgDataModel.Callbacks?) { changes.add(changeLog) }
}

fun main(args: Array<String>) {
    val root = Path.of(args[0])
    val tasks = ArrayDeque<Runnable>()
    val context = Context().apply { noBackupFilesDir = Files.createDirectory(root.resolve("writer-journal")).toFile() }
    val db = ModelDbController(context)
    val model = LauncherModel(db)
    val data = BgDataModel()
    val notifier = Notifier()
    val writer = ModelWriter(context, model, data, CellPosMapper.DEFAULT,
        BgDataModel.ModificationSource.ModelTask, notifier, null, Executor { tasks.add(it) })
    val blocked = context.noBackupFilesDir.toPath().resolve("workspace-availability-v1.new")
    Files.createDirectory(blocked)
    val item = WorkspaceItemInfo()
    writer.addItemToDatabase(item, -100, 0, 1, 1)
    try { tasks.removeFirst().run() } catch (failure: Exception) {
        throw AssertionError("Identity persistence failure escaped the real legacy ModelWriter scheduler", failure)
    }
    check(db.sql.favorites.isEmpty() && data.itemsIdMap.count() == 0 && notifier.changes.isEmpty()) { "Failed insertion published a ghost item" }
    check(model.reloads == 1) { "Legacy optimistic UI was not rolled back by reload" }
    Files.move(blocked, root.resolve("retained-blocker"))
    writer.addItemsToDatabase(listOf(item))
    tasks.removeFirst().run()
    check(db.sql.favorites.size == 1 && data.itemsIdMap.count() == 1 && notifier.changes.size == 1)
    notifier.changes.clear()
    var completed: Boolean? = null
    val first = WorkspaceItemInfo()
    val second = WorkspaceItemInfo()
    writer.scheduleTransaction(onComplete = { success, _ -> completed = success }) { transaction ->
        transaction.addItemToDatabase(first)
        Files.createDirectory(blocked)
        transaction.addItemToDatabase(second)
    }
    tasks.removeFirst().run()
    check(completed == false && db.sql.favorites.size == 1 && data.itemsIdMap.count() == 1 && notifier.changes.isEmpty()) { "Later insertion failure leaked earlier batch model/ChangeLog changes" }
    Files.move(blocked, root.resolve("retained-second-blocker"))
    db.sql.rejectInsert = true
    writer.addItemsToDatabase(listOf(WorkspaceItemInfo()))
    tasks.removeFirst().run()
    check(data.itemsIdMap.count() == 1 && notifier.changes.isEmpty()) { "Negative SQL insert result became optimistic success" }
    db.sql.rejectInsert = false
    db.sql.failClose = true
    writer.scheduleTransaction(onComplete = { success, _ -> completed = success }) { it.addItemToDatabase(WorkspaceItemInfo()) }
    tasks.removeFirst().run()
    check(completed == false && data.itemsIdMap.count() == 1 && notifier.changes.isEmpty()) { "Failed commit/close was reported as success" }
    db.sql.failClose = false
    Files.createDirectory(blocked)
    for (type in listOf(2, 4, 6)) {
        val specialized = android.content.ContentValues().apply { put("_id", 200 + type); put("itemType", type) }
        check(db.insert(specialized) == 200 + type) { "Specialized item acquired an application-journal write prerequisite" }
    }
    println("PASS real ModelWriter: handled legacy failure, retry, atomic batch, SQL rejection, commit failure")
    println("PASS real birth helper: folders, widgets and deep shortcuts insert with journal unwritable")
    val uiTasks = ArrayDeque<Runnable>()
    val delivery = com.android.launcher3.ui.DefaultLauncherUiStateNotifier(Executor { uiTasks.add(it) }, data, false, model)
    var delivered = 0
    delivery.addCallback(object : BgDataModel.Callbacks {
        override fun bindItemsAdded(items: List<ItemInfo>) { delivered += items.size }
        override fun bindItemsUpdated(items: Set<ItemInfo>) { delivered += items.size }
    })
    delivery.notifyModelChanged(IModelWriter.ChangeLog(itemsAdded = mutableListOf(item)), null)
    delivery.notifyItemModifiedOptimistically(item)
    data.bindingGuard.beginRemoval()
    data.bindingGuard.finishRemoval()
    data.itemsIdMap.bindingToken = data.bindingGuard.capture()
    context.servicesAllowed = false
    while (uiTasks.isNotEmpty()) uiTasks.removeFirst().run()
    check(delivered == 0) { "Queued notifier adopted a new generation at UI delivery" }
    delivery.notifyModelChanged(IModelWriter.ChangeLog(itemsAdded = mutableListOf(item)), null)
    delivery.notifyItemModifiedOptimistically(item)
    while (uiTasks.isNotEmpty()) uiTasks.removeFirst().run()
    check(delivered == 2)
    println("PASS real notifier delivery: old model/optimistic callbacks rejected; fresh query-free callbacks finish")
}
