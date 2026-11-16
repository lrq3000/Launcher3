/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.util;
public class TraceHelper { public static final TraceHelper INSTANCE = new TraceHelper(); public AutoCloseable beginAsyncSection(String s) { return () -> {}; } }
