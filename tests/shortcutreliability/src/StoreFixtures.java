/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
import com.android.launcher3.model.availability.WorkspaceAvailabilityStore;
import java.io.File;

/** Test-only fresh-process disk reader; production cannot construct competing owners. */
public final class StoreFixtures {
    public static WorkspaceAvailabilityStore snapshot(File file) {
        try {
            var constructor = WorkspaceAvailabilityStore.class.getDeclaredConstructor(File.class);
            constructor.setAccessible(true);
            return constructor.newInstance(file);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
