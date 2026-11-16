package android.content;
import android.content.pm.LauncherApps;
public class Context {
    public final LauncherApps apps = new LauncherApps();
    public boolean unregistered;
    public <T> T getSystemService(Class<T> type) { return type.cast(apps); }
    public void unregisterReceiver(BroadcastReceiver receiver) { unregistered = true; }
}
