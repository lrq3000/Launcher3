/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package android.util;
public class SparseArray<T> implements Cloneable {
    private final java.util.Map<Integer,T> values = new java.util.LinkedHashMap<>();
    public void put(int id, T value) { values.put(id, value); }
    public T get(int id) { return values.get(id); }
    public void clear() { values.clear(); }
    public int size() { return values.size(); }
    public int keyAt(int i) { return new java.util.ArrayList<>(values.keySet()).get(i); }
    public T valueAt(int i) { return new java.util.ArrayList<>(values.values()).get(i); }
    public SparseArray<T> clone() { SparseArray<T> result = new SparseArray<>(); result.values.putAll(values); return result; }
}
