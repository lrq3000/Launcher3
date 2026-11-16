/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package android.database.sqlite;
import android.content.ContentValues;
import android.database.Cursor;
import java.util.*;

/** Explicit SQL boundary: nested transactions roll back together. It does not implement the journal or ModelWriter policy. */
public class SQLiteDatabase {
    public static final int CONFLICT_NONE = 0, CONFLICT_REPLACE = 5;
    public final Map<Integer, ContentValues> favorites = new LinkedHashMap<>();
    public final Map<Integer, String> births = new LinkedHashMap<>();
    public Runnable beforeInsert = () -> {};
    public boolean rejectInsert, failClose;
    private final Deque<Boolean> successful = new ArrayDeque<>();
    private boolean rollback;
    private Map<Integer, ContentValues> savedFavorites;
    private Map<Integer, String> savedBirths;
    public void beginTransaction() {
        if (successful.isEmpty()) {
            savedFavorites = new LinkedHashMap<>(favorites); savedBirths = new LinkedHashMap<>(births); rollback = false;
        }
        successful.push(false);
    }
    public void setTransactionSuccessful() { successful.pop(); successful.push(true); }
    public void endTransaction() {
        if (!successful.pop()) rollback = true;
        if (successful.isEmpty()) {
            if (rollback || failClose) {
                favorites.clear(); favorites.putAll(savedFavorites); births.clear(); births.putAll(savedBirths);
            }
            if (failClose) throw new IllegalStateException("Simulated transaction close failure");
        }
    }
    public long insertWithOnConflict(String table, String nullHack, ContentValues values, int conflict) {
        if (!table.equals("favorites")) throw new AssertionError("Unexpected table " + table);
        beforeInsert.run();
        if (rejectInsert) return -1;
        Integer id = values.getAsInteger("_id");
        if (id == null) id = favorites.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        if (favorites.containsKey(id) && conflict != CONFLICT_REPLACE) return -1;
        favorites.put(id, new ContentValues(values));
        return id;
    }
    public void execSQL(String sql) { execSQL(sql, new Object[0]); }
    public void execSQL(String sql, Object[] args) {
        if (sql.startsWith("CREATE TABLE IF NOT EXISTS ")) return;
        if (sql.equals("INSERT OR REPLACE INTO workspace_availability_rows_favorites VALUES (?, ?)")) {
            births.put(((Number) args[0]).intValue(), args[1].toString()); return;
        }
        throw new AssertionError("Unexpected SQL " + sql);
    }
    public Cursor rawQuery(String sql, String[] args) {
        if (sql.equals("SELECT generation FROM workspace_availability_rows_favorites WHERE item_id=?")) {
            String birth = births.get(Integer.parseInt(args[0]));
            return new Cursor(birth == null ? List.of() : Collections.singletonList(new Object[] {birth}));
        }
        throw new AssertionError("Unexpected query " + sql);
    }
    public int delete(String table, String where, String[] args) { throw new AssertionError("Unexpected delete"); }
}
