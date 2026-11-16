/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.util;
public class IntSet extends java.util.HashSet<Integer> {
    public static IntSet wrap(int... values) { IntSet result = new IntSet(); for (int value : values) result.add(value); return result; }
}
