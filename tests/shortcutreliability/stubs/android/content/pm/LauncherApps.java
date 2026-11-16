/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package android.content.pm;
import android.os.UserHandle;
public class LauncherApps {
    public boolean enabled;
    public java.util.List<LauncherActivityInfo> activities = java.util.List.of();
    public java.util.List<LauncherActivityInfo> getActivityList(String name, UserHandle user) { return activities; }
    public boolean isPackageEnabled(String name, UserHandle user) { return enabled; }
    public abstract static class Callback {
        public void onPackageAdded(String name, UserHandle user) {}
        public void onPackageChanged(String name, UserHandle user) {}
        public void onPackageLoadingProgressChanged(String name, UserHandle user, float progress) {}
        public void onPackageRemoved(String name, UserHandle user) {}
        public void onPackagesAvailable(String[] names, UserHandle user, boolean replacing) {}
        public void onPackagesUnavailable(String[] names, UserHandle user, boolean replacing) {}
        public void onPackagesSuspended(String[] names, UserHandle user) {}
        public void onPackagesUnsuspended(String[] names, UserHandle user) {}
        public void onShortcutsChanged(String name, java.util.List<ShortcutInfo> shortcuts, UserHandle user) {}
    }
}
