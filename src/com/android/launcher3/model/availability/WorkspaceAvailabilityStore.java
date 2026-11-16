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
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.CRC32;

/** Durable evidence, not a second favorites database. Never recreates items or infers an uninstall. */
public final class WorkspaceAvailabilityStore {
    private static final Map<String, WorkspaceAvailabilityStore> OWNERS = new HashMap<>();

    /** All models, including previews, share one mutable journal/batch owner for a canonical path. */
    public static synchronized WorkspaceAvailabilityStore open(File file) {
        String path;
        try {
            path = file.getCanonicalPath();
        } catch (IOException e) {
            path = file.getAbsoluteFile().toPath().normalize().toString();
        }
        return OWNERS.computeIfAbsent(path, key -> new WorkspaceAvailabilityStore(new File(key)));
    }
    private static final int MAGIC = 0x4c334156;
    private static final int VERSION = 2;
    private static final int MAX_RECORDS = 100000;
    private static final int MAX_BYTES = 16 * 1024 * 1024;

    public enum Storage { UNKNOWN, INTERNAL, EXTERNAL }

    /** Database identity, exact saved component, and durable insertion generation distinguish even a reused item ID. */
    public static final class Row {
        public final String database;
        public final long serial;
        public final int id;
        public final String component;
        public final String generation;

        public Row(String database, long serial, int id, String component) {
            this(database, serial, id, component, "");
        }

        public Row(String database, long serial, int id, String component, String generation) {
            this.database = Objects.requireNonNull(database);
            this.serial = serial;
            this.id = id;
            this.component = Objects.requireNonNull(component);
            this.generation = Objects.requireNonNull(generation);
            if (component.indexOf('/') <= 0) throw new IllegalArgumentException("No component");
        }

        String packageKey() { return serial + ":" + component.substring(0, component.indexOf('/')); }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof Row)) return false;
            Row other = (Row) object;
            return serial == other.serial && id == other.id && database.equals(other.database)
                    && component.equals(other.component) && generation.equals(other.generation);
        }

        @Override
        public int hashCode() { return Objects.hash(database, serial, id, component, generation); }
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
    private final ReentrantLock mOperationLock = new ReentrantLock(true);
    private final Map<String, PackageState> mPackages = new HashMap<>();
    private final Map<Row, ItemState> mItems = new HashMap<>();
    private IOException mLastFailure;
    private boolean mReadable = true;
    private int mBatchDepth;
    private boolean mDirty;
    private String mClock = UUID.randomUUID().toString();
    private long mSequence;
    private long mRevision;
    private final Map<String, RemovalFence> mRemovalFences = new HashMap<>();

    private static final class RemovalFence {
        final long serial;
        final String packageName;
        final long cutoff;
        RemovalFence(long serial, String packageName, long cutoff) {
            this.serial = serial;
            this.packageName = packageName;
            this.cutoff = cutoff;
        }
    }

    /** Acquired before SQLite transactions, migration switches, and callback snapshots. Helper-thread callbacks release this gate before acquiring LauncherModel locks or dispatching reload/model work. */
    public Operation beginOperation() { return new Operation(mOperationLock); }

    public static final class Operation implements AutoCloseable {
        private ReentrantLock mLock;
        private Operation(ReentrantLock lock) { mLock = lock; lock.lock(); }
        @Override
        public void close() {
            if (mLock != null) { ReentrantLock lock = mLock; mLock = null; lock.unlock(); }
        }
    }

    /** Reserve birth identity durably BEFORE inserting a favorite. Gaps after failed/rolled-back SQL writes are harmless; reuse is not. */
    public synchronized String nextInsertionGeneration() {
        if (mSequence == Long.MAX_VALUE) throw new IllegalStateException("Availability clock exhausted");
        String generation = mClock + ":" + ++mSequence;
        if (!persist()) throw new IllegalStateException("Cannot persist favorite insertion identity", mLastFailure);
        return generation;
    }

    /** A bounded, ordered uninstall fence is durable before any identity/favorites query can fail. Future insertions necessarily have a later birth identity. */
    public synchronized void beginRemoval(long serial, Collection<String> packages) {
        if (mSequence == Long.MAX_VALUE) throw new IllegalStateException("Availability clock exhausted");
        long cutoff = ++mSequence;
        for (String pkg : packages) mRemovalFences.put(serial + ":" + pkg, new RemovalFence(serial, pkg, cutoff));
        mRevision++;
        persist();
    }

    public synchronized long getRevision() { return mRevision; }

    private WorkspaceAvailabilityStore(File file) {
        mFile = file;
        if (!file.exists()) return;
        try {
            if (Files.size(file.toPath()) > MAX_BYTES) throw new IOException("Oversized journal");
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (bytes.length < 8) throw new IOException("Truncated journal");
            CRC32 crc = new CRC32();
            crc.update(bytes, 8, bytes.length - 8);
            try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
                if (in.readLong() != crc.getValue() || in.readInt() != MAGIC) throw new IOException("Invalid journal");
                int version = in.readInt();
                if (version != 1 && version != VERSION) throw new IOException("Unsupported journal version");
                if (version >= 2) {
                    mClock = in.readUTF();
                    mSequence = in.readLong();
                    mRevision = in.readLong();
                    if (mSequence < 0) throw new IOException("Invalid availability clock");
                }
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
                    Row row = new Row(in.readUTF(), in.readLong(), in.readInt(), in.readUTF(),
                            version >= 2 ? in.readUTF() : "");
                    ItemState state = new ItemState();
                    state.unavailable = in.readBoolean();
                    state.removed = in.readBoolean();
                    mItems.put(row, state);
                }
                if (version >= 2) {
                    int fences = readCount(in);
                    for (int i = 0; i < fences; i++) {
                        long serial = in.readLong();
                        String pkg = in.readUTF();
                        long cutoff = in.readLong();
                        if (cutoff < 0 || cutoff > mSequence) throw new IOException("Invalid removal cutoff");
                        mRemovalFences.put(serial + ":" + pkg, new RemovalFence(serial, pkg, cutoff));
                    }
                }
                if (in.available() != 0) throw new IOException("Trailing journal data");
            }
        } catch (IOException | IllegalArgumentException e) {
            mPackages.clear();
            mItems.clear();
            mRemovalFences.clear();
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
        return !mReadable || !mItems.containsKey(row) || pkg == null || pkg.storage != Storage.INTERNAL
                || pkg.unavailable || isRecovering(row);
    }

    public synchronized boolean isRecovering(Row row) {
        ItemState item = mItems.get(row);
        return item != null && item.unavailable && !item.removed;
    }

    public synchronized boolean isRemoved(Row row) {
        ItemState item = mItems.get(row);
        if (item != null && item.removed) return true;
        RemovalFence fence = mRemovalFences.get(row.packageKey());
        return fence != null && bornBefore(row.generation, fence.cutoff);
    }

    private boolean bornBefore(String generation, long cutoff) {
        // Legacy rows and rows from a former (lost/rebuilt) journal predate this clock. Every new insertion is stamped with the current durable clock before SQL mutation.
        String prefix = mClock + ":";
        if (!generation.startsWith(prefix)) return true;
        try { return Long.parseLong(generation.substring(prefix.length())) <= cutoff; }
        catch (NumberFormatException invalid) { return false; }
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
        if (isRemoved(row)) return;
        PackageState pkg = mPackages.computeIfAbsent(row.packageKey(), key -> new PackageState());
        String normalizedUuid = uuid == null ? "" : uuid;
        boolean changed = newItem || item.unavailable || pkg.unavailable || pkg.storage != storage
                || !pkg.uuid.equals(normalizedUuid);
        item.unavailable = false;
        pkg.unavailable = false;
        pkg.storage = storage;
        pkg.uuid = normalizedUuid;
        if (changed) observationChanged();
    }

    public synchronized void unavailable(long serial, String packageName) {
        mRevision++;
        String key = serial + ":" + packageName;
        PackageState pkg = mPackages.computeIfAbsent(key, ignored -> new PackageState());
        pkg.unavailable = true;
        mItems.forEach((row, item) -> {
            if (row.packageKey().equals(key) && !item.removed) item.unavailable = true;
        });
        persist();
    }

    /** Storage evidence may change before launcher activities are registered. It must not clear recovery. */
    public synchronized void classifyStorage(long serial, String packageName, Storage storage, String uuid) {
        PackageState pkg = mPackages.computeIfAbsent(serial + ":" + packageName,
                ignored -> new PackageState());
        String normalizedUuid = uuid == null ? "" : uuid;
        if (pkg.storage == storage && pkg.uuid.equals(normalizedUuid)) return;
        pkg.storage = storage;
        pkg.uuid = normalizedUuid;
        observationChanged();
    }

    /** Snapshot actual favorites before mutation. Exact tombstones never apply to a future insertion, even when its item ID and component repeat. */
    public synchronized void removed(Collection<Row> rows) {
        for (Row row : rows) mItems.computeIfAbsent(row, key -> new ItemState()).removed = true;
        persist();
    }

    public synchronized IOException getLastFailure() { return mLastFailure; }

    /** Batch positive loader observations to avoid serializing/fsyncing the entire journal once per item. Safety-critical recovery and removal evidence always persists immediately. */
    public synchronized void beginBatch() { mBatchDepth++; }

    public synchronized void endBatch() {
        if (mBatchDepth <= 0) throw new IllegalStateException("No observation batch");
        if (--mBatchDepth == 0 && mDirty) persist();
    }

    private void observationChanged() {
        mDirty = true;
        if (mBatchDepth == 0) persist();
    }

    private boolean persist() {
        try {
            ByteArrayOutputStream payload = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(payload)) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                out.writeUTF(mClock);
                out.writeLong(mSequence);
                out.writeLong(mRevision);
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
                    out.writeUTF(row.generation);
                    out.writeBoolean(entry.getValue().unavailable);
                    out.writeBoolean(entry.getValue().removed);
                }
                out.writeInt(mRemovalFences.size());
                for (RemovalFence fence : mRemovalFences.values()) {
                    out.writeLong(fence.serial);
                    out.writeUTF(fence.packageName);
                    out.writeLong(fence.cutoff);
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
            mDirty = false;
            return true;
        } catch (IOException e) {
            mLastFailure = e;
            mReadable = false;
            return false;
        }
    }
}
