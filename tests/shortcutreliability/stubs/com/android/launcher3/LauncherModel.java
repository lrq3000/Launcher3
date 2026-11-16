/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3;
import android.os.UserHandle;
import java.util.List;
public class LauncherModel {
    public com.android.launcher3.model.WorkspaceAvailability availability;
    public com.android.launcher3.model.WorkspaceAvailability getWorkspaceAvailability() { return availability; }
    public interface ModelUpdateTask {
        void execute(com.android.launcher3.model.ModelTaskController controller,
                com.android.launcher3.model.BgDataModel data, com.android.launcher3.model.AllAppsList apps);
    }
    public int removed;
    public int unavailable;
    public int available;
    public LauncherModel newModelCallbacks() { return this; }
    public void onPackagesRemoved(UserHandle user, List<String> packages) { removed += packages.size(); }
    public void onPackagesUnavailable(String[] packages, UserHandle user, boolean replacing) { unavailable += packages.length; }
    public void onPackagesAvailable(String[] packages, UserHandle user, boolean replacing) { available += packages.length; }
}
