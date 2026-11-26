package com.quranaudio.sudais.minshawi;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new AudioBridge(), "AndroidQuranAudio");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    public class AudioBridge {
        @JavascriptInterface
        public String getLocalUrl(String reciter, String sPad) {
            File saved = new File(new File(getFilesDir(), "quran_audio/" + reciter), sPad + ".mp3");
            if (saved.exists() && saved.length() > 4096) {
                return "file://" + saved.getAbsolutePath();
            }
            String assetPath = "audio/" + reciter + "/" + sPad + ".mp3";
            try (InputStream is = getAssets().open(assetPath)) {
                return "file:///android_asset/" + assetPath;
            } catch (Exception ignored) {
            }
            return "";
        }

        @JavascriptInterface
        public boolean isDownloaded(String reciter, String sPad) {
            return !getLocalUrl(reciter, sPad).isEmpty();
        }

        @JavascriptInterface
        public String listDownloaded(String reciter) {
            Set<String> nums = new HashSet<>();
            File dir = new File(getFilesDir(), "quran_audio/" + reciter);
            if (dir.exists() && dir.listFiles() != null) {
                for (File f : dir.listFiles()) {
                    if (f.getName().endsWith(".mp3") && f.length() > 4096) {
                        nums.add(f.getName().replace(".mp3", ""));
                    }
                }
            }
            try {
                String[] assets = getAssets().list("audio/" + reciter);
                if (assets != null) {
                    for (String a : assets) {
                        if (a.endsWith(".mp3")) nums.add(a.replace(".mp3", ""));
                    }
                }
            } catch (Exception ignored) {
            }
            return String.join(",", nums);
        }

        @JavascriptInterface
        public void downloadSurah(String reciter, String sPad, String url1, String url2) {
            int surahNum = Integer.parseInt(sPad);
            executor.execute(() -> {
                boolean ok = fetchToStorage(reciter, sPad, surahNum, url1);
                if (!ok && url2 != null && !url2.equals(url1)) {
                    ok = fetchToStorage(reciter, sPad, surahNum, url2);
                }
                final int finalPct = ok ? 100 : -1;
                notifyJs(reciter, surahNum, finalPct);
            });
        }

        private boolean fetchToStorage(String reciter, String sPad, int surahNum, String remoteUrl) {
            try {
                File dir = new File(getFilesDir(), "quran_audio/" + reciter);
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, sPad + ".mp3");
                File tmp = new File(dir, sPad + ".tmp");

                HttpURLConnection conn = (HttpURLConnection) new URL(remoteUrl).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.connect();
                if (conn.getResponseCode() != 200) {
                    conn.disconnect();
                    return false;
                }
                long total = conn.getContentLengthLong();
                long downloaded = 0;
                int lastPct = 0;
                try (InputStream in = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[16384];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        fos.write(buf, 0, n);
                        downloaded += n;
                        if (total > 0) {
                            int pct = (int) Math.min(99, (downloaded * 100L) / total);
                            if (pct >= lastPct + 10) {
                                lastPct = pct;
                                notifyJs(reciter, surahNum, pct);
                            }
                        }
                    }
                }
                conn.disconnect();
                return tmp.renameTo(out);
            } catch (Exception e) {
                return false;
            }
        }

        private void notifyJs(String reciter, int surahNum, int pct) {
            runOnUiThread(() -> {
                if (webView != null) {
                    webView.evaluateJavascript(
                        "window.onNativeDownloadUpdate && window.onNativeDownloadUpdate('" + reciter + "'," + surahNum + "," + pct + ");",
                        null
                    );
                }
            });
        }
    }
}
