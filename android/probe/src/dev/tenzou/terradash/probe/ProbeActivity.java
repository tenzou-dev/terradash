package dev.tenzou.terradash.probe;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * APK uji minimal: tampilan 100% native (tanpa WebView saat start).
 * Dipakai untuk memastikan apakah masalah "kedip hitam lalu tutup" berasal
 * dari WebView atau dari proses packaging APK.
 */
public class ProbeActivity extends Activity {

    private static final String TAG = "TerraDashProbe";

    private TextView out;
    private final StringBuilder log = new StringBuilder();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(30, 24, 30, 24);

        TextView title = new TextView(this);
        title.setText("TerraDash Probe");
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);

        Button test = new Button(this);
        test.setText("Tes tampilkan WebView");
        test.setAllCaps(false);

        Button again = new Button(this);
        again.setText("Ulang tes WebView (tanpa tampil)");
        again.setAllCaps(false);

        out = new TextView(this);
        out.setTypeface(Typeface.MONOSPACE);
        out.setTextSize(11);
        out.setTextIsSelectable(true);
        out.setPadding(0, 16, 0, 0);

        ScrollView sc = new ScrollView(this);
        sc.addView(out, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(title);
        root.addView(test);
        root.addView(again);
        root.addView(sc, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        line("UI native tampil. Berarti APK terpasang & Activity jalan.");
        line("android  : " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
        line("device   : " + Build.MANUFACTURER + " " + Build.MODEL);
        line("abi      : " + (Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?"));
        line("webview  : " + webViewInfo());
        line("");
        line("Tekan tombol di atas untuk tes WebView.");
        line("Kalau app ini langsung tertutup juga -> masalahnya di APK/ROM, bukan WebView.");

        test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showWebView();
            }
        });
        again.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    WebView w = new WebView(ProbeActivity.this);
                    line("buat WebView  : OK");
                    w.destroy();
                } catch (Throwable t) {
                    line("buat WebView  : GAGAL " + t.getClass().getName() + " - " + t.getMessage());
                    Log.e(TAG, "new WebView gagal", t);
                }
            }
        });
    }

    private void line(String s) {
        log.append(s).append('\n');
        if (out != null) out.setText(log.toString());
        Log.i(TAG, s);
    }

    private static String webViewInfo() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PackageInfo pi = WebView.getCurrentWebViewPackage();
                if (pi != null) {
                    return pi.packageName + " " + pi.versionName
                            + " (target " + pi.applicationInfo.targetSdkVersion + ")";
                }
                return "tidak terdeteksi";
            }
            return "API < 26";
        } catch (Throwable t) {
            return "error " + t.getClass().getSimpleName();
        }
    }

    private void showWebView() {
        try {
            WebView w = new WebView(this);
            WebSettings s = w.getSettings();
            s.setJavaScriptEnabled(true);
            w.loadDataWithBaseURL(null,
                    "<html><body style='background:#0a1119;color:#e6edf5;font:16px sans-serif;padding:24px'>"
                            + "<h1 style='color:#f5a524'>WebView OK</h1>"
                            + "<p>Kalau layar ini tampil, WebView di HP ini normal.</p>"
                            + "<p style='font-size:13px;color:#8fa3b8'>" + webViewInfo() + "</p>"
                            + "</body></html>",
                    "text/html", "utf-8", null);
            setContentView(w);
            line("WebView ditampilkan: OK");
        } catch (Throwable t) {
            line("tampilkan WebView: GAGAL " + t.getClass().getName() + " - " + t.getMessage());
            Log.e(TAG, "WebView gagal", t);
        }
    }
}
