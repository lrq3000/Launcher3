/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package android.content.pm;
import android.content.ComponentName;
public class LauncherActivityInfo {
    private final ComponentName component;
    public final ApplicationInfo applicationInfo = new ApplicationInfo();
    public LauncherActivityInfo(ComponentName component) { this.component = component; }
    public ComponentName getComponentName() { return component; }
    public ApplicationInfo getApplicationInfo() { return applicationInfo; }
}
