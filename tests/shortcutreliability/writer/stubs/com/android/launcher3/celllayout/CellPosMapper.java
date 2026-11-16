/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package com.android.launcher3.celllayout;
public class CellPosMapper {
    public static final CellPosMapper DEFAULT = new CellPosMapper();
    public Position mapPresenterToModel(int x, int y, int screen, int container) { return new Position(x, y, screen); }
    public static class Position {
        public final int cellX, cellY, screenId;
        Position(int x, int y, int screen) { cellX = x; cellY = y; screenId = screen; }
    }
}
