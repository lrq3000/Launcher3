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
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real SQLite/favorites integration; unlike the standalone boundary tests this requires the AOSP test target. */
@RunWith(AndroidJUnit4::class)
class WorkspaceAvailabilityTest {
    @get:Rule val context = SandboxApplication().withModelDependency()

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
