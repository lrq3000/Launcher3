/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.pm;
import android.os.UserHandle;
import android.content.pm.PackageInstaller.SessionInfo;
import com.android.launcher3.util.PackageUserKey;
public class InstallSessionTracker {
    public interface Callback {
        void onSessionFailure(String name, UserHandle user);
        void onPackageStateChanged(PackageInstallInfo info);
        void onUpdateSessionDisplay(PackageUserKey key, SessionInfo info);
        void onInstallSessionCreated(PackageInstallInfo info);
    }
}
