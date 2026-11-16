/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package android.content;
import android.content.pm.LauncherApps;
public class Context {
    public final LauncherApps apps = new LauncherApps();
    public boolean unregistered;
    public boolean servicesAllowed = true;
    public java.io.File noBackupFilesDir;
    public java.io.File getNoBackupFilesDir() { return noBackupFilesDir; }
    public <T> T getSystemService(Class<T> type) {
        if (!servicesAllowed) throw new AssertionError("Service query after worker phase");
        return type.cast(type == LauncherApps.class ? apps : new android.os.UserManager());
    }
    public void unregisterReceiver(BroadcastReceiver receiver) { unregistered = true; }
}
