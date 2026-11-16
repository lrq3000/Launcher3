/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.util;
import android.content.*;
public class ContentWriter {
    private final ContentValues values = new ContentValues();
    public ContentWriter(Context context) {}
    public ContentWriter put(String key, int value) { values.put(key, value); return this; }
    public ContentValues getValues(Context context) { return values; }
}
