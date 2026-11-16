/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package android.content.pm;
public class ApplicationInfo {
    public static final int FLAG_EXTERNAL_STORAGE = 1;
    public int flags;
    public java.util.UUID storageUuid = android.os.storage.StorageManager.UUID_DEFAULT;
}
