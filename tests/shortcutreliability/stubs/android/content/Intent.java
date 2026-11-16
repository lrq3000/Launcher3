/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package android.content;
public class Intent {
    private ComponentName component;
    public Intent setComponent(ComponentName value) { component = value; return this; }
    public ComponentName getComponent() { return component; }
    public boolean filterEquals(Intent other) { return java.util.Objects.equals(component, other.component); }
    public String toUri(int flags) { return component.flattenToString(); }
    public static Intent parseUri(String value, int flags) {
        String[] parts = value.split("/", 2);
        return new Intent().setComponent(new ComponentName(parts[0], parts[1]));
    }
}
