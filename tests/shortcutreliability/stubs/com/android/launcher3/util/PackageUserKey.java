package com.android.launcher3.util;
import android.os.UserHandle;
public class PackageUserKey {
    public final String mPackageName;
    public final UserHandle mUser;
    public PackageUserKey(String name, UserHandle user) { mPackageName = name; mUser = user; }
}
