/* Copyright (C) 2026 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */
import android.content.*;
import android.content.pm.LauncherActivityInfo;
import android.os.UserHandle;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.model.*;
import com.android.launcher3.model.data.*;
import com.android.launcher3.model.tasks.*;
import java.nio.file.*;
import java.util.*;

/** Real adapter, journal, update task and removal factory with explicitly limited in-memory platform/model boundaries. */
public final class ModelBoundaryTest {
    private static int count;
    private static void check(boolean result, String message) {
        if (!result) throw new AssertionError(message);
        count++;
    }
    public static void run(Path root) throws Exception {
        Context context = new Context();
        context.noBackupFilesDir = Files.createDirectory(root.resolve("model-store")).toFile();
        ModelDbController db = new ModelDbController();
        LauncherModel model = new LauncherModel();
        model.availability = new WorkspaceAvailability(context, db);
        BgDataModel data = new BgDataModel();
        ModelTaskController controller = new ModelTaskController(context, model, data);
        AllAppsList apps = new AllAppsList();
        UserHandle user = new UserHandle(10);
        WorkspaceItemInfo saved = item(42, user, "sd.app", "Saved");
        Object bitmap = saved.bitmap;
        data.itemsIdMap.add(saved);
        saved.runtimeStatusFlags = ItemInfoWithIcon.FLAG_DISABLED_QUIET_USER;

        new PackageUpdatedTask(PackageUpdatedTask.OP_UPDATE, user, "sd.app").execute(controller, data, apps);
        check(data.itemsIdMap.contains(saved), "Negative update query must retain original object");
        check(saved.bitmap == bitmap && saved.title.equals("Saved label"), "Negative update preserves appearance");
        check((saved.runtimeStatusFlags & ItemInfoWithIcon.FLAG_DISABLED_NOT_AVAILABLE) != 0, "Negative update marks unavailable");
        check(controller.icons.purged == 0, "Inferred absence must not purge icon cache");

        context.apps.enabled = true;
        apps.activities = List.of(new LauncherActivityInfo(new ComponentName("sd.app", "Other")));
        new PackageUpdatedTask(PackageUpdatedTask.OP_UPDATE, user, "sd.app").execute(controller, data, apps);
        check(saved.intent.toUri(0).equals("sd.app/Saved"), "Another target must not replace saved intent");
        check((saved.runtimeStatusFlags & ItemInfoWithIcon.FLAG_DISABLED_NOT_AVAILABLE) != 0, "Another target must not recover saved activity");

        saved.runtimeStatusFlags |= ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED;
        apps.activities = List.of(new LauncherActivityInfo(saved.getTargetComponent()));
        new PackageUpdatedTask(PackageUpdatedTask.OP_UPDATE, user, "sd.app").execute(controller, data, apps);
        check(data.itemsIdMap.size() == 1 && data.itemsIdMap.get(0) == saved && saved.id == 42, "Exact recovery keeps same identity and no duplicate");
        check(saved.cellX == 2 && saved.cellY == 3 && saved.rank == 4, "Recovery preserves placement and rank");
        check(saved.title.equals("Saved label") && saved.bitmap == bitmap, "Recovery preserves valid saved appearance");
        check((saved.runtimeStatusFlags & ItemInfoWithIcon.FLAG_DISABLED_NOT_AVAILABLE) == 0, "Exact recovery clears unavailable");
        check((saved.runtimeStatusFlags & (ItemInfoWithIcon.FLAG_DISABLED_SUSPENDED | ItemInfoWithIcon.FLAG_DISABLED_QUIET_USER)) == 6,
                "Recovery must not clear independent restrictions");

        WorkspaceItemInfo internal = item(70, user, "internal.app", "Saved");
        data.itemsIdMap.add(internal);
        model.availability.observe(internal, new LauncherActivityInfo(internal.getTargetComponent()));
        context.apps.enabled = false;
        new PackageUpdatedTask(PackageUpdatedTask.OP_UPDATE, user, "internal.app").execute(controller, data, apps);
        check(!data.itemsIdMap.contains(internal), "Known internal app outside recovery keeps normal removal policy");

        db.rows.add(new Object[] {42, 10L, "sd.app/Saved"});
        db.rows.add(new Object[] {42, 11L, "sd.app/Saved"});
        Set<com.android.launcher3.model.availability.WorkspaceAvailabilityStore.Row> snapshot =
                model.availability.removed(user, Set.of("sd.app"));
        WorkspaceItemInfo replacement = item(43, user, "sd.app", "Saved");
        data.itemsIdMap.add(replacement);
        db.rows.add(new Object[] {43, 10L, "sd.app/Saved"});
        PackageTaskFactory.INSTANCE.appsRemoved(user, Set.of("sd.app"), snapshot).execute(controller, data, apps);
        check(!data.itemsIdMap.contains(saved) && data.itemsIdMap.contains(replacement), "Queued confirmed uninstall cannot delete later row");

        model.availability = new WorkspaceAvailability(context, db);
        check(model.availability.reconcileRemovals(), "Reconciliation reports deletions for existing empty-folder cleanup");
        check(db.rows.size() == 2, "Restart applies journaled uninstall without touching new row or another profile");
        check(db.rows.stream().noneMatch(row -> row[0].equals(42) && row[1].equals(10L)), "Removed favorite stays removed after process restart");
        check(!model.availability.reconcileRemovals(), "Repeated reconciliation does not report a new deletion");
        check(db.rows.size() == 2, "Removal reconciliation is idempotent");

        // The accepting executor intentionally drops all tasks, as LauncherModel does when unloaded. Durable callback effects must precede that boundary.
        ModelLauncherCallbacks callbacks = new ModelLauncherCallbacks(task -> {}, model.availability, () -> kotlin.Unit.INSTANCE);
        callbacks.onPackagesUnavailable(new String[] {"sd.app"}, user, false);
        model.availability = new WorkspaceAvailability(context, db);
        check(model.availability.shouldPreserve(replacement), "Unavailable callback survives task discard and process death");
        callbacks = new ModelLauncherCallbacks(task -> {}, model.availability, () -> kotlin.Unit.INSTANCE);
        callbacks.onPackageRemoved("sd.app", user);
        callbacks.onPackageAdded("sd.app", user);
        db.rows.add(new Object[] {44, 10L, "sd.app/Saved"});
        model.availability = new WorkspaceAvailability(context, db);
        model.availability.reconcileRemovals();
        check(db.rows.size() == 2 && db.rows.stream().anyMatch(row -> row[0].equals(44)),
                "Removal callback precedes discard; late add cannot resurrect old row or delete a new installation");
        System.out.println("PASS real model-task boundaries: " + count + " behavioral assertions");
    }
    private static WorkspaceItemInfo item(int id, UserHandle user, String pkg, String activity) {
        WorkspaceItemInfo item = new WorkspaceItemInfo();
        item.id = id;
        item.user = user;
        item.intent = new Intent().setComponent(new ComponentName(pkg, activity));
        item.cellX = 2; item.cellY = 3; item.rank = 4;
        return item;
    }
}
