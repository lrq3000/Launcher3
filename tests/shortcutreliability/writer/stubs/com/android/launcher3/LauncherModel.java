/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3;
import com.android.launcher3.model.*;
public class LauncherModel {
    public int lastLoadId, reloads;
    private final ModelDbController db;
    public ModelTaskController controller;
    public BgDataModel data;
    public boolean loaded = true;
    public LauncherModel(ModelDbController db) { this.db = db; }
    public ModelDbController getModelDbController() { return db; }
    public WorkspaceAvailability getWorkspaceAvailability() { return new WorkspaceAvailability(); }
    public void enqueueModelUpdateTask(ModelUpdateTask task) { if (loaded) task.execute(controller, data, new AllAppsList()); }
    public java.util.concurrent.CompletionStage<kotlin.Unit> forceReload(String reason) { reloads++; return java.util.concurrent.CompletableFuture.completedFuture(kotlin.Unit.INSTANCE); }
    public interface ModelUpdateTask { void execute(ModelTaskController c, BgDataModel d, AllAppsList a); }
}
