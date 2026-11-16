/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package androidx.core.util
import android.util.SparseArray
fun <T> SparseArray<T>.putAll(other: SparseArray<T>) { for (i in 0 until other.size()) put(other.keyAt(i), other.valueAt(i)) }
fun <T> SparseArray<T>.valueIterator(): Iterator<T> = (0 until size()).map { valueAt(it) }.iterator()
