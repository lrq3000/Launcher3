/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package android.content;
public class ComponentName {
    private final String pkg;
    private final String cls;
    public ComponentName(String pkg, String cls) { this.pkg = pkg; this.cls = cls; }
    public String getPackageName() { return pkg; }
    public String flattenToString() { return pkg + "/" + cls; }
    @Override public boolean equals(Object o) {
        return o instanceof ComponentName && flattenToString().equals(((ComponentName) o).flattenToString());
    }
    @Override public int hashCode() { return flattenToString().hashCode(); }
}
