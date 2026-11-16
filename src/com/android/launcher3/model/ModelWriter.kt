/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.model

import android.content.ContentValues
import android.content.Context
import android.util.Log
import com.android.launcher3.Flags
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.Utilities
import com.android.launcher3.celllayout.CellPosMapper
import com.android.launcher3.config.FeatureFlags
import com.android.launcher3.model.BgDataModel.Callbacks
import com.android.launcher3.model.BgDataModel.ModificationSource
import com.android.launcher3.model.IModelWriter.ChangeLog
import com.android.launcher3.model.data.CollectionInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.LauncherAppWidgetInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.provider.LauncherDbUtils.itemIdMatch
import com.android.launcher3.ui.DefaultLauncherUiStateNotifier
import com.android.launcher3.ui.LauncherUiStateNotifier
import com.android.launcher3.util.ContentWriter
import com.android.launcher3.util.Executors
import com.android.launcher3.widget.LauncherWidgetHolder
import java.util.concurrent.Executor
import java.util.function.Consumer
import java.util.function.Predicate

open class ModelWriter(
    private val context: Context,
    private val model: LauncherModel,
    private val bgDataModel: BgDataModel,
    private val cellPosMapper: CellPosMapper,
    private val modificationSource: ModificationSource,
    private val launcherStateNotifier: LauncherUiStateNotifier,
    private val owner: Callbacks?,
    protected val modelExecutor: Executor = Executors.MODEL_EXECUTOR,
) : IModelWriter {

    private interface QueuedTransaction {
        fun execute()
    }

    private var isSuspended = false
    private val transactionQueue = mutableListOf<QueuedTransaction>()

    open fun createTransactionContext(outChangeLog: ChangeLog): TransactionContext =
        TransactionContextImpl(outChangeLog, model, modificationSource, context, bgDataModel)

    open inner class TransactionContextImpl(
        private val outChangeLog: ChangeLog,
        private val model: LauncherModel,
        private val modificationSource: ModificationSource,
        private val context: Context,
        private val bgDataModel: BgDataModel,
    ) : TransactionContext {

        // SQL may still fail after an earlier operation in the block succeeded. Do not publish repository/model changes until the outer transaction has actually closed successfully.
        private val committedModelChanges = mutableListOf<() -> Unit>()
        private val pendingAdditions = mutableListOf<ItemInfo>()

        internal fun publishCommittedChanges() {
            committedModelChanges.forEach { it() }
            committedModelChanges.clear()
            pendingAdditions.clear()
        }

        override fun addItemToDatabase(item: ItemInfo) {
            this.addItemsToDatabase(listOf(item))
        }

        override fun addItemsToDatabase(items: List<ItemInfo>) {
            val additions = items.toList()
            additions.forEach {
                if (it.id == ItemInfo.NO_ID) {
                    it.id = model.modelDbController.generateNewItemId()
                }
            }
            val stackTrace = Throwable().stackTrace
            synchronized(bgDataModel) {
                additions.forEach { checkItemInfoLocked(it.id, it, stackTrace) }
            }
            for (item in additions) {
                val writer = ContentWriter(context)
                item.onAddToDatabase(writer)
                writer.put(Favorites._ID, item.id)
                check(model.modelDbController.insert(writer.getValues(context)) == item.id) {
                    "Favorite insertion failed for item ${item.id}"
                }
                if (item is WorkspaceItemInfo) model.workspaceAvailability.row(item)
            }
            pendingAdditions.addAll(additions)
            committedModelChanges.add {
                synchronized(bgDataModel) { bgDataModel.addItems(context, additions, modificationSource) }
                outChangeLog.itemsAdded.addAll(additions)
            }
        }

        override fun moveItemInDatabase(
            item: ItemInfo,
            container: Int,
            screenId: Int,
            cellX: Int,
            cellY: Int,
        ) {
            updateItemInDatabase(item)
        }

        override fun moveItemsInDatabase(items: List<ItemInfo>, container: Int, screen: Int) {
            outChangeLog.itemsModified.addAll(items)
            val contentValues =
                items.map { item ->
                    ContentValues().apply {
                        put(Favorites.CONTAINER, item.container)
                        put(Favorites.CELLX, item.cellX)
                        put(Favorites.CELLY, item.cellY)
                        put(Favorites.RANK, item.rank)
                        put(Favorites.SCREEN, item.screenId)
                    }
                }

            items.zip(contentValues).forEach { (item, value) ->
                model.modelDbController.update(value, itemIdMatch(item.id), null)
            }
            val updatedItems = items.toList()
            committedModelChanges.add { bgDataModel.updateItems(updatedItems, modificationSource) }
        }

        override fun modifyItemInDatabase(
            item: ItemInfo,
            container: Int,
            screenId: Int,
            cellX: Int,
            cellY: Int,
            spanX: Int,
            spanY: Int,
        ) {
            updateItemInDatabase(item)
        }

        override fun updateItemInDatabase(item: ItemInfo) {
            outChangeLog.itemsModified.add(item)
            val writer = ContentWriter(context)
            item.onAddToDatabase(writer)
            model.modelDbController.update(writer.getValues(context), itemIdMatch(item.id), null)
            committedModelChanges.add { bgDataModel.updateItems(listOf(item), modificationSource) }
        }

        override fun deleteItemsFromDatabase(matcher: Predicate<ItemInfo?>, reason: String?) {
            performDeleteItemsFromDatabase({ item -> matcher.test(item) }, reason)
        }

        private fun performDeleteItemsFromDatabase(
            matcher: (ItemInfo) -> Boolean,
            reason: String?,
        ) {
            val itemsToDelete = (bgDataModel.itemsIdMap.toList() + pendingAdditions)
                .distinctBy { it.id }.filter(matcher)
            this.deleteItemsFromDatabase(itemsToDelete, reason)
        }

        override fun deleteItemsFromDatabase(items: List<ItemInfo>, reason: String?) {
            Log.d(
                TAG,
                "removing items from db ${items.map { it.targetPackage ?: "" }}. Reason: [${reason ?: "unknown"}]",
            )
            outChangeLog.itemsRemoved.addAll(items)
            for (item in items) {
                model.modelDbController.delete(itemIdMatch(item.id), null)
            }
            val removedItems = items.toList()
            committedModelChanges.add { bgDataModel.removeItems(context, removedItems, modificationSource) }
        }

        override fun deleteCollectionAndContentsFromDatabase(info: CollectionInfo) {
            outChangeLog.itemsRemoved.add(info)
            model.modelDbController.delete(Favorites.CONTAINER + "=" + info.id, null)
            model.modelDbController.delete(Favorites._ID + "=" + info.id, null)
            val itemsToDelete = info.getContents() + info
            committedModelChanges.add { bgDataModel.removeItems(context, itemsToDelete, modificationSource) }
        }

        override fun deleteWidgetInfo(
            info: LauncherAppWidgetInfo,
            holder: LauncherWidgetHolder?,
            reason: String?,
        ) {
            outChangeLog.itemsRemoved.add(info)
            if (holder != null && !info.isCustomWidget && info.isWidgetIdAllocated) {
                holder.deleteAppWidgetId(info.appWidgetId)
            }
            this.deleteItemFromDatabase(info, reason)
        }

        override fun deleteAllItems() {
            model.modelDbController.createEmptyDB()
            committedModelChanges.add { bgDataModel.clear() }
        }
    }

    override fun <T> scheduleTransaction(
        onComplete: ((success: Boolean, result: T?) -> Unit)?,
        block: (TransactionContext) -> T,
    ) {
        if (isSuspended) {
            transactionQueue.add(
                object : QueuedTransaction {
                    override fun execute() {
                        scheduleTransaction(onComplete, block)
                    }
                }
            )
            return
        }

        // TODO(b/457449059): Should this be a submit() instead?
        modelExecutor.execute {
            var success = false
            var result: T? = null
            val outChangeLog = ChangeLog()
            val transactionContext = createTransactionContext(outChangeLog)
            try {
                model.modelDbController.newTransaction().use { t ->
                    result = block(transactionContext)
                    t.commit()
                }
                success = true
            } catch (e: Exception) {
                Log.e(TAG, "Transaction failed", e)
                result = null
            }

            if (success) {
                (transactionContext as? TransactionContextImpl)?.publishCommittedChanges()
                launcherStateNotifier.notifyModelChanged(outChangeLog, this.owner)
            }
            onComplete?.invoke(success, result)
        }
    }

    override fun suspendWrites() {
        isSuspended = true
    }

    override fun resumeWrites(
        pendingTransaction: Consumer<TransactionContext>?,
        discardPending: Boolean,
    ) {
        isSuspended = false

        if (pendingTransaction != null) {
            scheduleTransaction(null) { pendingTransaction.accept(it) }
        }

        val queue = transactionQueue.toList()
        transactionQueue.clear()

        if (!discardPending) {
            queue.forEach { it.execute() }
        }
    }

    override fun getNotifier(): LauncherUiStateNotifier = launcherStateNotifier

    /** Updates the location properties of the item */
    fun updateItemModel(item: ItemInfo, container: Int, screenId: Int, cellX: Int, cellY: Int) {
        val modelPos = cellPosMapper.mapPresenterToModel(cellX, cellY, screenId, container)
        item.container = container
        item.cellX = modelPos.cellX
        item.cellY = modelPos.cellY
        item.screenId = modelPos.screenId
    }

    /**
     * Adds an item to the DB if it was not created previously, or move it to a new
     * <container></container>, screen, cellX, cellY>
     */
    override fun addOrMoveItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ) {
        updateItemModel(item, container, screenId, cellX, cellY)
        if (item.id == ItemInfo.NO_ID) {
            item.id = model.modelDbController.generateNewItemId()
            executeAddition { it.addItemToDatabase(item) }
        } else {
            execute { it.moveItemInDatabase(item, container, screenId, cellX, cellY) }
        }
    }

    private fun execute(block: (TransactionContext) -> Unit) {
        if (Flags.enableTransactionalModelWriter() || isSuspended) {
            scheduleTransaction(block = block)
        } else {
            modelExecutor.execute {
                val changeLog = ChangeLog()
                val transactionContext = createTransactionContext(changeLog)
                block(transactionContext)
                (transactionContext as? TransactionContextImpl)?.publishCommittedChanges()
                launcherStateNotifier.notifyModelChanged(changeLog, this.owner)
            }
        }
    }

    /** Legacy add APIs have optimistic callers and no failure result. Always use the caught, atomic scheduler and restore their UI from committed favorites on failure, regardless of the writer feature flag. */
    private fun executeAddition(block: (TransactionContext) -> Unit) {
        scheduleTransaction(onComplete = { success, _ ->
            if (!success) model.forceReload("favorite-insertion-failed")
        }, block = block)
    }

    private fun checkItemInfoLocked(
        itemId: Int,
        item: ItemInfo,
        stackTrace: Array<StackTraceElement>,
    ) {
        val modelItem = bgDataModel.itemsIdMap[itemId]
        if (modelItem != null && item !== modelItem) {
            // check all the data is consistent
            if (
                !Utilities.IS_DEBUG_DEVICE &&
                    !FeatureFlags.IS_STUDIO_BUILD &&
                    modelItem is WorkspaceItemInfo &&
                    item is WorkspaceItemInfo
            ) {
                if (
                    modelItem.title.toString() == item.title.toString() &&
                        modelItem.getIntent().filterEquals(item.getIntent()) &&
                        modelItem.id == item.id &&
                        modelItem.itemType == item.itemType &&
                        modelItem.container == item.container &&
                        modelItem.screenId == item.screenId &&
                        modelItem.cellX == item.cellX &&
                        modelItem.cellY == item.cellY &&
                        modelItem.spanX == item.spanX &&
                        modelItem.spanY == item.spanY
                ) {
                    // For all intents and purposes, this is the same object
                    return
                }
            }

            // the modelItem needs to match up perfectly with item if our model is
            // to be consistent with the database-- for now, just require
            // modelItem == item or the equality check above
            val e =
                RuntimeException(
                    "item: $item modelItem: $modelItem Error: ItemInfo passed to checkItemInfo doesn't match original"
                )
            e.stackTrace = stackTrace
            throw e
        }
    }

    /**
     * Move an item in the DB to a new <container></container>, screen, cellX, cellY>
     *
     * TODO(b/457449059): Remove this method.
     */
    override fun moveItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ) {
        updateItemModel(item, container, screenId, cellX, cellY)
        execute { it.moveItemInDatabase(item, container, screenId, cellX, cellY) }
    }

    /**
     * Move items in the DB to a new <container></container>, screen, cellX, cellY>. We assume that
     * the cellX, cellY have already been updated on the ItemInfos.
     *
     * TODO(b/457449059): Remove this method.
     */
    override fun moveItemsInDatabase(items: List<ItemInfo>, container: Int, screen: Int) {
        items.forEach { updateItemModel(it, container, screen, it.cellX, it.cellY) }
        execute { it.moveItemsInDatabase(items, container, screen) }
    }

    /**
     * Move and/or resize item in the DB to a new <container></container>, screen, cellX, cellY,
     * spanX, spanY>
     *
     * TODO(b/457449059): Remove this method.
     */
    override fun modifyItemInDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
        spanX: Int,
        spanY: Int,
    ) {
        updateItemModel(item, container, screenId, cellX, cellY)
        item.spanX = spanX
        item.spanY = spanY
        execute { it.modifyItemInDatabase(item, container, screenId, cellX, cellY, spanX, spanY) }
    }

    /**
     * Update an item to the database in a specified container.
     *
     * TODO(b/457449059): Remove this method.
     */
    override fun updateItemInDatabase(item: ItemInfo) {
        execute { it.updateItemInDatabase(item) }
    }

    /**
     * Add an item to the database in a specified container. Sets the container, screen, cellX and
     * cellY fields of the item. Also assigns an ID to the item.
     *
     * TODO(b/457449059): Remove this method.
     */
    override fun addItemToDatabase(
        item: ItemInfo,
        container: Int,
        screenId: Int,
        cellX: Int,
        cellY: Int,
    ) {
        updateItemModel(item, container, screenId, cellX, cellY)
        if (item.id == ItemInfo.NO_ID) {
            item.id = model.modelDbController.generateNewItemId()
        }
        executeAddition { it.addItemToDatabase(item) }
    }

    /**
     * Add provided items to the database. Also assigns an ID to each item.
     *
     * TODO(b/457449059): Remove this method.
     */
    override fun addItemsToDatabase(items: List<ItemInfo>) {
        items.forEach {
            if (it.id == ItemInfo.NO_ID) {
                it.id = model.modelDbController.generateNewItemId()
            }
        }
        executeAddition { it.addItemsToDatabase(items) }
    }

    /**
     * Removes the specified item from the database
     *
     * TODO(b/457449059): Remove this method.
     */
    override fun deleteItemFromDatabase(item: ItemInfo, reason: String?) {
        execute { it.deleteItemFromDatabase(item, reason) }
    }

    /**
     * Removes all the items from the database matching {@param matcher}.
     *
     * TODO(b/457449059): Remove this method.
     */
    override fun deleteItemsFromDatabase(matcher: Predicate<ItemInfo?>, reason: String?) {
        execute { it.deleteItemsFromDatabase(matcher, reason) }
    }

    /**
     * Removes the specified items from the database
     *
     * TODO(b/457449059): Remove this method.
     */
    override fun deleteItemsFromDatabase(items: List<ItemInfo>, reason: String?) {
        execute { it.deleteItemsFromDatabase(items, reason) }
    }

    /**
     * Remove the specified folder and all its contents from the database.
     *
     * TODO(b/457449059): Remove this method.
     */
    override fun deleteCollectionAndContentsFromDatabase(info: CollectionInfo) {
        execute { it.deleteCollectionAndContentsFromDatabase(info) }
    }

    /**
     * Deletes the widget info and the widget id.
     *
     * TODO(b/457449059): Remove this method.
     */
    override fun deleteWidgetInfo(
        info: LauncherAppWidgetInfo,
        holder: LauncherWidgetHolder?,
        reason: String?,
    ) {
        execute { it.deleteWidgetInfo(info, holder, reason) }
    }

    private abstract inner class ModelTask : Runnable {
        private val loadId = bgDataModel.lastLoadId

        override fun run() {
            if (loadId != model.lastLoadId) {
                Log.d(TAG, "Model changed before the task could execute")
                return
            }
            runImpl()
        }

        fun executeOnModelThread() {
            modelExecutor.execute(this)
        }

        abstract fun runImpl()
    }

    private fun newModelTask(r: Runnable): ModelTask {
        return object : ModelTask() {
            override fun runImpl() {
                r.run()
            }
        }
    }

    companion object {
        private const val TAG = "ModelWriter"
    }
}
