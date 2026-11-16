/*
 * Copyright (C) 2026 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
import com.android.launcher3.model.availability.WorkspaceAvailabilityStore;
import com.android.launcher3.model.availability.WorkspaceAvailabilityStore.Row;
import com.android.launcher3.model.availability.WorkspaceAvailabilityStore.Storage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class AvailabilityStoreTest {
    private static int count;
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        count++;
    }

    public static void run(Path root) throws Exception {
        Path file = root.resolve("availability.bin");
        WorkspaceAvailabilityStore store = StoreFixtures.snapshot(file.toFile());
        Row first = new Row("db-one", 10, 42, "sd.app/First");
        Row second = new Row("db-one", 10, 43, "sd.app/Second");
        Row profile = new Row("db-one", 11, 42, "sd.app/First");
        Row otherDb = new Row("db-two", 10, 42, "sd.app/First");
        Row replacement = new Row("db-one", 10, 44, "sd.app/First");
        check(store.shouldPreserve(first), "Migration without history must preserve");
        store.observe(first, Storage.EXTERNAL, "card-A");
        store.observe(second, Storage.EXTERNAL, "card-A");
        store.observe(profile, Storage.INTERNAL, "internal");
        check(!store.shouldPreserve(profile), "Known internal app outside recovery keeps normal policy");
        check(store.shouldPreserve(new Row("db-one", 11, 99, "sd.app/UnknownActivity")),
                "Package history alone cannot classify a never-observed migration target");
        store.classifyStorage(11, "sd.app", Storage.EXTERNAL, "card-B");
        check(store.shouldPreserve(profile), "Current external association supersedes old internal history without requiring activity registration");
        store.observe(profile, Storage.INTERNAL, "internal");
        store.unavailable(10, "sd.app");
        store = StoreFixtures.snapshot(file.toFile());
        check(store.shouldPreserve(first), "Recovery survives process restart");
        check(store.isRecovering(second), "Every saved component remains independently protected");
        check(!store.isRecovering(profile), "Unavailability is profile scoped");
        store.observe(first, Storage.EXTERNAL, "card-A");
        check(store.isRecovering(second), "Another component returning must not resolve Second");
        check(!store.isRecovering(first), "Exact target recovery clears its own state");
        store.removed(List.of(first, second));
        store = StoreFixtures.snapshot(file.toFile());
        check(store.isRemoved(first), "Uninstall persists before favorites mutation");
        check(!store.isRemoved(profile), "Uninstall must not affect another profile");
        check(!store.isRemoved(otherDb), "Uninstall must not affect another database");
        check(!store.isRemoved(replacement), "Old removal must not delete a new row");
        store.observe(first, Storage.EXTERNAL, "card-A");
        check(store.isRemoved(first), "Late availability cannot resurrect removed row");
        store.observe(replacement, Storage.INTERNAL, "internal");
        check(!store.isRemoved(replacement), "New installation remains independent of old removal");
        Files.write(file.resolveSibling("availability.bin.new"), new byte[] {1, 2});
        store = StoreFixtures.snapshot(file.toFile());
        check(store.isRemoved(first), "Interrupted next write retains prior committed state");
        Files.write(file, new byte[] {1, 2, 3});
        store = StoreFixtures.snapshot(file.toFile());
        check(store.shouldPreserve(profile), "Corrupt store fails safe instead of purging favorites");
        check(!store.isRemoved(first), "Corrupt store must not invent removal evidence");
        Path blocked = root.resolve("unwritable-store");
        Files.createDirectory(blocked);
        store = StoreFixtures.snapshot(blocked.toFile());
        store.observe(profile, Storage.INTERNAL, "internal");
        check(store.shouldPreserve(profile), "Failed persistence cannot authorize negative-lookup removal");
        check(store.getLastFailure() != null, "Persistence failure must remain diagnosable");
        Path batchFile = root.resolve("batched.bin");
        WorkspaceAvailabilityStore batch = StoreFixtures.snapshot(batchFile.toFile());
        batch.beginBatch();
        batch.observe(profile, Storage.INTERNAL, "internal");
        check(StoreFixtures.snapshot(batchFile.toFile()).shouldPreserve(profile),
                "Interrupted positive-observation batch leaves only conservative unknown history");
        batch.endBatch();
        check(!StoreFixtures.snapshot(batchFile.toFile()).shouldPreserve(profile),
                "Completed batch atomically commits positive storage history");
        batch.beginBatch();
        batch.protect(profile);
        check(StoreFixtures.snapshot(batchFile.toFile()).isRecovering(profile),
                "Recovery evidence bypasses observation batching before process death");
        batch.removed(List.of(profile));
        check(StoreFixtures.snapshot(batchFile.toFile()).isRemoved(profile),
                "Confirmed removals bypass batching before favorites mutation");
        batch.endBatch();
        System.out.println("PASS availability store: " + count + " behavioral assertions");
    }
}
