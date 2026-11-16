/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
import com.android.launcher3.model.availability.WorkspaceAvailabilityStore;
import java.io.File;

/** Test-only fresh-process disk reader; production cannot construct competing owners. */
public final class StoreFixtures {
    public static String nextGeneration(WorkspaceAvailabilityStore store) {
        try { return (String) store.getClass().getMethod("nextInsertionGeneration").invoke(store); }
        catch (NoSuchMethodException oldImplementation) { return ""; }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    public static WorkspaceAvailabilityStore.Row row(String database, long serial, int id,
            String component, String generation) {
        try {
            return WorkspaceAvailabilityStore.Row.class
                    .getConstructor(String.class, long.class, int.class, String.class, String.class)
                    .newInstance(database, serial, id, component, generation);
        } catch (NoSuchMethodException oldImplementation) {
            return new WorkspaceAvailabilityStore.Row(database, serial, id, component);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    public static AutoCloseable operation(WorkspaceAvailabilityStore store) {
        try { return (AutoCloseable) store.getClass().getMethod("beginOperation").invoke(store); }
        catch (NoSuchMethodException oldImplementation) { return () -> {}; }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    public static void restartOwners() {
        try {
            var field = WorkspaceAvailabilityStore.class.getDeclaredField("OWNERS");
            field.setAccessible(true);
            ((java.util.Map<?, ?>) field.get(null)).clear();
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    public static WorkspaceAvailabilityStore snapshot(File file) {
        try {
            var constructor = WorkspaceAvailabilityStore.class.getDeclaredConstructor(File.class);
            constructor.setAccessible(true);
            return constructor.newInstance(file);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
