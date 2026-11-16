/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.util;
import android.content.*;
import android.os.UserHandle;
import com.android.launcher3.model.data.ItemInfo;
import java.util.*;
import java.nio.file.*;
/** Simple real file boundary, not the production Android XML/AtomicFile serializer. Queue-retirement decisions remain production code. */
public class PersistedItemArray<T extends ItemInfo> {
    private final String name;
    public PersistedItemArray(String name) { this.name = name; }
    public java.io.File getFile(Context c) { return new java.io.File(c.noBackupFilesDir, name); }
    public void write(Context c, List<T> items) {
        try { Files.write(getFile(c).toPath(), items.stream().map(i -> i.itemType + "|" + i.user.serial + "|" + i.getIntent().toUri(0)).toList()); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    public List<T> read(Context c, Factory<T> factory) {
        List<T> result = new ArrayList<>();
        if (!getFile(c).exists()) return result;
        try {
            for (String line : Files.readAllLines(getFile(c).toPath())) {
                String[] p = line.split("\\|", 3); result.add(factory.createInfo(Integer.parseInt(p[0]), new UserHandle(Long.parseLong(p[1])), Intent.parseUri(p[2], 0)));
            }
        } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
        return result;
    }
    public interface Factory<T> { T createInfo(int type, UserHandle user, Intent intent); }
}
