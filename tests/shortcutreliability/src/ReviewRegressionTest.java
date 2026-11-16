/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
import android.content.*;
import android.content.pm.LauncherActivityInfo;
import android.os.UserHandle;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.model.*;
import com.android.launcher3.model.availability.WorkspaceAvailabilityStore;
import com.android.launcher3.model.availability.WorkspaceAvailabilityStore.Row;
import com.android.launcher3.model.data.*;
import com.android.launcher3.model.tasks.*;
import com.android.launcher3.icons.IconCache;
import java.nio.file.*;
import java.util.*;

/** Regression cases exercise the production units, not a second implementation of the journal/task policy. */
public final class ReviewRegressionTest {
    public static void main(String[] args) throws Exception { run(Integer.parseInt(args[0]), Path.of(args[1])); }
    public static void runAll(Path root) throws Exception {
        for (int finding : new int[] {1, 2, 3, 4, 5, 6}) run(finding, root);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private static void run(int finding, Path root) throws Exception {
        Context context = new Context();
        context.noBackupFilesDir = Files.createDirectory(root.resolve("finding-" + finding)).toFile();
        ModelDbController db = new ModelDbController();
        WorkspaceAvailability owner = new WorkspaceAvailability(context, db);
        UserHandle user = new UserHandle(10);
        WorkspaceItemInfo item = new WorkspaceItemInfo();
        item.id = 42; item.user = user;
        item.intent = new Intent().setComponent(new ComponentName("sd.app", "Saved"));
        db.rows.add(new Object[] {42, 10L, "sd.app/Saved"});
        LauncherModel model = new LauncherModel(); model.availability = owner;
        BgDataModel data = new BgDataModel(); data.itemsIdMap.add(item);
        ModelTaskController controller = new ModelTaskController(context, model, data);
        AllAppsList apps = new AllAppsList();
        switch (finding) {
            case 1 -> {
                Path file = context.noBackupFilesDir.toPath().resolve("workspace-availability-v1");
                WorkspaceAvailabilityStore store = WorkspaceAvailabilityStore.open(file.toFile());
                Row old = new Row("database-one", 10, 42, "sd.app/Saved");
                store.removed(Set.of(old));
                // The production allocator may reuse 42 after its former highest row is deleted. Only insertion incarnation, not a deliberately different fixture ID, may distinguish the new row.
                String generation = StoreFixtures.nextGeneration(store);
                Row replacement = StoreFixtures.row("database-one", 10, 42, "sd.app/Saved", generation);
                WorkspaceAvailabilityStore disk = StoreFixtures.snapshot(file.toFile());
                require(!disk.isRemoved(replacement), "Finding 1: reused ID/component inherited the old uninstall tombstone");
                require(disk.isRemoved(old), "Old removal must not be cleared by a new installation");
                String next = StoreFixtures.nextGeneration(disk);
                require(!next.equals(generation), "Insertion generation must advance after journal reopen");
            }
            case 2 -> {
                WorkspaceAvailability second = new WorkspaceAvailability(context, db);
                owner.beginLoad();
                owner.observe(owner.row(7, 10, new ComponentName("other.app", "A")),
                        new LauncherActivityInfo(new ComponentName("other.app", "A")));
                second.removed(user, Set.of("sd.app"));
                owner.endLoad();
                WorkspaceAvailabilityStore disk = StoreFixtures.snapshot(
                        context.noBackupFilesDir.toPath().resolve("workspace-availability-v1").toFile());
                require(disk.isRemoved(new Row("database-one", 10, 42, "sd.app/Saved")),
                        "Finding 2: second owner's uninstall was erased by the first owner's batch");
            }
            case 5 -> {
                Set<Row> rows = owner.removed(user, Set.of("sd.app"));
                PackageTaskFactory.INSTANCE.appsRemoved(user, Set.of("sd.app"), rows).execute(controller, data, apps);
                WorkspaceItemInfo newTag = new WorkspaceItemInfo();
                newTag.id = item.id; newTag.user = user; newTag.intent = item.intent;
                newTag.availabilityGeneration = WorkspaceAvailabilityStore.open(
                        context.noBackupFilesDir.toPath().resolve("workspace-availability-v1").toFile()).nextInsertionGeneration();
                context.servicesAllowed = false; db.queriesAllowed = false;
                db.identity = "different-database-after-worker";
                require(controller.lastRemovalMatcher.test(item), "Finding 5: UI matcher lost immutable membership");
                require(!controller.lastRemovalMatcher.test(newTag), "Stale UI matcher must not remove a new incarnation with the same ID/component");
            }
            case 3 -> {
                var started = new java.util.concurrent.CountDownLatch(1);
                java.util.concurrent.CompletableFuture<Set<Row>> callback;
                WorkspaceAvailabilityStore store = WorkspaceAvailabilityStore.open(
                        context.noBackupFilesDir.toPath().resolve("workspace-availability-v1").toFile());
                try (AutoCloseable migration = StoreFixtures.operation(store)) {
                    db.identity = "migrated-database";
                    db.rows.clear();
                    callback = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                        started.countDown();
                        return owner.removed(user, Set.of("sd.app"));
                    });
                    require(started.await(2, java.util.concurrent.TimeUnit.SECONDS), "Callback did not start");
                    try {
                        callback.get(200, java.util.concurrent.TimeUnit.MILLISECONDS);
                        throw new AssertionError("Finding 3: callback snapshotted empty migration destination before copy");
                    } catch (java.util.concurrent.TimeoutException expected) { }
                    db.rows.add(new Object[] {42, 10L, "sd.app/Saved"});
                }
                require(callback.get(2, java.util.concurrent.TimeUnit.SECONDS).size() == 1,
                        "Migration gate must release callback onto the completed destination");
                require(owner.reconcileRemovals() && db.rows.isEmpty(), "Migrated old row must be removed");
            }
            case 4 -> {
                db.failSnapshots = true;
                ModelLauncherCallbacks callback = new ModelLauncherCallbacks(task -> {}, owner, () -> kotlin.Unit.INSTANCE);
                callback.onPackageRemoved("sd.app", user);
                callback.onPackageAdded("sd.app", user);
                // Simulate process death after task discard; a later installation is a new row, not a package-wide removal target.
                db.insertNew(WorkspaceAvailabilityStore.open(
                        context.noBackupFilesDir.toPath().resolve("workspace-availability-v1").toFile()),
                        43, 10L, "sd.app/Saved");
                StoreFixtures.restartOwners();
                db.failSnapshots = false;
                WorkspaceAvailability restarted = new WorkspaceAvailability(context, db);
                restarted.reconcileRemovals();
                require(db.rows.stream().noneMatch(row -> row[0].equals(42)),
                        "Finding 4: failed snapshot lost the confirmed uninstall across restart");
                require(db.rows.stream().anyMatch(row -> row[0].equals(43)),
                        "Ordered pending uninstall must never remove a later installation");
                db.identityAvailable = false;
                ModelLauncherCallbacks again = new ModelLauncherCallbacks(task -> {}, restarted, () -> kotlin.Unit.INSTANCE);
                again.onPackageRemoved("sd.app", user);
                again.onPackageAdded("sd.app", user);
                db.insertNew(WorkspaceAvailabilityStore.open(
                        context.noBackupFilesDir.toPath().resolve("workspace-availability-v1").toFile()),
                        44, 10L, "sd.app/Saved");
                StoreFixtures.restartOwners();
                db.identityAvailable = true;
                new WorkspaceAvailability(context, db).reconcileRemovals();
                require(db.rows.size() == 1 && db.rows.get(0)[0].equals(44),
                        "Unavailable database identity must leave a retryable, insertion-bounded uninstall fence");
            }
            case 6 -> {
                context.apps.enabled = true;
                apps.activities = List.of(new LauncherActivityInfo(new ComponentName("sd.app", "Other")));
                context.apps.activities = apps.activities;
                controller.icons.registered = apps.activities;
                Object valid = item.bitmap;
                controller.icons.durable.put("sd.app/Saved", valid);
                new PackageUpdatedTask(PackageUpdatedTask.OP_UPDATE, user, "sd.app").execute(controller, data, apps);
                IconCache restarted = new IconCache(controller.icons.durable);
                require(restarted.durable.get("sd.app/Saved") == valid,
                        "Finding 6: package refresh erased the only durable saved-component icon");
            }
            default -> throw new IllegalArgumentException("Unknown finding " + finding);
        }
        System.out.println("PASS independent-review finding " + finding);
    }
}
