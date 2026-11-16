/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3;
public class LauncherSettings {
    public static class Favorites {
        public static final int ITEM_TYPE_APPLICATION = 0;
        public static final int ITEM_TYPE_DEEP_SHORTCUT = 6;
        public static final String TABLE_NAME = "favorites";
        public static final String _ID = "_id", PROFILE_ID = "profileId", INTENT = "intent",
                ITEM_TYPE = "itemType", RESTORED = "restored";
    }
}
