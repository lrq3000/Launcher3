/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.util;
import android.content.Context;
public class Singleton<T> {
    private final T value;
    public Singleton(T value) { this.value = value; }
    public T get(Context context) { return value; }
}
