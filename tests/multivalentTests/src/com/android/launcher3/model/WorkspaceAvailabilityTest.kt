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
import android.content.ContentValues
import android.content.Intent
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.model.availability.WorkspaceAvailabilityDb
import com.android.launcher3.model.availability.WorkspaceAvailabilityStore
import com.android.launcher3.provider.LauncherDbUtils
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

/** Real SQLite/favorites integration; unlike the standalone boundary tests this requires the AOSP test target. */
@RunWith(AndroidJUnit4::class)
class WorkspaceAvailabilityTest {
    @get:Rule val context = SandboxApplication().withModelDependency()

    @Test
    fun deletedHighestIdCanBeReusedAfterReopenWithoutInheritingUninstall() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            val user = Process.myUserHandle()
            val serial = context.appComponent.userCache.getSerialNumberForUser(user)
            val component = ComponentName("test.sd.app", "SavedActivity")
            val store = WorkspaceAvailabilityDb.store(context)
            fun values(id: Int) = ContentValues().apply {
                put(Favorites._ID, id)
                put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                put(Favorites.PROFILE_ID, serial)
                put(Favorites.RESTORED, 0)
                put(Favorites.INTENT, Intent(Intent.ACTION_MAIN).setComponent(component).toUri(0))
            }
            val first = DatabaseHelper(context, "availability-id-reuse.db") {}
            first.initIds()
            val oldId = first.generateNewItemId()
            assertThat(first.insertAndCheck(first.writableDatabase, values(oldId))).isEqualTo(oldId)
            val oldRow = WorkspaceAvailabilityDb.row(first.writableDatabase, oldId, serial, component.flattenToString())
            store.beginRemoval(serial, setOf(component.packageName))
            store.removed(setOf(oldRow))
            first.writableDatabase.delete(Favorites.TABLE_NAME, "${Favorites._ID}=?", arrayOf(oldId.toString()))
            first.close()

            val reopened = DatabaseHelper(context, "availability-id-reuse.db") {}
            try {
                reopened.initIds()
                val replacementId = reopened.generateNewItemId()
                // Exercise the real allocator, not a manually chosen distinct replacement ID.
                assertThat(replacementId).isEqualTo(oldId)
                assertThat(reopened.insertAndCheck(reopened.writableDatabase, values(replacementId))).isEqualTo(replacementId)
                val replacement = WorkspaceAvailabilityDb.row(reopened.writableDatabase, replacementId, serial, component.flattenToString())
                assertThat(replacement.generation).isNotEqualTo(oldRow.generation)
                assertThat(store.isRemoved(replacement)).isFalse()
                assertThat(store.isRemoved(oldRow)).isTrue()
                assertThat(reopened.writableDatabase.query(Favorites.TABLE_NAME, null, null, null, null, null, null).use { it.count }).isEqualTo(1)
            } finally {
                reopened.close()
            }
        }
    }

    @Test
    fun failedSnapshotBeforeDiscardSurvivesDiskReloadWithoutRemovingLaterInsertion() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            val real = context.appComponent.testableModelState.dbController
            real.createEmptyDB()
            val controller = spy(real)
            val user = Process.myUserHandle()
            val serial = context.appComponent.userCache.getSerialNumberForUser(user)
            val intent = Intent(Intent.ACTION_MAIN).setComponent(ComponentName("test.sd.app", "Saved")).toUri(0)
            fun insert(): Int {
                val id = controller.generateNewItemId()
                controller.insert(ContentValues().apply {
                    put(Favorites._ID, id)
                    put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                    put(Favorites.RESTORED, 0)
                    put(Favorites.PROFILE_ID, serial)
                    put(Favorites.INTENT, intent)
                })
                return id
            }
            val old = insert()
            var failing = true
            doAnswer { invocation ->
                if (failing) throw IllegalStateException("simulated snapshot failure")
                invocation.callRealMethod()
            }.whenever(controller).query(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
            val availability = WorkspaceAvailability(context, controller)
            val callbacks = ModelLauncherCallbacks(java.util.function.Consumer { }, availability)
            callbacks.onPackageRemoved("test.sd.app", user)
            callbacks.onPackageAdded("test.sd.app", user)
            val replacement = insert()
            // Forget only in-process ownership, as a process restart would. The actual journal and SQLite data remain on disk.
            val owners = WorkspaceAvailabilityStore::class.java.getDeclaredField("OWNERS")
            owners.isAccessible = true
            (owners.get(null) as MutableMap<*, *>).clear()
            failing = false
            val restarted = WorkspaceAvailability(context, real)
            assertThat(restarted.reconcileRemovals()).isTrue()
            real.query(arrayOf(Favorites._ID), null, null, null).use { cursor ->
                assertThat(cursor.count).isEqualTo(1)
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getInt(0)).isEqualTo(replacement)
                assertThat(cursor.getInt(0)).isNotEqualTo(old)
            }
        }
    }

    @Test
    fun favoritesAndBirthCopyRollBackTogetherIfBirthWriteIsInterrupted() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            val source = DatabaseHelper(context, "availability-copy-source.db") {}
            val target = DatabaseHelper(context, "availability-copy-target.db") {}
            val serial = context.appComponent.userCache.getSerialNumberForUser(Process.myUserHandle())
            fun insert(helper: DatabaseHelper, id: Int) {
                helper.insertAndCheck(helper.writableDatabase, ContentValues().apply {
                    put(Favorites._ID, id)
                    put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                    put(Favorites.RESTORED, 0)
                    put(Favorites.PROFILE_ID, serial)
                    put(Favorites.INTENT, Intent().setComponent(ComponentName("test.sd.app", "Saved$id")).toUri(0))
                })
            }
            try {
                insert(source, 7)
                insert(target, 42)
                val oldBirth = WorkspaceAvailabilityDb.generation(target.writableDatabase, Favorites.TABLE_NAME, 42)
                target.writableDatabase.execSQL("CREATE TRIGGER abort_birth_copy BEFORE INSERT ON workspace_availability_rows_favorites BEGIN SELECT RAISE(ABORT, 'simulated interruption'); END")
                var interrupted = false
                try {
                    LauncherDbUtils.copyTable(source.writableDatabase, Favorites.TABLE_NAME,
                        target.writableDatabase, Favorites.TABLE_NAME, context)
                } catch (expected: android.database.SQLException) {
                    interrupted = true
                }
                assertThat(interrupted).isTrue()
                target.writableDatabase.query(Favorites.TABLE_NAME, arrayOf(Favorites._ID), null, null, null, null, null).use {
                    assertThat(it.count).isEqualTo(1)
                    assertThat(it.moveToFirst()).isTrue()
                    assertThat(it.getInt(0)).isEqualTo(42)
                }
                assertThat(WorkspaceAvailabilityDb.generation(target.writableDatabase, Favorites.TABLE_NAME, 42)).isEqualTo(oldBirth)
                target.writableDatabase.execSQL("DROP TRIGGER abort_birth_copy")
                LauncherDbUtils.copyTable(source.writableDatabase, Favorites.TABLE_NAME,
                    target.writableDatabase, Favorites.TABLE_NAME, context)
                assertThat(WorkspaceAvailabilityDb.generation(target.writableDatabase, Favorites.TABLE_NAME, 7))
                    .isEqualTo(WorkspaceAvailabilityDb.generation(source.writableDatabase, Favorites.TABLE_NAME, 7))
                assertThat(target.writableDatabase.query(Favorites.TABLE_NAME, null, null, null, null, null, null).use { it.count }).isEqualTo(1)
            } finally {
                source.close()
                target.close()
            }
        }
    }

    @Test
    fun journaledRemovalSurvivesRestartAndDoesNotDeleteReinstallationOrAnotherProfile() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            val db = context.appComponent.testableModelState.dbController
            db.createEmptyDB()
            val user = Process.myUserHandle()
            val serial = context.appComponent.userCache.getSerialNumberForUser(user)
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName("test.sd.app", "SavedActivity")).toUri(0)
            fun insert(id: Int, profile: Long) {
                db.insert(ContentValues().apply {
                    put(Favorites._ID, id)
                    put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                    put(Favorites.RESTORED, 0)
                    put(Favorites.PROFILE_ID, profile)
                    put(Favorites.INTENT, intent)
                    put(Favorites.TITLE, "Saved label")
                    put(Favorites.CONTAINER, Favorites.CONTAINER_DESKTOP)
                    put(Favorites.SCREEN, 0)
                    put(Favorites.CELLX, id % 4)
                    put(Favorites.CELLY, 0)
                    put(Favorites.SPANX, 1)
                    put(Favorites.SPANY, 1)
                    put(Favorites.RANK, 0)
                })
            }
            insert(42, serial)
            insert(43, serial + 100)
            val first = WorkspaceAvailability(context, db)
            assertThat(first.removed(user, setOf("test.sd.app"))).hasSize(1)
            insert(44, serial)
            val restarted = WorkspaceAvailability(context, db)
            restarted.reconcileRemovals()
            restarted.reconcileRemovals()
            db.query(arrayOf(Favorites._ID, Favorites.TITLE), null, null, Favorites._ID).use { cursor ->
                val ids = mutableListOf<Int>()
                while (cursor.moveToNext()) {
                    ids.add(cursor.getInt(0))
                    assertThat(cursor.getString(1)).isEqualTo("Saved label")
                }
                assertThat(ids).containsExactly(43, 44)
            }
        }
    }

    @Test
    fun availabilityIdentityDoesNotMigrateOrEraseFavoritesAndChangesAfterDatabaseReset() {
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            val db = context.appComponent.testableModelState.dbController
            val before = db.query(arrayOf(Favorites._ID), null, null, null).use { it.count }
            val identity = db.getAvailabilityIdentity()
            assertThat(identity).isNotNull()
            assertThat(db.getAvailabilityIdentity()).isEqualTo(identity)
            assertThat(db.query(arrayOf(Favorites._ID), null, null, null).use { it.count }).isEqualTo(before)
            db.createEmptyDB()
            assertThat(db.getAvailabilityIdentity()).isNotEqualTo(identity)
        }
    }
}
