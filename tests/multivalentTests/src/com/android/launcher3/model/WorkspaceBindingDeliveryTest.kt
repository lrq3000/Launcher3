/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model

import android.content.ComponentName
import android.content.Intent
import android.os.Process.myUserHandle
import android.platform.test.annotations.DisableFlags
import android.platform.test.flag.junit.SetFlagsRule
import android.util.SparseArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.Flags
import com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceData
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.ModelTestExtensions.setEmptyModelLayout
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil.runOnExecutorSync
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/** Actual BaseLauncherBinder, default callback UI delivery, and LauncherModel receipt wiring. Root ModelCallbacks inflation methods have a separate executable boundary harness. */
@RunWith(AndroidJUnit4::class)
class WorkspaceBindingDeliveryTest {
    @get:Rule val context = SandboxApplication().withModelDependency()
    @get:Rule val flags = SetFlagsRule()

    @Test
    @DisableFlags(Flags.FLAG_MODEL_REPOSITORY)
    fun removalAfterSchedulingBeforeUiDeliveryRejectsOldSnapshotAndRebindsFreshData() {
        context.setEmptyModelLayout()
        val state = context.appComponent.testableModelState
        val model = spy(state.model)
        doReturn(CompletableFuture.completedFuture(Unit)).whenever(model).forceReload(any())
        val bound = CopyOnWriteArrayList<Int>()
        val callbacks = object : BgDataModel.Callbacks {
            override fun bindCompleteModel(itemIdMap: WorkspaceData, isBindingSync: Boolean) {
                bound.addAll(itemIdMap.map { it.id })
            }
        }
        fun items(id: Int) = SparseArray<ItemInfo>().apply {
            put(id, WorkspaceItemInfo().apply {
                this.id = id
                container = CONTAINER_DESKTOP
                user = myUserHandle()
                intent = Intent().setComponent(ComponentName(if (id == 42) "test.sd.app" else "healthy.app", "Saved"))
            })
        }
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        MAIN_EXECUTOR.execute { entered.countDown(); check(release.await(5, SECONDS)) }
        try {
            assertThat(entered.await(5, SECONDS)).isTrue()
            runOnExecutorSync(MODEL_EXECUTOR) {
                state.dataModel.dataLoadComplete(items(42), "old snapshot")
                BaseLauncherBinder(context, model, state.dataModel, state.appsList, arrayOf(callbacks))
                    .bindWorkspace(true, false)
            }
            model.newModelCallbacks().onPackageRemoved("test.sd.app", myUserHandle())
            verify(model).forceReload("confirmed-package-removal")
        } finally {
            release.countDown()
        }
        runOnExecutorSync(MAIN_EXECUTOR) {}
        assertThat(bound).isEmpty()
        runOnExecutorSync(MODEL_EXECUTOR) {
            state.dataModel.dataLoadComplete(items(44), "fresh snapshot")
            BaseLauncherBinder(context, model, state.dataModel, state.appsList, arrayOf(callbacks))
                .bindWorkspace(true, false)
        }
        runOnExecutorSync(MAIN_EXECUTOR) {}
        assertThat(bound).containsExactly(44)
    }
}
