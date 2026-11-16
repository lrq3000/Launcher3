/* Copyright (C) 2026 The Android Open Source Project; SPDX-License-Identifier: Apache-2.0 */
package android.util;
public class Pair<A,B> { public final A first; public final B second; private Pair(A a,B b) { first=a; second=b; } public static <A,B> Pair<A,B> create(A a,B b) { return new Pair<>(a,b); } }
