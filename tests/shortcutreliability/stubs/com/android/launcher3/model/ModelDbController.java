/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model;
import android.database.Cursor;
import java.util.*;
public class ModelDbController {
    public final List<Object[]> rows = new ArrayList<>();
    public String identity = "database-one";
    public boolean queriesAllowed = true;
    public boolean failSnapshots;
    public Runnable beforeSnapshot = () -> {};
    public boolean identityAvailable = true;
    public final Map<Integer, String> births = new HashMap<>();
    public com.android.launcher3.model.availability.WorkspaceAvailabilityStore.Row getAvailabilityRow(
            int id, long serial, String component) {
        if (!identityAvailable) return null;
        return new com.android.launcher3.model.availability.WorkspaceAvailabilityStore.Row(
                getAvailabilityIdentity(), serial, id, component, births.getOrDefault(id, ""));
    }
    public void insertNew(com.android.launcher3.model.availability.WorkspaceAvailabilityStore store,
            int id, long serial, String component) {
        births.put(id, store.nextInsertionGeneration());
        rows.add(new Object[] {id, serial, component});
    }
    public String getAvailabilityIdentity() {
        if (!queriesAllowed) throw new AssertionError("Database query after worker phase");
        return identity;
    }
    public Cursor query(String[] columns, String selection, String[] args, String sortOrder) {
        beforeSnapshot.run();
        if (failSnapshots) throw new IllegalStateException("Transient favorites query failure");
        if (!selection.equals("itemType=? AND restored=0") || !Arrays.equals(args, new String[] {"0"})) {
            throw new AssertionError("Incorrect favorites snapshot selection");
        }
        return new Cursor(new ArrayList<>(rows));
    }
    public int delete(String where, String[] args) {
        if (!where.equals("_id=? AND profileId=? AND intent=?")) throw new AssertionError("Unsafe deletion");
        int size = rows.size();
        rows.removeIf(row -> row[0].toString().equals(args[0]) && row[1].toString().equals(args[1]) && row[2].equals(args[2]));
        return size - rows.size();
    }
}
