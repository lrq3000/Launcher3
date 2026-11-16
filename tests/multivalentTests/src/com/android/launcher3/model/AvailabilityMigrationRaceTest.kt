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
import android.database.sqlite.SQLiteDatabase
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.LauncherPrefs
import com.android.launcher3.LauncherSettings.Favorites
import com.android.launcher3.provider.LauncherDbUtils
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeoutException
import javax.inject.Provider
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Real controller helper switch plus real SQLite copy; only the copy timing is deliberately held at the empty-destination boundary. */
@RunWith(AndroidJUnit4::class)
class AvailabilityMigrationRaceTest {
    @get:Rule val context = SandboxApplication().withModelDependency()

    @Test
    fun uninstallWaitsForHelperSwitchAndCopyThenRemovesMigratedOldRow() {
        val component = context.appComponent
        val idp = component.idp
        val migration = mock<GridSizeMigrationLogic>()
        val delegate = mock<ModelDelegate>()
        val controller = ModelDbController(context, idp, LauncherPrefs.get(context),
            component.userCache, component.layoutParserFactory, Provider { migration })
        val availability = WorkspaceAvailability(context, controller)
        val enteredEmptyDestination = CountDownLatch(1)
        val allowCopy = CountDownLatch(1)
        val callbackStarted = CountDownLatch(1)
        val user = Process.myUserHandle()
        val serial = component.userCache.getSerialNumberForUser(user)
        doAnswer { call ->
            val target = call.getArgument<DatabaseHelper>(2)
            val source = call.getArgument<SQLiteDatabase>(3)
            enteredEmptyDestination.countDown()
            check(allowCopy.await(5, SECONDS))
            LauncherDbUtils.copyTable(source, Favorites.TABLE_NAME, target.writableDatabase,
                Favorites.TABLE_NAME, context)
            null
        }.whenever(migration).migrateGrid(any(), any(), any(), any(), any())
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            idp.numColumns = 4
            idp.numRows = 5
            idp.dbFile = "availability-migration-source.db"
            DeviceGridState(idp).writeToPrefs(context)
            controller.createEmptyDB()
            controller.insert(ContentValues().apply {
                put(Favorites._ID, 42)
                put(Favorites.ITEM_TYPE, Favorites.ITEM_TYPE_APPLICATION)
                put(Favorites.RESTORED, 0)
                put(Favorites.PROFILE_ID, serial)
                put(Favorites.INTENT, Intent(Intent.ACTION_MAIN).setComponent(ComponentName("test.sd.app", "Saved")).toUri(0))
            })
            controller.clearEmptyDbFlag()
            idp.numRows = 6
            idp.dbFile = "availability-migration-target.db"
        }
        val migrated = MODEL_EXECUTOR.submit { controller.attemptMigrateDb(null, delegate) }
        assertThat(enteredEmptyDestination.await(5, SECONDS)).isTrue()
        val callback = CompletableFuture.supplyAsync {
            callbackStarted.countDown()
            availability.removed(user, setOf("test.sd.app"))
        }
        assertThat(callbackStarted.await(5, SECONDS)).isTrue()
        var blocked = false
        try {
            callback.get(200, MILLISECONDS)
        } catch (expected: TimeoutException) {
            blocked = true
        } finally {
            allowCopy.countDown()
        }
        migrated.get(5, SECONDS)
        assertThat(callback.get(5, SECONDS)).hasSize(1)
        assertThat(blocked).isTrue()
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            assertThat(availability.reconcileRemovals()).isTrue()
            assertThat(controller.query(arrayOf(Favorites._ID), null, null, null).use { it.count }).isEqualTo(0)
        }
    }
}
