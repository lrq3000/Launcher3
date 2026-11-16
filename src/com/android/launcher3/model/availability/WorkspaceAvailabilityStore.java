/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.model.availability;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.CRC32;

/** Durable evidence, not a second favorites database. Never recreates items or infers an uninstall. */
public final class WorkspaceAvailabilityStore {
    private static final int MAGIC = 0x4c334156;
    private static final int VERSION = 1;
    private static final int MAX_RECORDS = 100000;
    private static final int MAX_BYTES = 16 * 1024 * 1024;

    public enum Storage { UNKNOWN, INTERNAL, EXTERNAL }

    /** Database identity and exact saved component prevent stale evidence matching a different row. */
    public static final class Row {
        public final String database;
        public final long serial;
        public final int id;
        public final String component;

        public Row(String database, long serial, int id, String component) {
            this.database = Objects.requireNonNull(database);
            this.serial = serial;
            this.id = id;
            this.component = Objects.requireNonNull(component);
            if (component.indexOf('/') <= 0) throw new IllegalArgumentException("No component");
        }

        String packageKey() { return serial + ":" + component.substring(0, component.indexOf('/')); }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof Row)) return false;
            Row other = (Row) object;
            return serial == other.serial && id == other.id && database.equals(other.database)
                    && component.equals(other.component);
        }

        @Override
        public int hashCode() { return Objects.hash(database, serial, id, component); }
    }

    private static final class PackageState {
        Storage storage = Storage.UNKNOWN;
        String uuid = "";
        boolean unavailable;
    }

    private static final class ItemState {
        boolean unavailable;
        boolean removed;
    }

    private final File mFile;
    private final Map<String, PackageState> mPackages = new HashMap<>();
    private final Map<Row, ItemState> mItems = new HashMap<>();
    private IOException mLastFailure;
    private boolean mReadable = true;

    public WorkspaceAvailabilityStore(File file) {
        mFile = file;
        if (!file.exists()) return;
        try {
            if (Files.size(file.toPath()) > MAX_BYTES) throw new IOException("Oversized journal");
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length < 8) throw new IOException("Truncated journal");
            CRC32 crc = new CRC32();
            crc.update(bytes, 8, bytes.length - 8);
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (in.readLong() != crc.getValue() || in.readInt() != MAGIC
                        || in.readInt() != VERSION) throw new IOException("Invalid journal");
                int packages = readCount(in);
                for (int i = 0; i < packages; i++) {
                    String key = in.readUTF();
                    PackageState state = new PackageState();
                    int ordinal = in.readInt();
                    if (ordinal < 0 || ordinal >= Storage.values().length) {
                        throw new IOException("Invalid storage classification");
                    }
                    state.storage = Storage.values()[ordinal];
                    state.uuid = in.readUTF();
                    state.unavailable = in.readBoolean();
                    mPackages.put(key, state);
                }
                int items = readCount(in);
                for (int i = 0; i < items; i++) {
                    Row row = new Row(in.readUTF(), in.readLong(), in.readInt(), in.readUTF());
                    ItemState state = new ItemState();
                    state.unavailable = in.readBoolean();
                    state.removed = in.readBoolean();
                    mItems.put(row, state);
                }
                if (in.available() != 0) throw new IOException("Trailing journal data");
            }
        } catch (IOException | IllegalArgumentException e) {
            mPackages.clear();
            mItems.clear();
            mReadable = false;
            mLastFailure = new IOException("Cannot read workspace availability", e);
        }
    }

    private static int readCount(DataInputStream in) throws IOException {
        int count = in.readInt();
        if (count < 0 || count > MAX_RECORDS) throw new IOException("Invalid record count");
        return count;
    }

    /** Only known internal applications outside a recovery condition retain negative-lookup policy. */
    public synchronized boolean shouldPreserve(Row row) {
        if (isRemoved(row)) return false;
        PackageState pkg = mPackages.get(row.packageKey());
        return !mReadable || pkg == null || pkg.storage != Storage.INTERNAL
                || pkg.unavailable || isRecovering(row);
    }

    public synchronized boolean isRecovering(Row row) {
        ItemState item = mItems.get(row);
        return item != null && item.unavailable && !item.removed;
    }

    public synchronized boolean isRemoved(Row row) {
        ItemState item = mItems.get(row);
        return item != null && item.removed;
    }

    public synchronized void protect(Row row) {
        ItemState item = mItems.computeIfAbsent(row, key -> new ItemState());
        if (item.unavailable || item.removed) return;
        item.unavailable = true;
        persist();
    }

    /** Positive resolution of ONE exact component cannot clear another component's recovery state. */
    public synchronized void observe(Row row, Storage storage, String uuid) {
        boolean newItem = !mItems.containsKey(row);
        ItemState item = mItems.computeIfAbsent(row, key -> new ItemState());
        if (item.removed) return;
        PackageState pkg = mPackages.computeIfAbsent(row.packageKey(), key -> new PackageState());
        String normalizedUuid = uuid == null ? "" : uuid;
        boolean changed = newItem || item.unavailable || pkg.unavailable || pkg.storage != storage
                || !pkg.uuid.equals(normalizedUuid);
        item.unavailable = false;
        pkg.unavailable = false;
        pkg.storage = storage;
        pkg.uuid = normalizedUuid;
        if (changed) persist();
    }

    public synchronized void unavailable(long serial, String packageName) {
        String key = serial + ":" + packageName;
        PackageState pkg = mPackages.computeIfAbsent(key, ignored -> new PackageState());
        pkg.unavailable = true;
        mItems.forEach((row, item) -> {
            if (row.packageKey().equals(key) && !item.removed) item.unavailable = true;
        });
        persist();
    }

    /** Snapshot actual favorites before mutation. Tombstones never apply to future row IDs. */
    public synchronized void removed(Collection<Row> rows) {
        for (Row row : rows) mItems.computeIfAbsent(row, key -> new ItemState()).removed = true;
        persist();
    }

    public synchronized IOException getLastFailure() { return mLastFailure; }

    private void persist() {
        try {
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(payload)) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeInt(mPackages.size());
                for (Map.Entry<String, PackageState> entry : mPackages.entrySet()) {
                    PackageState pkg = entry.getValue();
                    out.writeUTF(entry.getKey());
                    out.writeInt(pkg.storage.ordinal());
                    out.writeUTF(pkg.uuid);
                    out.writeBoolean(pkg.unavailable);
                }
                out.writeInt(mItems.size());
                for (Map.Entry<Row, ItemState> entry : mItems.entrySet()) {
                    Row row = entry.getKey();
                    out.writeUTF(row.database);
                    out.writeLong(row.serial);
                    out.writeInt(row.id);
                    out.writeUTF(row.component);
                    out.writeBoolean(entry.getValue().unavailable);
                    out.writeBoolean(entry.getValue().removed);
                }
            }
            byte[] bytes = payload.toByteArray();
            CRC32 crc = new CRC32();
            crc.update(bytes);
            File next = new File(mFile.getPath() + ".new");
            try (FileOutputStream file = new FileOutputStream(next);
                    DataOutputStream out = new DataOutputStream(file)) {
                out.writeLong(crc.getValue());
                out.write(bytes);
                out.flush();
                file.getFD().sync();
            }
            // Do not fall back to a non-atomic replacement. An interrupted write must leave the prior committed journal intact.
            Files.move(next.toPath(), mFile.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            mLastFailure = e;
            mReadable = false;
        }
    }
}
