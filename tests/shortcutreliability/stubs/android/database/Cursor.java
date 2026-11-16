/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package android.database;
public class Cursor implements AutoCloseable {
    private final java.util.List<Object[]> rows;
    private int index = -1;
    public Cursor(java.util.List<Object[]> rows) { this.rows = rows; }
    public boolean moveToNext() { return ++index < rows.size(); }
    public int getInt(int column) { return ((Number) rows.get(index)[column]).intValue(); }
    public long getLong(int column) { return ((Number) rows.get(index)[column]).longValue(); }
    public String getString(int column) { return (String) rows.get(index)[column]; }
    @Override public void close() {}
}
