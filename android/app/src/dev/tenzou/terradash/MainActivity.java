package dev.tenzou.terradash;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String ASSET_HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + ASSET_HOST + "/assets/index.html";

    private static MainActivity instance;

    private WebView web;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile String appsCache = "[]";

    /* ------------------------------------------------------------------ */
    /* Lifecycle                                                           */
    /* ------------------------------------------------------------------ */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        /* Kalau run sebelumnya mati sebelum halaman selesai dimuat, tampilkan
           laporan error dulu supaya ketahuan penyebabnya. */
        String crash = TerraApp.load(this);
        if (crash == null) {
            String stage = TerraApp.previousStage();
            if (stage != null) crash = forcedStopReport(stage);
        }
        if (crash != null) {
            showCrashScreen(crash);
            return;
        }

        TerraApp.mark("activity");
        /* Jeda kecil: beri waktu AssetManager selesai init sebelum WebView
           provider dimuat (menghindari race bug Android 16). */
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                startDashboard();
            }
        }, 60);
    }

    private void startDashboard() {
        try {
            buildDashboard();
        } catch (Throwable t) {
            TerraApp.save(this, Thread.currentThread(), t);
            String saved = TerraApp.load(this);
            showCrashScreen(hintFor(t) + (saved != null ? saved : describe(t)));
        }
    }

    /** Penjelasan singkat untuk error yang sudah dikenal. */
    private static String hintFor(Throwable t) {
        if (t == null) return "";
        String s = String.valueOf(t.getMessage()) + " " + t.getClass().getName();
        Throwable c = t.getCause();
        if (c != null) s = s + " " + c.getMessage();
        if (s.contains("redirect ResourcesImpl") || s.contains("Package not found: com.google.android.webview")) {
            return "PENYEBAB: bug platform Android 16 (WebView provider gagal dimuat).\n"
                    + "Bukan salah setelan HP. Perbaikan:\n"
                    + "  1. Buka Play Store, update \"Android System WebView\" dan Chrome.\n"
                    + "  2. Atau buka TerraDash 2-3 kali lagi (biasanya percobaan berikutnya berhasil).\n"
                    + "  3. Kalau tetap gagal, kirim laporan di bawah ke developer.\n\n";
        }
        if (s.contains("Permission Denial") || s.contains("SecurityException")) {
            return "PENYEBAB: izin ditolak sistem.\n\n";
        }
        return "";
    }

    private void buildDashboard() {
        web = createWebViewWithRetry();
        web.setBackgroundColor(0xFF05080C);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        setContentView(web);
        TerraApp.mark("webview");

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setGeolocationEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setTextZoom(100);
        s.setLoadWithOverviewMode(false);
        s.setUseWideViewPort(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(false);
        }

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u != null && ASSET_HOST.equals(u.getHost())) {
                    return assetResponse(u.getPath());
                }
                return null;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (url != null && url.startsWith("https://" + ASSET_HOST + "/")) {
                    TerraApp.markOk();
                }
                injectBridges();
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                TerraApp.logJs(MainActivity.this,
                        "load error " + errorCode + " " + description + " @ " + failingUrl);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });

        web.loadUrl(START_URL);

        requestLocationPermission();
        buildAppsCache();
        maybeShowSetup();
        initSwc();
    }

    /**
     * Bug platform Android 16 (rilis 25Q2): pembuatan WebView bisa gagal dengan
     * "Resources$NotFoundException: failed to redirect ResourcesImpl" kalau
     * AssetManager sedang di-rebuild tepat saat WebView provider dimuat.
     * Retry beberapa kali dengan jeda kecil biasanya lolos (workaround komunitas).
     */
    private WebView createWebViewWithRetry() {
        Throwable last = null;
        final int attempts = 4;
        for (int i = 0; i < attempts; i++) {
            try {
                if (i > 0) {
                    TerraApp.logJs(this, "retry buat WebView #" + (i + 1));
                }
                return new WebView(this);
            } catch (Throwable t) {
                last = t;
                try {
                    Thread.sleep(120L * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        TerraApp.logJs(this, "WebView gagal dibuat setelah " + attempts + " percobaan");
        if (last instanceof RuntimeException) throw (RuntimeException) last;
        throw new RuntimeException(last);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        hideSystemUi();
        registerSwcReceiver();
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        unregisterSwcReceiver();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    public void onBackPressed() {
        /* sebagai launcher: back tidak keluar. Kalau dibuka sebagai app biasa,
           back menyembunyikan dashboard (kembali ke app sebelumnya). */
        if (!isDefaultLauncher()) moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        if (instance == this) instance = null;
        super.onDestroy();
    }

    private void hideSystemUi() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                getWindow().setDecorFitsSystemWindows(false);
                WindowInsetsController c = getWindow().getInsetsController();
                if (c != null) {
                    c.hide(WindowInsets.Type.systemBars());
                    c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            }
        } catch (Throwable ignored) {
            /* sebagian ROM melempar error di sini; tampilan saja yang kurang fullscreen */
        }
    }

    /* ------------------------------------------------------------------ */
    /* Layar diagnostik crash                                              */
    /* ------------------------------------------------------------------ */

    private static String describe(Throwable t) {
        if (t == null) return "(tanpa keterangan)";
        java.io.StringWriter sw = new java.io.StringWriter();
        t.printStackTrace(new java.io.PrintWriter(sw));
        return t.getClass().getName() + ": " + t.getMessage() + "\n\n" + sw;
    }

    private static String forcedStopReport(String stage) {
        return "TerraDash berhenti paksa sebelum halaman selesai dimuat.\n"
                + "Tahap terakhir: " + stage + "\n\n"
                + "Ini biasanya crash native (WebView / driver GPU) atau proses dibunuh sistem,\n"
                + "bukan exception Java, jadi tidak ada stack trace.\n"
                + "Kalau terjadi berulang: update \"Android System WebView\" + Chrome di Play Store,\n"
                + "lalu buka TerraDash lagi.\n\n"
                + "Info perangkat:\n"
                + "  android : " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")\n"
                + "  device  : " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "  webview : " + TerraApp.webViewInfo() + "\n";
    }

    private void showCrashScreen(String text) {
        try {
            showCrashScreenUi(text);
        } catch (Throwable t) {
            /* layar diagnostik pun gagal dibuat: tampilkan versi paling sederhana */
            try {
                TextView tv = new TextView(this);
                tv.setText(text + "\n\n[layar diagnostik gagal: " + t.getClass().getSimpleName() + "]");
                tv.setTextColor(0xFFFFFFFF);
                tv.setTextSize(11);
                tv.setTypeface(Typeface.MONOSPACE);
                tv.setTextIsSelectable(true);
                tv.setPadding(24, 24, 24, 24);
                setContentView(tv);
            } catch (Throwable ignored) {
            }
        }
    }

    private void showCrashScreenUi(String text) {
        String body = text == null || text.trim().isEmpty() ? "(laporan kosong)" : text;
        String js = TerraApp.jsErrors(this);
        if (js != null && !js.isEmpty()) {
            body = body + "\n--- error JavaScript ---\n" + js;
        }
        final String report = body;

        float d = getResources().getDisplayMetrics().density;
        int pad = Math.round(18 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF05080C);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("TerraDash berhenti");
        title.setTextColor(0xFFFF7A7A);
        title.setTextSize(21);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        TextView sub = new TextView(this);
        sub.setText("Ketuk teks di bawah untuk menyalin laporan, lalu kirim ke developer. "
                + "Tombol \"Coba lagi\" akan menghapus laporan ini dan membuka dashboard.");
        sub.setTextColor(0xFFB8C4D4);
        sub.setTextSize(12);
        sub.setPadding(0, Math.round(6 * d), 0, Math.round(10 * d));

        final TextView trace = new TextView(this);
        trace.setText(report);
        trace.setTextColor(0xFFE6EDF5);
        trace.setTextSize(11);
        trace.setTypeface(Typeface.MONOSPACE);
        trace.setTextIsSelectable(true);
        trace.setPadding(Math.round(10 * d), Math.round(10 * d), Math.round(10 * d), Math.round(10 * d));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF0A1119);
        scroll.addView(trace, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        root.addView(title);
        root.addView(sub);
        root.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Math.round(10 * d), 0, 0);

        Button copy = new Button(this);
        copy.setText("Salin");
        copy.setAllCaps(false);

        Button retry = new Button(this);
        retry.setText("Coba lagi");
        retry.setAllCaps(false);

        Button wv = new Button(this);
        wv.setText("Info WebView");
        wv.setAllCaps(false);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(copy, new LinearLayout.LayoutParams(bp));
        row.addView(retry, new LinearLayout.LayoutParams(bp));
        row.addView(wv, new LinearLayout.LayoutParams(bp));
        root.addView(row);

        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyToClipboard(report);
            }
        });
        trace.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copyToClipboard(report);
            }
        });
        retry.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                TerraApp.clear(MainActivity.this);
                recreate();
            }
        });
        wv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openWebViewDetails();
            }
        });

        setContentView(root);
    }

    private void copyToClipboard(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("TerraDash error", text));
                toast("Laporan disalin");
            }
        } catch (Throwable t) {
            toast("Gagal menyalin");
        }
    }

    private void openWebViewDetails() {
        String pkg = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.content.pm.PackageInfo pi = WebView.getCurrentWebViewPackage();
                if (pi != null) pkg = pi.packageName;
            }
        } catch (Throwable ignored) {
        }
        try {
            Intent i;
            if (pkg != null) {
                i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
            } else {
                i = new Intent(Settings.ACTION_APPLICATION_SETTINGS);
            }
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            toast("Tidak bisa membuka setelan");
        }
    }

    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, 1001);
        }
    }

    /* ------------------------------------------------------------------ */
    /* Asset loader (origin https://appassets.androidplatform.net)         */
    /* ------------------------------------------------------------------ */

    private WebResourceResponse assetResponse(String path) {
        String rel = path == null ? "" : path;
        while (rel.startsWith("/")) rel = rel.substring(1);
        if (rel.startsWith("assets/")) rel = rel.substring(7);
        if (rel.isEmpty() || rel.endsWith("/")) rel = rel + "index.html";
        try {
            InputStream in = getAssets().open(rel);
            String mime = mimeOf(rel);
            /* charset wajib untuk teks: tanpa ini WebView bisa menebak
               Windows-1252 dan karakter seperti © / ’ jadi rusak. */
            String enc = isTextMime(mime) ? "utf-8" : null;
            WebResourceResponse r = new WebResourceResponse(mime, enc, in);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                r.setStatusCodeAndReasonPhrase(200, "OK");
                java.util.Map<String, String> h = new java.util.HashMap<>();
                h.put("Cache-Control", "no-cache");
                r.setResponseHeaders(h);
            }
            return r;
        } catch (Exception e) {
            WebResourceResponse r = new WebResourceResponse(
                    "text/plain", "UTF-8",
                    new ByteArrayInputStream("not found".getBytes(StandardCharsets.UTF_8)));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                r.setStatusCodeAndReasonPhrase(404, "Not Found");
            }
            return r;
        }
    }

    private static boolean isTextMime(String mime) {
        if (mime == null) return false;
        return mime.startsWith("text/")
                || mime.equals("application/javascript")
                || mime.equals("application/json")
                || mime.equals("image/svg+xml");
    }

    private static String mimeOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".html") || n.endsWith(".htm")) return "text/html";
        if (n.endsWith(".js")) return "application/javascript";
        if (n.endsWith(".css")) return "text/css";
        if (n.endsWith(".json")) return "application/json";
        if (n.endsWith(".woff2")) return "font/woff2";
        if (n.endsWith(".woff")) return "font/woff";
        if (n.endsWith(".ttf")) return "font/ttf";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".svg")) return "image/svg+xml";
        if (n.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    /* ------------------------------------------------------------------ */
    /* JS bridges                                                          */
    /* ------------------------------------------------------------------ */

    private void injectBridges() {
        String js =
                "(function(){" +
                "  if(!window.AndroidBridge) return;" +
                "  window.HUApps = {" +
                "    list: function(){ try { return AndroidBridge.apps(); } catch(e){ return '[]'; } }," +
                "    launch: function(p){ try { AndroidBridge.launch(p); } catch(e){} }" +
                "  };" +
                "  window.HUMedia = {" +
                "    state: function(){ try { return AndroidBridge.mediaState(); } catch(e){ return 'null'; } }," +
                "    play: function(){ AndroidBridge.mediaCmd('play',''); }," +
                "    pause: function(){ AndroidBridge.mediaCmd('pause',''); }," +
                "    next: function(){ AndroidBridge.mediaCmd('next',''); }," +
                "    prev: function(){ AndroidBridge.mediaCmd('prev',''); }," +
                "    seek: function(ms){ AndroidBridge.mediaCmd('seek', String(ms)); }" +
                "  };" +
                "  try { if(window.renderDock) renderDock(); } catch(e){}" +
                "  try { if(window.renderDockPick) renderDockPick(); } catch(e){}" +
                "  try { if(window.syncMedia) syncMedia(); } catch(e){}" +
                "  try { if(window.refreshBridgeButtons) refreshBridgeButtons(); } catch(e){}" +
                "})();";
        web.evaluateJavascript(js, null);
    }

    public class Bridge {

        @JavascriptInterface
        public String apps() {
            return appsCache;
        }

        @JavascriptInterface
        public void launch(String pkg) {
            if (pkg == null || pkg.isEmpty()) return;
            try {
                Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
                if (i == null) {
                    toast("Tidak bisa membuka " + pkg);
                    return;
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(i);
            } catch (Exception e) {
                toast("Gagal membuka aplikasi");
            }
        }

        @JavascriptInterface
        public String mediaState() {
            MediaListener m = MediaListener.get();
            return m == null ? "null" : m.stateJson();
        }

        @JavascriptInterface
        public void mediaCmd(String cmd, String arg) {
            MediaListener.command(cmd, arg);
        }

        @JavascriptInterface
        public boolean notifAccess() {
            return hasNotifAccess();
        }

        @JavascriptInterface
        public void openNotifAccess() {
            openNotificationAccess();
        }

        @JavascriptInterface
        public void openHomeSettings() {
            try {
                startActivity(new Intent(Settings.ACTION_HOME_SETTINGS));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_SETTINGS));
                } catch (Exception ignored) {
                }
            }
        }

        /** Info aplikasi — di sinilah menu "Allow restricted settings" berada. */
        @JavascriptInterface
        public void openAppInfo() {
            try {
                Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                toast("Tidak bisa membuka info aplikasi");
            }
        }

        @JavascriptInterface
        public boolean isDefaultHome() {
            return isDefaultLauncher();
        }

        @JavascriptInterface
        public void toast(String msg) {
            MainActivity.this.toast(msg);
        }

        @JavascriptInterface
        public void logError(String msg) {
            TerraApp.logJs(MainActivity.this, msg);
        }
    }

    private void toast(String msg) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* Media push (dari MediaListener)                                     */
    /* ------------------------------------------------------------------ */

    public static void pushMedia(final String json) {
        final MainActivity a = instance;
        if (a == null || a.web == null) return;
        final String js = "window.HUMediaUpdate && window.HUMediaUpdate(" + JSONObject.quote(json) + ")";
        a.ui.post(new Runnable() {
            @Override
            public void run() {
                if (a.web != null) a.web.evaluateJavascript(js, null);
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /* Notification access                                                 */
    /* ------------------------------------------------------------------ */

    private boolean hasNotifAccess() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                return nm != null && nm.isNotificationListenerAccessGranted(
                        new ComponentName(this, MediaListener.class));
            }
            String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            return flat != null && flat.contains(getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private void openNotificationAccess() {
        try {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Daftar aplikasi untuk dock                                          */
    /* ------------------------------------------------------------------ */

    private void buildAppsCache() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String json = buildAppsJson();
                appsCache = json;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (web == null) return;
                        web.evaluateJavascript(
                                "try{if(window.renderDock)renderDock();}catch(e){}" +
                                "try{if(window.renderDockPick)renderDockPick();}catch(e){}",
                                null);
                    }
                });
            }
        }, "apps-cache").start();
    }

    private String buildAppsJson() {
        JSONArray arr = new JSONArray();
        try {
            Intent main = new Intent(Intent.ACTION_MAIN);
            main.addCategory(Intent.CATEGORY_LAUNCHER);
            PackageManager pm = getPackageManager();
            List<ResolveInfo> list = pm.queryIntentActivities(main, 0);
            List<ResolveInfo> sorted = new ArrayList<>(list);
            Collections.sort(sorted, new Comparator<ResolveInfo>() {
                @Override
                public int compare(ResolveInfo a, ResolveInfo b) {
                    String la = a.loadLabel(pm).toString();
                    String lb = b.loadLabel(pm).toString();
                    return la.compareToIgnoreCase(lb);
                }
            });

            Set<String> seen = new HashSet<>();
            for (ResolveInfo ri : sorted) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || seen.contains(pkg)) continue;
                seen.add(pkg);
                try {
                    JSONObject o = new JSONObject();
                    o.put("pkg", pkg);
                    o.put("label", ri.loadLabel(pm).toString());
                    o.put("icon", iconBase64(ri.loadIcon(pm)));
                    arr.put(o);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            /* biarkan kosong */
        }
        return arr.toString();
    }

    private static String iconBase64(Drawable d) {
        if (d == null) return "";
        try {
            int size = 96;
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            d.setBounds(0, 0, size, size);
            d.draw(c);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            bmp.recycle();
            return "data:image/png;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    /* ------------------------------------------------------------------ */
    /* Dialog pertama kali                                                 */
    /* ------------------------------------------------------------------ */

    private void maybeShowSetup() {
        SharedPreferences sp = getSharedPreferences("terradash", MODE_PRIVATE);
        if (sp.getBoolean("setup_done", false)) return;
        sp.edit().putBoolean("setup_done", true).apply();

        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                StringBuilder msg = new StringBuilder();
                msg.append("Supaya tombol musik di dock berfungsi, TerraDash butuh izin akses notifikasi.\n\n");
                msg.append("Kalau muncul \"App was denied access\": buka Info aplikasi → titik tiga (⋮) → Allow restricted settings, lalu aktifkan lagi akses notifikasinya (tombol di Pengaturan → Tentang aplikasi).\n\n");
                if (!isDefaultLauncher()) {
                    msg.append("Setelah itu tekan tombol HOME, pilih TerraDash, lalu pilih \"Selalu\" supaya langsung jadi layar utama.\n\n");
                }
                msg.append("Izin lokasi juga akan diminta saat peta mulai jalan.");

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("TerraDash")
                        .setMessage(msg.toString())
                        .setPositiveButton("Buka izin", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                openNotificationAccess();
                            }
                        })
                        .setNegativeButton("Nanti", null)
                        .show();
            }
        }, 1200);
    }

    private boolean isDefaultLauncher() {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo r = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
            return r != null && r.activityInfo != null && getPackageName().equals(r.activityInfo.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Tombol steerwheel / headset                                         */
    /*                                                                     */
    /* Head unit mengirim tombol setir lewat salah satu dari tiga jalur:   */
    /*   1. key event biasa ke jendela yang sedang fokus                  */
    /*   2. media button lewat MediaSession (jalur resmi Android)          */
    /*   3. broadcast gaya lama "musicservicecommand"                     */
    /* Ketiganya ditangkap di sini lalu diteruskan ke MediaListener,       */
    /* supaya next/prev/play-pause tetap jalan walau yang di depan layar   */
    /* adalah TerraDash (bukan app pemutar musiknya).                      */
    /* ------------------------------------------------------------------ */

    private MediaSession swcSession;
    private boolean swcReceiverOn = false;

    private final BroadcastReceiver swcReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String cmd = intent.getStringExtra("command");
            if (cmd == null) cmd = intent.getStringExtra("action");
            if (cmd == null) cmd = intent.getStringExtra("button");
            if (cmd == null) return;
            String c = cmd.toLowerCase(Locale.US);
            if (c.indexOf("next") >= 0 || c.indexOf("forward") >= 0) {
                forward("next");
            } else if (c.indexOf("prev") >= 0 || c.indexOf("rewind") >= 0) {
                forward("prev");
            } else if (c.indexOf("toggle") >= 0 || c.indexOf("playpause") >= 0) {
                forward("toggle");
            } else if (c.equals("play") || c.equals("resume")) {
                forward("play");
            } else if (c.equals("pause") || c.equals("stop")) {
                forward("pause");
            }
        }
    };

    private void initSwc() {
        try {
            swcSession = new MediaSession(this, "TerraDash");
            swcSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                    | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
            swcSession.setCallback(new MediaSession.Callback() {
                @Override
                public void onPlay() {
                    forward("play");
                }

                @Override
                public void onPause() {
                    forward("pause");
                }

                @Override
                public void onStop() {
                    forward("pause");
                }

                @Override
                public void onSkipToNext() {
                    forward("next");
                }

                @Override
                public void onSkipToPrevious() {
                    forward("prev");
                }

                @Override
                public void onPlayFromMediaId(String mediaId, Bundle extras) {
                    forward("play");
                }
            });
            swcSession.setActive(true);
            Log.d("TerraDash", "swc: media session aktif");
        } catch (Throwable t) {
            Log.w("TerraDash", "swc: gagal membuat media session", t);
        }
    }

    private void registerSwcReceiver() {
        if (swcReceiverOn) return;
        try {
            IntentFilter f = new IntentFilter();
            f.addAction("com.android.music.musicservicecommand");
            f.addAction("com.sec.android.app.music.musicservicecommand");
            f.addAction("com.htc.music.musicservicecommand");
            f.addAction("com.miui.player.musicservicecommand");
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(swcReceiver, f, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(swcReceiver, f);
            }
            swcReceiverOn = true;
        } catch (Throwable t) {
            Log.w("TerraDash", "swc: gagal daftar receiver", t);
        }
    }

    private void unregisterSwcReceiver() {
        if (!swcReceiverOn) return;
        try {
            unregisterReceiver(swcReceiver);
        } catch (Throwable ignored) {
        }
        swcReceiverOn = false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null && handleMediaKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean handleMediaKey(KeyEvent e) {
        String cmd;
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_HEADSETHOOK:
                cmd = "toggle";
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY:
                cmd = "play";
                break;
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_STOP:
                cmd = "pause";
                break;
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                cmd = "next";
                break;
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                cmd = "prev";
                break;
            default:
                return false;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            forward(cmd);
        }
        return true;
    }

    private void forward(String cmd) {
        Log.d("TerraDash", "swc -> " + cmd);
        MediaListener.command(cmd, "");
    }
}
