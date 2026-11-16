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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.android.launcher3.LauncherSettings.Favorites;

import java.io.File;
import java.util.UUID;

/** Favorites remains unchanged: a companion row records insertion birth in the SAME SQLite transaction as each insert/replace. */
public final class WorkspaceAvailabilityDb {
    private WorkspaceAvailabilityDb() { }

    public static WorkspaceAvailabilityStore store(Context context) {
        return WorkspaceAvailabilityStore.open(new File(context.getNoBackupFilesDir(), "workspace-availability-v1"));
    }

    private static String birthsTable(String table) {
        if (!table.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Invalid favorites table");
        return "workspace_availability_rows_" + table;
    }

    public static void ensureBirths(SQLiteDatabase db, String table) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + birthsTable(table)
                + " (item_id INTEGER PRIMARY KEY, generation TEXT NOT NULL)");
    }

    public static String identity(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS workspace_availability_identity (id INTEGER PRIMARY KEY CHECK (id=1), token TEXT NOT NULL)");
        db.execSQL("INSERT OR IGNORE INTO workspace_availability_identity VALUES (1, ?)",
                new Object[] {UUID.randomUUID().toString()});
        ensureBirths(db, Favorites.TABLE_NAME);
        try (Cursor cursor = db.rawQuery("SELECT token FROM workspace_availability_identity WHERE id=1", null)) {
            if (!cursor.moveToFirst()) throw new IllegalStateException("Missing workspace identity");
            return cursor.getString(0);
        }
    }

    public static String generation(SQLiteDatabase db, String table, int id) {
        ensureBirths(db, table);
        try (Cursor cursor = db.rawQuery("SELECT generation FROM " + birthsTable(table)
                + " WHERE item_id=?", new String[] {Integer.toString(id)})) {
            return cursor.moveToFirst() ? cursor.getString(0) : "";
        }
    }

    public static WorkspaceAvailabilityStore.Row row(SQLiteDatabase db, int id, long serial, String component) {
        return new WorkspaceAvailabilityStore.Row(identity(db), serial, id, component,
                generation(db, Favorites.TABLE_NAME, id));
    }

    /** Null birth means a genuinely NEW insertion; a supplied birth is an explicit historical migration/copy, never a reinstall. */
    public static long insert(Context context, SQLiteDatabase db, String table, ContentValues values,
            int conflict, String historicalBirth) {
        WorkspaceAvailabilityStore store = store(context);
        try (WorkspaceAvailabilityStore.Operation ignored = store.beginOperation()) {
            db.beginTransaction();
            try {
                String birth = historicalBirth == null ? store.nextInsertionGeneration() : historicalBirth;
                long result = db.insertWithOnConflict(table, null, values, conflict);
                if (result >= 0) {
                    ensureBirths(db, table);
                    db.execSQL("INSERT OR REPLACE INTO " + birthsTable(table) + " VALUES (?, ?)",
                            new Object[] {result, birth});
                }
                db.setTransactionSuccessful();
                return result;
            } finally {
                db.endTransaction();
            }
        }
    }

    /** Copy history with the favorites copy, including ID-renumbering via insert(..., historicalBirth). Legacy rows retain a conservative empty birth. */
    public static void copyBirths(Context context, SQLiteDatabase source, String from,
            SQLiteDatabase destination, String to) {
        copyBirths(context, source, from, destination, to, "main");
    }

    /** An attached source must be read on the transaction-owning connection, not a second connection blocked by its exclusive transaction. */
    public static void copyBirths(Context context, SQLiteDatabase source, String from,
            SQLiteDatabase destination, String to, String sourceSchema) {
        if (!sourceSchema.matches("[A-Za-z0-9_]+")) throw new IllegalArgumentException("Invalid source schema");
        if (source == destination && sourceSchema.equals("main") && from.equals(to)) return;
        try (WorkspaceAvailabilityStore.Operation ignored = store(context).beginOperation()) {
            ensureBirths(destination, to);
            destination.delete(birthsTable(to), null, null);
            boolean hasBirths;
            try (Cursor schema = source.rawQuery("SELECT 1 FROM " + sourceSchema
                    + ".sqlite_master WHERE type='table' AND name=?",
                    new String[] {birthsTable(from)})) {
                hasBirths = schema.moveToFirst();
            }
            String sql = hasBirths ? "SELECT f._id, COALESCE(b.generation, '') FROM "
                    + sourceSchema + "." + from + " f LEFT JOIN " + sourceSchema + "."
                    + birthsTable(from) + " b ON f._id=b.item_id"
                    : "SELECT _id, '' FROM " + sourceSchema + "." + from;
            try (Cursor rows = source.rawQuery(sql, null)) {
                while (rows.moveToNext()) {
                    destination.execSQL("INSERT INTO " + birthsTable(to) + " VALUES (?, ?)",
                            new Object[] {rows.getInt(0), rows.getString(1)});
                }
            }
        }
    }
}
