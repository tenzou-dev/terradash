package dev.tenzou.terradash;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.webkit.WebView;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Menyimpan jejak crash supaya bisa ditampilkan di layar berikutnya.
 *
 * Kalau aplikasi berhenti sendiri (force close), stack trace ditulis ke
 * SharedPreferences "terradash" lalu ditampilkan sebagai layar diagnostik
 * waktu aplikasi dibuka lagi.
 */
public class TerraApp extends Application {

    static final String PREFS = "terradash";
    static final String KEY_CRASH = "last_crash";
    static final String KEY_JS = "js_errors";
    static final String KEY_STAGE = "boot_stage";

    private static Context appContext;
    private static String previousStage;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        try {
            previousStage = prefs(appContext).getString(KEY_STAGE, null);
        } catch (Throwable e) {
            previousStage = null;
        }
        mark("app");

        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    save(appContext, t, e);
                } catch (Throwable ignored) {
                }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    /* ------------------------------------------------------------------ */

    static void mark(String stage) {
        try {
            prefs(appContext).edit().putString(KEY_STAGE, stage).commit();
        } catch (Throwable ignored) {
        }
    }

    static void markOk() {
        try {
            prefs(appContext).edit().remove(KEY_STAGE).commit();
        } catch (Throwable ignored) {
        }
    }

    static String stage() {
        try {
            return prefs(appContext).getString(KEY_STAGE, null);
        } catch (Throwable e) {
            return null;
        }
    }

    /** Tahap terakhir dari proses sebelumnya. Kalau tidak null, run lalu tidak selesai. */
    static String previousStage() {
        return previousStage;
    }

    static void save(Context c, Thread t, Throwable e) {
        if (c == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("waktu   : ").append(now()).append('\n');
        sb.append("aplikasi: TerraDash ").append(versionName(c)).append('\n');
        sb.append("android : ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("device  : ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("webview : ").append(webViewInfo()).append('\n');
        sb.append("thread  : ").append(t == null ? "?" : t.getName()).append('\n');
        sb.append('\n');
        if (e != null) {
            sb.append(e.getClass().getName());
            if (e.getMessage() != null) sb.append(": ").append(e.getMessage());
            sb.append('\n');
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            sb.append(sw.toString());
        } else {
            sb.append("(tidak ada stack trace)\n");
        }
        try {
            prefs(c).edit().putString(KEY_CRASH, sb.toString()).commit();
        } catch (Throwable ignored) {
        }
    }

    static String load(Context c) {
        if (c == null) return null;
        try {
            return prefs(c).getString(KEY_CRASH, null);
        } catch (Throwable e) {
            return null;
        }
    }

    static void clear(Context c) {
        if (c == null) return;
        previousStage = null;
        try {
            prefs(c).edit().remove(KEY_CRASH).remove(KEY_STAGE).remove(KEY_JS).commit();
        } catch (Throwable ignored) {
        }
    }

    /** Error JavaScript terakhir (tidak mematikan app, tapi berguna buat diagnosa). */
    static void logJs(Context c, String msg) {
        if (c == null || msg == null) return;
        try {
            String old = prefs(c).getString(KEY_JS, "");
            String line = now() + "  " + msg;
            String all = old == null || old.isEmpty() ? line : old + "\n" + line;
            if (all.length() > 4000) all = all.substring(all.length() - 4000);
            prefs(c).edit().putString(KEY_JS, all).commit();
        } catch (Throwable ignored) {
        }
    }

    static String jsErrors(Context c) {
        if (c == null) return "";
        try {
            String s = prefs(c).getString(KEY_JS, "");
            return s == null ? "" : s;
        } catch (Throwable e) {
            return "";
        }
    }

    /* ------------------------------------------------------------------ */

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String now() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
    }

    private static String versionName(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionName == null ? "?" : pi.versionName;
        } catch (Throwable e) {
            return "?";
        }
    }

    static String webViewInfo() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PackageInfo pi = WebView.getCurrentWebViewPackage();
                if (pi != null) {
                    return pi.packageName + " " + pi.versionName
                            + " (target SDK " + pi.applicationInfo.targetSdkVersion + ")";
                }
                return "tidak terdeteksi";
            }
            return "API < 26";
        } catch (Throwable e) {
            return "error: " + e.getClass().getSimpleName();
        }
    }
}
