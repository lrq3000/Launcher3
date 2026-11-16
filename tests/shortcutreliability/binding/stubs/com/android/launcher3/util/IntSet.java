/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.util;
public class IntSet extends java.util.HashSet<Integer> { public IntArray getArray() { return new IntArray(); } public static IntSet wrap(int id) { IntSet s = new IntSet(); s.add(id); return s; } }
