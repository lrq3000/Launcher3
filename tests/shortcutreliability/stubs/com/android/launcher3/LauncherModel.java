package com.android.launcher3;
import android.os.UserHandle;
import java.util.List;
public class LauncherModel {
    public int removed;
    public int unavailable;
    public int available;
    public LauncherModel newModelCallbacks() { return this; }
    public void onPackagesRemoved(UserHandle user, List<String> packages) { removed += packages.size(); }
    public void onPackagesUnavailable(String[] packages, UserHandle user, boolean replacing) { unavailable += packages.length; }
    public void onPackagesAvailable(String[] packages, UserHandle user, boolean replacing) { available += packages.length; }
}
