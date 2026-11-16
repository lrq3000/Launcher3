/*
 * Copyright (C) 2026 The Android Open Source Project
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

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.os.UserHandle
import android.os.UserManager
import android.os.storage.StorageManager
import android.util.Log
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.availability.WorkspaceAvailabilityStore
import com.android.launcher3.model.availability.WorkspaceAvailabilityStore.Row
import com.android.launcher3.model.availability.WorkspaceAvailabilityStore.Storage
import com.android.launcher3.model.data.WorkspaceItemInfo
import java.io.File

/** Model-owned durable evidence. All callers are background workers; no callbacks or global observers are owned here. */
class WorkspaceAvailability(private val context: Context, private val db: ModelDbController) {
    private var revision = 0L
    private var failureReported = false

    @Synchronized
    fun getRevision(): Long = revision

    private val store by lazy {
        WorkspaceAvailabilityStore.open(File(context.noBackupFilesDir, "workspace-availability-v1"))
    }

    fun beginLoad() = store.beginBatch()

    fun endLoad() = store.endBatch()

    fun row(id: Int, serial: Long, component: ComponentName): Row? {
        if (serial < 0 || id < 0) return null
        return db.getAvailabilityIdentity()?.let {
            Row(it, serial, id, component.flattenToString())
        }
    }

    fun row(item: WorkspaceItemInfo): Row? =
        item.targetComponent?.let {
            row(
                item.id,
                context.getSystemService(UserManager::class.java).getSerialNumberForUser(item.user),
                it,
            )
        }

    fun shouldPreserve(row: Row?): Boolean = row == null || store.shouldPreserve(row)

    fun shouldPreserve(item: WorkspaceItemInfo): Boolean {
        val row = row(item)
        return !isRemoved(row) &&
            (shouldPreserve(row) ||
                item.runtimeStatusFlags and WorkspaceItemInfo.FLAG_DISABLED_NOT_AVAILABLE != 0)
    }

    fun isRemoved(row: Row?): Boolean = row != null && store.isRemoved(row)

    fun isRecovering(row: Row?): Boolean = row != null && store.isRecovering(row)

    fun protect(row: Row?) {
        if (row != null) store.protect(row)
        reportFailure()
    }

    fun protect(item: WorkspaceItemInfo) = protect(row(item))

    fun observe(row: Row?, activity: LauncherActivityInfo?) {
        if (row == null || activity == null ||
            activity.componentName.flattenToString() != row.component) return
        val app = activity.applicationInfo
        store.observe(row, storageOf(app), app.storageUuid?.toString())
        reportFailure()
    }

    private fun storageOf(app: ApplicationInfo): Storage =
        when {
            app.flags and ApplicationInfo.FLAG_EXTERNAL_STORAGE != 0 -> Storage.EXTERNAL
            app.storageUuid == StorageManager.UUID_DEFAULT -> Storage.INTERNAL
            app.storageUuid != null -> Storage.EXTERNAL
            else -> Storage.UNKNOWN
        }

    fun rememberStorage(row: Row?, app: ApplicationInfo?) {
        if (row == null || app == null) return
        // Persist the actual installation UUID, never the state of an unrelated mounted volume. Public/adopted volume associations that cannot be established remain unknown; even a matching mounted volume cannot prove target registration.
        store.classifyStorage(
            row.serial,
            row.component.substringBefore('/'),
            storageOf(app),
            app.storageUuid?.toString(),
        )
        reportFailure()
    }

    fun observe(item: WorkspaceItemInfo, activity: LauncherActivityInfo?) = observe(row(item), activity)

    @Synchronized
    fun unavailable(user: UserHandle, packages: Set<String>) {
        val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(user)
        if (serial < 0) return
        revision++
        packages.forEach { store.unavailable(serial, it) }
        reportFailure()
    }

    /** Snapshot before enqueue/discard, including rows that the in-memory model has not loaded yet. */
    @Synchronized
    fun removed(user: UserHandle, packages: Set<String>): Set<Row> {
        val serial = context.getSystemService(UserManager::class.java).getSerialNumberForUser(user)
        if (serial < 0) return emptySet()
        revision++
        val rows = mutableSetOf<Row>()
        try {
            forEachApplication { id, profile, intent, _ ->
                val component = intent.component
                if (profile == serial && component != null && component.packageName in packages) {
                    row(id, profile, component)?.let(rows::add)
                }
            }
        } catch (e: Exception) {
            // A failed snapshot cannot authorize package-wide deletion of ordinary favorites. Preserve any evidence acquired so far and allow the next loader to retry.
            Log.w("WorkspaceAvailability", "Cannot snapshot removed favorites; preserving unmatched rows", e)
        }
        store.removed(rows)
        reportFailure()
        return rows
    }

    /** Idempotent after process death between journal and favorites writes. No path reinserts rows. */
    @Synchronized
    fun reconcileRemovals(): Boolean {
        val removed = mutableListOf<Triple<Int, Long, String>>()
        forEachApplication { id, serial, intent, savedIntent ->
            val component = intent.component
            if (component != null && isRemoved(row(id, serial, component))) {
                removed.add(Triple(id, serial, savedIntent))
            }
        }
        var changed = false
        removed.forEach { (id, serial, intent) ->
            val deleted = db.delete(
                "${Favorites._ID}=? AND ${Favorites.PROFILE_ID}=? AND ${Favorites.INTENT}=?",
                arrayOf(id.toString(), serial.toString(), intent),
            )
            changed = changed || deleted > 0
        }
        return changed
    }

    private fun forEachApplication(consumer: (Int, Long, Intent, String) -> Unit) {
        db.query(
                arrayOf(Favorites._ID, Favorites.PROFILE_ID, Favorites.INTENT),
                "${Favorites.ITEM_TYPE}=? AND ${Favorites.RESTORED}=0",
                arrayOf(Favorites.ITEM_TYPE_APPLICATION.toString()),
                null,
            )
            .use { cursor ->
                while (cursor.moveToNext()) {
                    val savedIntent = cursor.getString(2)
                    val intent = runCatching { Intent.parseUri(savedIntent, 0) }.getOrNull()
                    if (intent != null) consumer(cursor.getInt(0), cursor.getLong(1), intent, savedIntent)
                }
            }
    }

    @Synchronized
    private fun reportFailure() {
        if (failureReported) return
        store.lastFailure?.let {
            failureReported = true
            Log.w("WorkspaceAvailability", "Availability metadata unavailable; preserving shortcuts", it)
        }
    }
}
