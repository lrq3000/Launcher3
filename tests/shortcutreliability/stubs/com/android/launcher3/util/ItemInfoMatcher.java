/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.util;
import com.android.launcher3.model.data.*;
import android.os.UserHandle;
import java.util.*;
import java.util.function.Predicate;
public class ItemInfoMatcher {
    public static Predicate<ItemInfo> ofPackages(Set<String> packages, UserHandle user) {
        return item -> item instanceof WorkspaceItemInfo && item.user == user && packages.contains(((WorkspaceItemInfo) item).getTargetPackage());
    }
    public static Predicate<ItemInfo> ofItems(Collection<? extends ItemInfo> items) {
        Set<Integer> ids = new HashSet<>();
        items.forEach(item -> ids.add(item.id));
        return item -> ids.contains(item.id);
    }
    public static Predicate<ItemInfo> ofItemIds(IntSet ids) { return item -> ids.contains(item.id); }
}
