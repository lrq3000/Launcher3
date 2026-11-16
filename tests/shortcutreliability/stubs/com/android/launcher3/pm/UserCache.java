/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.pm;
import com.android.launcher3.util.Singleton;
public class UserCache {
    public static final Singleton<UserCache> INSTANCE = new Singleton<>(new UserCache());
    public Object getUserInfo(android.os.UserHandle user) { return user; }
}
