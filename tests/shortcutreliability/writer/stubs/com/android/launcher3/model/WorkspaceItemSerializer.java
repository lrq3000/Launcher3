/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model;
import com.android.launcher3.model.data.WorkspaceItemInfo;
public class WorkspaceItemSerializer {
    public WorkspaceItemInfo decode(SerializedItemItem item) {
        WorkspaceItemInfo result = new WorkspaceItemInfo(); result.itemType = item.itemType; result.user = item.user; result.intent = item.getIntent(); return result;
    }
}
