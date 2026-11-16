/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
import android.content.*
import android.os.UserHandle
import com.android.launcher3.LauncherModel
import com.android.launcher3.celllayout.CellPosMapper
import com.android.launcher3.model.*
import com.android.launcher3.ui.NoOpLauncherUiStateNotifier
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.views.ActivityContext
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor

fun main(args: Array<String>) {
    val root = Path.of(args[0])
    fun drain() { MODEL_EXECUTOR.drain(); MAIN_EXECUTOR.drain(); MODEL_EXECUTOR.drain() }
    val context = Context().apply { noBackupFilesDir = Files.createDirectory(root.resolve("queue-journal")).toFile() }
    val db = ModelDbController(context)
    val data = BgDataModel()
    val model = LauncherModel(db).apply { this.data = data }
    val writer = ModelWriter(context, model, data, CellPosMapper.DEFAULT,
        BgDataModel.ModificationSource.ModelTask, NoOpLauncherUiStateNotifier(), null, Executor { it.run() })
    val controller = ModelTaskController(context, writer)
    model.controller = controller
    fun queue() = ItemInstallQueue(context, { WorkspaceItemSpaceFinder() }, WorkspaceItemSerializer(), { model })
    var queue = queue()
    val surface = ActivityContext()
    queue.setIconUISurface(surface)
    val blocked = context.noBackupFilesDir.toPath().resolve("workspace-availability-v1.new")
    Files.createDirectory(blocked)
    val request = SerializedItemItem(0, UserHandle(10), Intent().setComponent(ComponentName("new.app", "Saved")))
    queue.queueItem(request)
    drain()
    check(controller.bound.isEmpty()) { "Install task bound a nonexistent favorite before persistence succeeded" }
    check(db.sql.favorites.isEmpty() && data.itemsIdMap.count() == 0) { "Failed install queue insertion became a ghost row" }
    val pending = context.noBackupFilesDir.toPath().resolve("apps_to_install")
    check(Files.exists(pending) && Files.readAllLines(pending).size == 1) { "Failed insertion consumed its durable install request" }
    Files.move(blocked, root.resolve("retained-queue-blocker"))
    // A new queue instance must reload the retained request; a later resume is the retry trigger, not a hot loop on storage failure.
    queue = queue()
    queue.setIconUISurface(surface)
    queue.resumeModelPush(ItemInstallQueue.FLAG_ACTIVITY_PAUSED)
    drain()
    check(db.sql.favorites.size == 1 && data.itemsIdMap.count() == 1 && controller.bound.size == 1)
    check(Files.readAllLines(pending).isEmpty()) { "Successful commit did not retire the processed queue batch" }
    // Repeated main-thread hops of one snapshot must not add a specialized item twice after the first commit retires it.
    queue.queueItem(SerializedItemItem(4, UserHandle(10), Intent().setComponent(ComponentName("widget.app", "Provider"))))
    queue.resumeModelPush(ItemInstallQueue.FLAG_ACTIVITY_PAUSED)
    drain()
    check(db.sql.favorites.size == 2 && controller.bound.size == 2) { "Stale queue snapshot duplicated a specialized item" }
    model.loaded = false
    queue.queueItem(SerializedItemItem(0, UserHandle(10), Intent().setComponent(ComponentName("later.app", "Saved"))))
    drain()
    check(Files.readAllLines(pending).size == 1)
    model.loaded = true
    queue.resumeModelPush(ItemInstallQueue.FLAG_ACTIVITY_PAUSED)
    drain()
    check(db.sql.favorites.size == 3 && controller.bound.size == 3 && Files.readAllLines(pending).isEmpty()) { "Discarded model task left queue permanently in flight" }
    println("PASS real install queue/task: no failed binding, retained disk request, restart/resume retry, retire after commit")
    println("PASS real install queue/task: stale-batch suppression and unloaded-task retry")
}
