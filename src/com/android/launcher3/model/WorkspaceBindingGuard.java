/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.model;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** In-memory delivery fence. No database, services, model locks or cancellation listeners are consulted. */
public final class WorkspaceBindingGuard {
    private final AtomicLong mGeneration = new AtomicLong();
    private final AtomicInteger mPendingRemovals = new AtomicInteger();
    private final Object mDeliveryLock = new Object();

    /** Capture before reading a load's data, not when an arbitrarily delayed binder finally enters. */
    public Token capture() {
        long generation = mGeneration.get();
        return new Token(this, generation, mPendingRemovals.get() == 0);
    }

    /** No snapshot taken between receipt and durable recording may borrow the new generation. Overlapping removals keep the barrier closed until all have recorded. */
    public void beginRemoval() {
        // Close admission immediately, including while an earlier UI batch finishes. No later callback may barge ahead while receipt waits for that batch's delivery lock.
        mPendingRemovals.incrementAndGet();
        synchronized (mDeliveryLock) {
            mGeneration.incrementAndGet();
        }
    }

    public void finishRemoval() {
        synchronized (mDeliveryLock) {
            if (mPendingRemovals.get() == 0) throw new IllegalStateException("No removal in progress");
            mGeneration.incrementAndGet();
            mPendingRemovals.decrementAndGet();
        }
    }

    /** Immutable provenance carried by every copy of the corresponding workspace snapshot. */
    public static final class Token {
        private final WorkspaceBindingGuard mOwner;
        private final long mGeneration;
        private final boolean mEligibleAtCapture;
        private Token(WorkspaceBindingGuard owner, long generation, boolean eligible) {
            mOwner = owner;
            mGeneration = generation;
            mEligibleAtCapture = eligible;
        }
        public boolean isCurrent() {
            return mEligibleAtCapture && mOwner.mPendingRemovals.get() == 0
                    && mOwner.mGeneration.get() == mGeneration;
        }

        /** UI-side delivery linearizes with receipt. Worker capture and registration stay lock-free, so SQL/model holders do not wait here; receipt itself holds neither SQL nor model locks. Inflation is outside this lock. */
        public boolean runIfCurrent(Runnable delivery) {
            synchronized (mOwner.mDeliveryLock) {
                if (!isCurrent()) return false;
                delivery.run();
                return true;
            }
        }

        /** Lock-free registration closes the token-check/signal-allocation race without letting a late old binder cancel a fresh generation. */
        public Session start(AtomicReference<Session> active) {
            Session next = new Session(this);
            while (isCurrent()) {
                Session previous = active.get();
                if (previous != null && previous.mToken.mOwner == mOwner
                        && previous.mToken.mGeneration > mGeneration) return null;
                if (active.compareAndSet(previous, next)) {
                    if (previous != null) previous.cancel();
                    if (next.isCanceled()) return null;
                    return next;
                }
            }
            return null;
        }
    }

    /** Existing per-bind cancellation plus immutable receipt-generation provenance. */
    public static final class Session {
        private final Token mToken;
        private volatile boolean mCanceled;
        private Session(Token token) { mToken = token; }
        public void cancel() { mCanceled = true; }
        public boolean isCanceled() { return mCanceled || !mToken.isCurrent(); }
        public void runIfCurrent(Runnable delivery) {
            mToken.runIfCurrent(() -> { if (!mCanceled) delivery.run(); });
        }
    }
}
