/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package android.os;
public class UserHandle {
    public final long serial;
    public UserHandle() { this(0); }
    public UserHandle(long value) { serial = value; }
}
