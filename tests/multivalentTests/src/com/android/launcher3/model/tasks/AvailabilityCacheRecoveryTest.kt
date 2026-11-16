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
package com.android.launcher3.model.tasks

import android.content.ComponentName
import android.content.pm.LauncherApps
import android.os.Process.myUserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.launcher3.model.BgDataModel.ModificationSource.ModelTask
import com.android.launcher3.model.IModelWriter
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.data.AppInfo.makeLaunchIntent
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MODEL_EXECUTOR
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY
import com.android.launcher3.util.LauncherModelHelper.TEST_ACTIVITY2
import com.android.launcher3.util.LauncherModelHelper.TEST_PACKAGE
import com.android.launcher3.util.SandboxApplication
import com.android.launcher3.util.TestUtil
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Uses the actual cache and its database; the standalone boundary cache is deliberately separate. */
@RunWith(AndroidJUnit4::class)
class AvailabilityCacheRecoveryTest {
    @get:Rule val context = SandboxApplication().withModelDependency()

    @Test
    fun missingSavedActivityDoesNotLoseItsOnlyDurableCacheEntryDuringPackageUpdate() {
        val service = context.spyService(LauncherApps::class.java)
        val user = myUserHandle()
        val saved = ComponentName(TEST_PACKAGE, TEST_ACTIVITY)
        val other = service.resolveActivity(makeLaunchIntent(ComponentName(TEST_PACKAGE, TEST_ACTIVITY2)), user)!!
        val state = context.appComponent.testableModelState
        val cache = state.iconCache
        val controller = mock<ModelTaskController>()
        whenever(controller.context).thenReturn(context)
        whenever(controller.iconCache).thenReturn(cache)
        whenever(controller.model).thenReturn(state.model)
        whenever(controller.getModelWriter()).thenReturn(mock<IModelWriter>())
        TestUtil.runOnExecutorSync(MODEL_EXECUTOR) {
            cache.updateIconsForPkg(TEST_PACKAGE, user)
            assertThat(cache.isItemInDb(ComponentKey(saved, user))).isTrue()
            val item = WorkspaceItemInfo().apply {
                id = 42
                this.user = user
                intent = makeLaunchIntent(saved)
                runtimeStatusFlags = WorkspaceItemInfo.FLAG_DISABLED_NOT_AVAILABLE
            }
            state.dataModel.addItems(context, listOf(item), ModelTask)
            doReturn(listOf(other)).whenever(service).getActivityList(TEST_PACKAGE, user)
            doReturn(true).whenever(service).isPackageEnabled(TEST_PACKAGE, user)
            PackageUpdatedTask(PackageUpdatedTask.OP_UPDATE, user, TEST_PACKAGE)
                .execute(controller, state.dataModel, state.appsList)
            assertThat(cache.isItemInDb(ComponentKey(saved, user))).isTrue()
            assertThat(item.targetComponent).isEqualTo(saved)
        }
    }
}
