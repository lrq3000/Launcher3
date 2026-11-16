/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.model;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import com.android.launcher3.model.availability.WorkspaceAvailabilityDb;
public class ModelDbController {
    public final SQLiteDatabase sql = new SQLiteDatabase();
    private final Context context;
    private int nextId = 100;
    public ModelDbController(Context context) { this.context = context; }
    public int generateNewItemId() { return ++nextId; }
    public int insert(ContentValues values) { return (int) WorkspaceAvailabilityDb.insert(context, sql, "favorites", values, 0, null); }
    public Transaction newTransaction() { return new Transaction(); }
    public int update(ContentValues v, String w, String[] a) { return 1; }
    public int delete(String w, String[] a) { return 0; }
    public void createEmptyDB() { sql.favorites.clear(); sql.births.clear(); }
    public class Transaction implements AutoCloseable {
        private final AutoCloseable operation = WorkspaceAvailabilityDb.store(context).beginOperation();
        public Transaction() { sql.beginTransaction(); }
        public void commit() { sql.setTransactionSuccessful(); }
        public void close() throws Exception { try { sql.endTransaction(); } finally { operation.close(); } }
    }
}
