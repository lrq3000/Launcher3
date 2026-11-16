/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.automation;
import com.android.launcher3.util.Singleton;
public class AutomationRepository {
    public static final Singleton<AutomationRepository> INSTANCE = new Singleton<>(new AutomationRepository());
}
