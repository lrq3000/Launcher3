/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
import android.content.Context;
import android.os.UserHandle;
import com.android.launcher3.model.*;
import java.nio.file.*;
import java.util.*;

/** Real callback/journal ordering: invalidation must precede a failing snapshot, replacement must still be requested afterward. */
public final class BindingReceiptTest {
    public static void run(Path root) throws Exception {
        Context context = new Context();
        context.noBackupFilesDir = Files.createDirectory(root.resolve("binding-receipt")).toFile();
        ModelDbController db = new ModelDbController();
        db.failSnapshots = true;
        WorkspaceBindingGuard guard = new WorkspaceBindingGuard();
        var old = guard.capture();
        List<String> order = new ArrayList<>();
        List<WorkspaceBindingGuard.Token> intervening = new ArrayList<>();
        db.beforeSnapshot = () -> {
            if (old.isCurrent()) throw new AssertionError("Uninstall reached SQL before invalidating UI delivery");
            if (!Files.isRegularFile(context.noBackupFilesDir.toPath().resolve("workspace-availability-v1"))) {
                throw new AssertionError("Snapshot failure can precede durable removal evidence");
            }
            order.add("query");
            intervening.add(guard.capture());
            if (intervening.get(0).isCurrent()) throw new AssertionError("Pending receipt authorized a new snapshot before journal completion");
        };
        ModelLauncherCallbacks callbacks = new ModelLauncherCallbacks(task -> order.add("task"),
                new WorkspaceAvailability(context, db), () -> kotlin.Unit.INSTANCE,
                () -> { guard.beginRemoval(); order.add("receipt"); return kotlin.Unit.INSTANCE; },
                () -> { guard.finishRemoval(); order.add("reload"); return kotlin.Unit.INSTANCE; });
        callbacks.onPackageRemoved("sd.app", new UserHandle(10));
        if (!order.equals(List.of("receipt", "query", "reload", "task"))) throw new AssertionError("Wrong removal order " + order);
        var current = guard.capture();
        if (!current.isCurrent() || intervening.get(0).isCurrent()) throw new AssertionError("Receipt barrier reopened the wrong snapshot generation");
        callbacks.onPackagesUnavailable(new String[] {"sd.app"}, new UserHandle(10), false);
        if (!current.isCurrent()) throw new AssertionError("Temporary unavailability must not invalidate unrelated pending pages");
        System.out.println("PASS true uninstall receipt: invalidate before snapshot, replacement after failure, temporary hints unchanged");
    }
}
