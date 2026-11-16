/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package android.os;
public class UserManager {
    public long getSerialNumberForUser(UserHandle user) { return user.serial; }
}
