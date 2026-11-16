/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package android.content;
public class ContentValues extends java.util.HashMap<String, Object> {
    public ContentValues() {}
    public ContentValues(ContentValues other) { super(other); }
    public Integer getAsInteger(String key) { Object value = get(key); return value == null ? null : ((Number) value).intValue(); }
    public String getAsString(String key) { Object value = get(key); return value == null ? null : value.toString(); }
}
