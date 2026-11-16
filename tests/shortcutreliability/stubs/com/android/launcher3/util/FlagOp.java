/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.util;
public class FlagOp {
    public static final FlagOp NO_OP = new FlagOp(0, 0);
    private final int add, remove;
    private FlagOp(int add, int remove) { this.add = add; this.remove = remove; }
    public FlagOp addFlag(int flag) { return new FlagOp(add | flag, remove); }
    public FlagOp removeFlag(int flag) { return new FlagOp(add, remove | flag); }
    public int apply(int flags) { return (flags | add) & ~remove; }
}
