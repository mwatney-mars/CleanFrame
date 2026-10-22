package com.cleanframe.tv;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends Activity {

    private WebView webView;

    private static final Set<String> BUNDLED_SVG_PACKAGES = new HashSet<>(Arrays.asList(
            "com.stremio.one",
            "com.netflix.ninja",
            "com.disney.disneyplus",
            "com.google.android.youtube.tv",
            "com.amazon.amazonvideo.livingroom",
            "com.apple.atve.androidtv.appletv",
            "com.plexapp.android",
            "com.google.android.youtube.tvmusic",
            "com.esaba.downloader",
            "com.spotify.tv.android",
            "com.spotify.music",
            "com.wbd.stream",
            "com.hbo.hbonow",
            "com.tubitv",
            "com.apple.android.music"
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );
        hideSystemUI();

        webView = new WebView(this);
        webView.setBackgroundColor(Color.parseColor("#0f1416"));
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        webView.setInitialScale(100);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setAllowFileAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new AndroidBridge(this), "AndroidBridge");
        webView.setWebViewClient(new WebViewClient());

        setContentView(webView);
        webView.loadUrl("file:///android_asset/app/index.html");
        webView.requestFocus();
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (webView != null) {
            webView.onResume();
            webView.resumeTimers();
        }
    }

    @Override
    protected void onStop() {
        if (webView != null) {
            webView.onPause();
            webView.pauseTimers();
        }
        super.onStop();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int jsKeyCode = mapKeyCodeToJs(keyCode);

        if (jsKeyCode != 0 && webView != null) {
            int action = event.getAction();
            if (action == KeyEvent.ACTION_DOWN) {
                boolean repeat = event.getRepeatCount() > 0;
                dispatchJsKey("keydown", jsKeyCode, repeat);
                return true;
            } else if (action == KeyEvent.ACTION_UP) {
                dispatchJsKey("keyup", jsKeyCode, false);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private int mapKeyCodeToJs(int androidKeyCode) {
        switch (androidKeyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return 37;
            case KeyEvent.KEYCODE_DPAD_UP:
                return 38;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return 39;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return 40;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                return 13;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
            case KeyEvent.KEYCODE_BUTTON_B:
                return 27;
            default:
                return 0;
        }
    }

    private void dispatchJsKey(String type, int jsKeyCode, boolean repeat) {
        String script = "(function(){"
                + "var e = new KeyboardEvent('" + type + "', { bubbles: true, cancelable: true, repeat: " + repeat + " });"
                + "Object.defineProperty(e, 'keyCode', { get: function() { return " + jsKeyCode + "; } });"
                + "Object.defineProperty(e, 'which', { get: function() { return " + jsKeyCode + "; } });"
                + "document.dispatchEvent(e);"
                + "})();";
        webView.evaluateJavascript(script, null);
    }

    @Override
    public void onBackPressed() {
        if (webView != null) {
            dispatchJsKey("keydown", 27, false);
            dispatchJsKey("keyup", 27, false);
        }
    }

    public static class AndroidBridge {

        private final MainActivity activity;

        public AndroidBridge(MainActivity activity) {
            this.activity = activity;
        }

        @JavascriptInterface
        public boolean launchApp(String packageName) {
            if (packageName == null || packageName.isEmpty()) {
                return false;
            }
            try {
                PackageManager pm = activity.getPackageManager();
                Intent intent = pm.getLeanbackLaunchIntentForPackage(packageName);
                if (intent == null) {
                    intent = pm.getLaunchIntentForPackage(packageName);
                }
                if (intent == null) {
                    Intent query = new Intent(Intent.ACTION_MAIN);
                    query.setPackage(packageName);
                    List<ResolveInfo> list = pm.queryIntentActivities(query, 0);
                    if (list != null && !list.isEmpty()) {
                        ActivityInfo ai = list.get(0).activityInfo;
                        intent = new Intent(Intent.ACTION_MAIN);
                        intent.setComponent(new ComponentName(ai.packageName, ai.name));
                    }
                }
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                    return true;
                }
            } catch (Exception ignored) {
            }
            return false;
        }

        @JavascriptInterface
        public boolean openGoogleTvHome() {
            HomeAccessibilityService.allowStockHomeTemporarily();
            try {
                PackageManager pm = activity.getPackageManager();
                Intent intent = pm.getLeanbackLaunchIntentForPackage("com.google.android.apps.tv.launcherx");
                if (intent == null) {
                    intent = pm.getLaunchIntentForPackage("com.google.android.apps.tv.launcherx");
                }
                if (intent == null) {
                    intent = pm.getLeanbackLaunchIntentForPackage("com.google.android.tvlauncher");
                }
                if (intent == null) {
                    intent = pm.getLaunchIntentForPackage("com.google.android.tvlauncher");
                }
                if (intent == null) {
                    intent = new Intent(Intent.ACTION_MAIN);
                    intent.addCategory(Intent.CATEGORY_HOME);
                    intent.setPackage("com.google.android.apps.tv.launcherx");
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                return true;
            } catch (Exception e) {
                try {
                    Intent fallback = new Intent(Intent.ACTION_MAIN);
                    fallback.addCategory(Intent.CATEGORY_HOME);
                    fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(fallback);
                    return true;
                } catch (Exception ignored) {
                }
            }
            return false;
        }

        @JavascriptInterface
        public boolean openTvSettings() {
            try {
                Intent intent = new Intent(Settings.ACTION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                return true;
            } catch (Exception ignored) {
            }
            return false;
        }

        @JavascriptInterface
        public String getInstalledApps() {
            JSONArray array = new JSONArray();
            try {
                PackageManager pm = activity.getPackageManager();
                Set<String> seen = new HashSet<>();
                String selfPkg = activity.getPackageName();

                String[] categories = new String[]{
                        Intent.CATEGORY_LEANBACK_LAUNCHER,
                        Intent.CATEGORY_LAUNCHER
                };

                for (String category : categories) {
                    Intent query = new Intent(Intent.ACTION_MAIN);
                    query.addCategory(category);
                    List<ResolveInfo> resolved = pm.queryIntentActivities(query, 0);
                    if (resolved == null) continue;

                    for (ResolveInfo info : resolved) {
                        if (info.activityInfo == null) continue;
                        String pkg = info.activityInfo.packageName;
                        if (pkg == null || pkg.equals(selfPkg) || seen.contains(pkg)) continue;
                        seen.add(pkg);

                        CharSequence labelSeq = info.loadLabel(pm);
                        String label = (labelSeq != null && labelSeq.length() > 0)
                                ? labelSeq.toString().trim()
                                : pkg;

                        JSONObject obj = new JSONObject();
                        obj.put("appId", pkg);
                        obj.put("name", label);
                        if (!BUNDLED_SVG_PACKAGES.contains(pkg)) {
                            String iconData = loadIconDataUri(info, pm);
                            if (!iconData.isEmpty()) {
                                obj.put("icon", iconData);
                            }
                        }
                        array.put(obj);
                    }
                }
            } catch (Exception ignored) {
            }
            return array.toString();
        }

        private String loadIconDataUri(ResolveInfo info, PackageManager pm) {
            try {
                Drawable d = info.activityInfo.loadIcon(pm);
                if (d == null) return "";
                int size = 88;
                Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bmp);
                d.setBounds(0, 0, size, size);
                d.draw(canvas);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.PNG, 90, out);
                bmp.recycle();
                byte[] bytes = out.toByteArray();
                if (bytes.length > 20000) return "";
                return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
            } catch (Exception e) {
                return "";
            }
        }
    }
}
