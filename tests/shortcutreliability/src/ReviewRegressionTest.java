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
        for (int finding : new int[] {2, 5, 6}) run(finding, root);
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
                context.servicesAllowed = false; db.queriesAllowed = false;
                db.identity = "different-database-after-worker";
                require(controller.lastRemovalMatcher.test(item), "Finding 5: UI matcher lost immutable membership");
            }
            case 6 -> {
                context.apps.enabled = true;
                apps.activities = List.of(new LauncherActivityInfo(new ComponentName("sd.app", "Other")));
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
