/* Copyright (C) 2026 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.model.SdCardAvailableReceiver;
import com.android.launcher3.util.PackageUserKey;
import java.util.Set;

public final class ShortcutReliabilityTest {
    public static void main(String[] args) throws Exception {
        Context context = new Context();
        LauncherModel model = new LauncherModel();
        new SdCardAvailableReceiver(context, model,
                Set.of(new PackageUserKey("sd.app", new UserHandle())))
                .onReceive(context, new Intent());
        if (model.removed != 0) {
            throw new AssertionError("Boot complete is not uninstall evidence: removed=" + model.removed);
        }
        if (model.unavailable != 1) throw new AssertionError("Missing app must remain unavailable");
        if (!context.unregistered) throw new AssertionError("Receiver must unregister");
        System.out.println("PASS receiver: absent package after boot is unavailable, not removed");
        AvailabilityStoreTest.run(java.nio.file.Path.of(args[0]));
        ModelBoundaryTest.run(java.nio.file.Path.of(args[0]));
        ReviewRegressionTest.runAll(java.nio.file.Path.of(args[0]));
        BindingReceiptTest.run(java.nio.file.Path.of(args[0]));
    }
}
