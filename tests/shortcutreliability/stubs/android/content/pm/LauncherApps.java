package android.content.pm;
import android.os.UserHandle;
public class LauncherApps {
    public boolean enabled;
    public boolean isPackageEnabled(String name, UserHandle user) { return enabled; }
}
