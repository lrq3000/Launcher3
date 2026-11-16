/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package android.os;
public class CancellationSignal { private volatile boolean canceled; public void cancel() { canceled = true; } public boolean isCanceled() { return canceled; } }
