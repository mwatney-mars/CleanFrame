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
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
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
                        String iconData = loadIconDataUri(info, pm);
                        if (!iconData.isEmpty()) {
                            obj.put("icon", iconData);
                        }
                        MonoResult mono = loadMonoIcon(info, pm, pkg);
                        if (mono != null && !mono.dataUri.isEmpty()) {
                            obj.put("monoIcon", mono.dataUri);
                            obj.put("monoW", mono.width);
                            obj.put("monoH", mono.height);
                            obj.put("monoFill", Math.round(mono.fill * 1000.0) / 1000.0);
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

        private static class MonoResult {
            final String dataUri;
            final int width;
            final int height;
            final double fill;

            MonoResult(String dataUri, int width, int height, double fill) {
                this.dataUri = dataUri;
                this.width = width;
                this.height = height;
                this.fill = fill;
            }
        }

        private MonoResult loadMonoIcon(ResolveInfo info, PackageManager pm, String pkg) {
            try {
                Drawable actIcon = info.activityInfo.loadIcon(pm);
                Drawable appIcon = null;
                try {
                    appIcon = pm.getApplicationIcon(pkg);
                } catch (Exception ignored) {
                }

                Drawable[] adaptiveCandidates = new Drawable[]{actIcon, appIcon};
                if (Build.VERSION.SDK_INT >= 26) {
                    for (Drawable cand : adaptiveCandidates) {
                        if (cand instanceof AdaptiveIconDrawable) {
                            AdaptiveIconDrawable aid = (AdaptiveIconDrawable) cand;
                            if (Build.VERSION.SDK_INT >= 33) {
                                Drawable monoLayer = aid.getMonochrome();
                                if (monoLayer != null) {
                                    MonoResult r = renderAndExtract(monoLayer, -22, 128, true, pkg + ":mono");
                                    if (r != null) return r;
                                }
                            }
                            Drawable fgLayer = aid.getForeground();
                            if (fgLayer != null) {
                                MonoResult r = renderAndExtract(fgLayer, -24, 128, false, pkg + ":fg");
                                if (r != null) return r;
                            }
                        }
                    }
                }

                for (Drawable cand : adaptiveCandidates) {
                    if (cand != null) {
                        MonoResult r = renderAndExtract(cand, 0, 128, false, pkg + ":full");
                        if (r != null) return r;
                    }
                }
            } catch (Exception ignored) {
            }
            return null;
        }

        private MonoResult renderAndExtract(Drawable d, int inset, int size, boolean isMonoLayer, String tag) {
            Bitmap bmp = null;
            try {
                bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bmp);
                d.setBounds(inset, inset, size - inset, size - inset);
                d.draw(canvas);
                return extractFromBitmap(bmp, isMonoLayer, tag);
            } catch (Exception e) {
                return null;
            } finally {
                if (bmp != null) {
                    bmp.recycle();
                }
            }
        }

        private MonoResult extractFromBitmap(Bitmap bmp, boolean isMonoLayer, String tag) {
            final int N = bmp.getWidth();
            int[] px = new int[N * N];
            bmp.getPixels(px, 0, N, 0, 0, N, N);

            int[] alpha = new int[N * N];
            for (int i = 0; i < px.length; i++) {
                alpha[i] = (px[i] >>> 24) & 0xFF;
            }

            boolean strippedAny = false;
            if (!isMonoLayer) {
                for (int pass = 0; pass < 2; pass++) {
                    int minX = N, maxX = -1, minY = N, maxY = -1, vis = 0;
                    for (int y = 0; y < N; y++) {
                        int row = y * N;
                        for (int x = 0; x < N; x++) {
                            if (alpha[row + x] >= 32) {
                                if (x < minX) minX = x;
                                if (x > maxX) maxX = x;
                                if (y < minY) minY = y;
                                if (y > maxY) maxY = y;
                                vis++;
                            }
                        }
                    }
                    if (vis < 40) break;
                    int bboxW = maxX - minX + 1;
                    int bboxH = maxY - minY + 1;
                    if (bboxW < 10 || bboxH < 10) break;
                    int bboxArea = bboxW * bboxH;
                    float bboxFill = (float) vis / (float) bboxArea;

                    int[] dist = computeExteriorDistance(alpha, N);

                    if (bboxFill < 0.64f) {
                        break;
                    }

                    int midX = (minX + maxX) / 2;
                    int midY = (minY + maxY) / 2;
                    int[][] qR = new int[4][512];
                    int[][] qG = new int[4][512];
                    int[][] qB = new int[4][512];
                    int[] qCount = new int[4];
                    int totalRing = 0;

                    for (int y = minY; y <= maxY; y++) {
                        int row = y * N;
                        for (int x = minX; x <= maxX; x++) {
                            int idx = row + x;
                            int d = dist[idx];
                            if (d >= 2 && d <= 4 && alpha[idx] >= 128) {
                                int q = (y <= midY ? 0 : 2) + (x <= midX ? 0 : 1);
                                int c = px[idx];
                                int cnt = qCount[q];
                                if (cnt < 512) {
                                    qR[q][cnt] = (c >> 16) & 0xFF;
                                    qG[q][cnt] = (c >> 8) & 0xFF;
                                    qB[q][cnt] = c & 0xFF;
                                    qCount[q] = cnt + 1;
                                }
                                totalRing++;
                            }
                        }
                    }

                    if (totalRing < 24) break;

                    float[] medR = new float[4];
                    float[] medG = new float[4];
                    float[] medB = new float[4];
                    for (int q = 0; q < 4; q++) {
                        if (qCount[q] >= 4) {
                            medR[q] = median(qR[q], qCount[q]);
                            medG[q] = median(qG[q], qCount[q]);
                            medB[q] = median(qB[q], qCount[q]);
                        } else {
                            medR[q] = -1;
                        }
                    }
                    float fallbackR = 0, fallbackG = 0, fallbackB = 0;
                    int validQ = 0;
                    for (int q = 0; q < 4; q++) {
                        if (medR[q] >= 0) {
                            fallbackR += medR[q];
                            fallbackG += medG[q];
                            fallbackB += medB[q];
                            validQ++;
                        }
                    }
                    if (validQ == 0) break;
                    fallbackR /= validQ;
                    fallbackG /= validQ;
                    fallbackB /= validQ;
                    for (int q = 0; q < 4; q++) {
                        if (medR[q] < 0) {
                            medR[q] = fallbackR;
                            medG[q] = fallbackG;
                            medB[q] = fallbackB;
                        }
                    }

                    float maxQDiff = 0;
                    for (int a = 0; a < 4; a++) {
                        for (int b = a + 1; b < 4; b++) {
                            float diff = colorDist(medR[a], medG[a], medB[a], medR[b], medG[b], medB[b]);
                            if (diff > maxQDiff) maxQDiff = diff;
                        }
                    }
                    if (maxQDiff > 165f) {
                        if (pass == 0 && bboxFill >= 0.78f) return null;
                        break;
                    }

                    int matchRing = 0;
                    for (int y = minY; y <= maxY; y++) {
                        float v = (float) (y - minY) / Math.max(1, bboxH - 1);
                        int row = y * N;
                        for (int x = minX; x <= maxX; x++) {
                            int idx = row + x;
                            int d = dist[idx];
                            if (d >= 2 && d <= 4 && alpha[idx] >= 128) {
                                float u = (float) (x - minX) / Math.max(1, bboxW - 1);
                                float bgR = bilerp(medR, u, v);
                                float bgG = bilerp(medG, u, v);
                                float bgB = bilerp(medB, u, v);
                                int c = px[idx];
                                float cd = colorDist((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, bgR, bgG, bgB);
                                if (cd <= 48f) matchRing++;
                            }
                        }
                    }

                    float ringMatchRatio = (float) matchRing / (float) totalRing;
                    if (ringMatchRatio < 0.62f) {
                        if (pass == 0 && bboxFill >= 0.78f) return null;
                        break;
                    }

                    int[] interiorDists = new int[bboxArea];
                    int interiorCount = 0;
                    int contrastCount = 0;
                    for (int y = minY; y <= maxY; y++) {
                        float v = (float) (y - minY) / Math.max(1, bboxH - 1);
                        int row = y * N;
                        for (int x = minX; x <= maxX; x++) {
                            int idx = row + x;
                            if (dist[idx] >= 5 && alpha[idx] >= 128) {
                                float u = (float) (x - minX) / Math.max(1, bboxW - 1);
                                float bgR = bilerp(medR, u, v);
                                float bgG = bilerp(medG, u, v);
                                float bgB = bilerp(medB, u, v);
                                int c = px[idx];
                                int cd = Math.round(colorDist((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, bgR, bgG, bgB));
                                interiorDists[interiorCount++] = cd;
                                if (cd >= 52) contrastCount++;
                            }
                        }
                    }

                    if (interiorCount < 16) break;
                    Arrays.sort(interiorDists, 0, interiorCount);
                    float maxDist = interiorDists[Math.min(interiorCount - 1, Math.round(interiorCount * 0.96f))];
                    float contrastRatio = (float) contrastCount / (float) bboxArea;

                    if (maxDist < 55f || contrastRatio < 0.015f) {
                        if (pass == 0 && bboxFill >= 0.76f) return null;
                        break;
                    }
                    if (contrastRatio > 0.74f) {
                        if (pass == 0) return null;
                        break;
                    }

                    float tLow = Math.max(30f, maxDist * 0.20f);
                    float tHigh = Math.max(58f, maxDist * 0.54f);
                    for (int y = 0; y < N; y++) {
                        float v = clamp01((float) (y - minY) / Math.max(1, bboxH - 1));
                        int row = y * N;
                        for (int x = 0; x < N; x++) {
                            int idx = row + x;
                            int d = dist[idx];
                            if (d <= 3 || alpha[idx] < 24) {
                                alpha[idx] = 0;
                            } else {
                                float u = clamp01((float) (x - minX) / Math.max(1, bboxW - 1));
                                float bgR = bilerp(medR, u, v);
                                float bgG = bilerp(medG, u, v);
                                float bgB = bilerp(medB, u, v);
                                int c = px[idx];
                                float cd = colorDist((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, bgR, bgG, bgB);
                                if (cd <= tLow) {
                                    alpha[idx] = 0;
                                } else if (cd >= tHigh) {
                                    alpha[idx] = d == 4 ? (alpha[idx] * 170) / 255 : alpha[idx];
                                } else {
                                    int ramp = Math.round(255f * (cd - tLow) / (tHigh - tLow));
                                    if (d == 4) ramp = (ramp * 170) / 255;
                                    alpha[idx] = (alpha[idx] * ramp) / 255;
                                }
                            }
                        }
                    }
                    despeckle(alpha, N);
                    strippedAny = true;
                }

                if (!strippedAny) {
                    punchInternalCutouts(px, alpha, N);
                }
            }

            int minX = N, maxX = -1, minY = N, maxY = -1;
            long sumAlpha = 0;
            for (int y = 0; y < N; y++) {
                int row = y * N;
                for (int x = 0; x < N; x++) {
                    int a = alpha[row + x];
                    if (a >= 20) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                        sumAlpha += a;
                    }
                }
            }

            if (maxX < minX || maxY < minY) return null;
            int cropW = maxX - minX + 1;
            int cropH = maxY - minY + 1;
            if (cropW < 8 || cropH < 8) return null;

            double fill = (double) sumAlpha / (double) (cropW * cropH * 255.0);
            if (fill < 0.05 || fill > (strippedAny ? 0.94 : 0.88)) {
                return null;
            }

            int[] outPx = new int[cropW * cropH];
            for (int y = 0; y < cropH; y++) {
                int srcRow = (minY + y) * N;
                int dstRow = y * cropW;
                for (int x = 0; x < cropW; x++) {
                    int a = alpha[srcRow + minX + x];
                    outPx[dstRow + x] = (a << 24) | 0x00FFFFFF;
                }
            }

            Bitmap outBmp = Bitmap.createBitmap(outPx, cropW, cropH, Bitmap.Config.ARGB_8888);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            outBmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            outBmp.recycle();
            byte[] bytes = out.toByteArray();
            if (bytes.length > 20000) return null;

            String uri = "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP);
            return new MonoResult(uri, cropW, cropH, clampFill(fill));
        }

        private static int[] computeExteriorDistance(int[] alpha, int N) {
            int[] dist = new int[N * N];
            for (int y = 0; y < N; y++) {
                int row = y * N;
                for (int x = 0; x < N; x++) {
                    if (alpha[row + x] < 64 || x == 0 || x == N - 1 || y == 0 || y == N - 1) {
                        dist[row + x] = 0;
                    } else {
                        dist[row + x] = 255;
                    }
                }
            }
            for (int y = 1; y < N - 1; y++) {
                int row = y * N;
                for (int x = 1; x < N - 1; x++) {
                    int idx = row + x;
                    if (dist[idx] > 0) {
                        int m = Math.min(dist[idx - N], dist[idx - 1]) + 1;
                        if (m < dist[idx]) dist[idx] = m;
                    }
                }
            }
            for (int y = N - 2; y >= 1; y--) {
                int row = y * N;
                for (int x = N - 2; x >= 1; x--) {
                    int idx = row + x;
                    if (dist[idx] > 0) {
                        int m = Math.min(dist[idx + N], dist[idx + 1]) + 1;
                        if (m < dist[idx]) dist[idx] = m;
                    }
                }
            }
            return dist;
        }

        private static void despeckle(int[] alpha, int N) {
            int[] copy = Arrays.copyOf(alpha, alpha.length);
            for (int y = 1; y < N - 1; y++) {
                int row = y * N;
                for (int x = 1; x < N - 1; x++) {
                    int idx = row + x;
                    if (copy[idx] > 0) {
                        int n = 0;
                        if (copy[idx - N - 1] >= 32) n++;
                        if (copy[idx - N] >= 32) n++;
                        if (copy[idx - N + 1] >= 32) n++;
                        if (copy[idx - 1] >= 32) n++;
                        if (copy[idx + 1] >= 32) n++;
                        if (copy[idx + N - 1] >= 32) n++;
                        if (copy[idx + N] >= 32) n++;
                        if (copy[idx + N + 1] >= 32) n++;
                        if (n <= 1) alpha[idx] = 0;
                    }
                }
            }
        }

        private static void punchInternalCutouts(int[] px, int[] alpha, int N) {
            int[] dist = computeExteriorDistance(alpha, N);
            int minX = N, maxX = -1, minY = N, maxY = -1, opaque = 0;
            for (int y = 0; y < N; y++) {
                int row = y * N;
                for (int x = 0; x < N; x++) {
                    if (alpha[row + x] >= 128) {
                        if (x < minX) minX = x;
                        if (x > maxX) maxX = x;
                        if (y < minY) minY = y;
                        if (y > maxY) maxY = y;
                        opaque++;
                    }
                }
            }
            if (opaque < 60 || maxX <= minX || maxY <= minY) return;
            int bboxW = maxX - minX + 1;
            int bboxH = maxY - minY + 1;
            int midX = (minX + maxX) / 2;
            int midY = (minY + maxY) / 2;

            int[][] qR = new int[4][512];
            int[][] qG = new int[4][512];
            int[][] qB = new int[4][512];
            int[] qCount = new int[4];
            int ringCount = 0;

            for (int y = minY; y <= maxY; y++) {
                int row = y * N;
                for (int x = minX; x <= maxX; x++) {
                    int idx = row + x;
                    int d = dist[idx];
                    if (d >= 2 && d <= 4 && alpha[idx] >= 128) {
                        int q = (y <= midY ? 0 : 2) + (x <= midX ? 0 : 1);
                        int c = px[idx];
                        int cnt = qCount[q];
                        if (cnt < 512) {
                            qR[q][cnt] = (c >> 16) & 0xFF;
                            qG[q][cnt] = (c >> 8) & 0xFF;
                            qB[q][cnt] = c & 0xFF;
                            qCount[q] = cnt + 1;
                        }
                        ringCount++;
                    }
                }
            }
            if (ringCount < 24) return;

            float[] medR = new float[4];
            float[] medG = new float[4];
            float[] medB = new float[4];
            float fallbackR = 0, fallbackG = 0, fallbackB = 0;
            int validQ = 0;
            for (int q = 0; q < 4; q++) {
                if (qCount[q] >= 4) {
                    medR[q] = median(qR[q], qCount[q]);
                    medG[q] = median(qG[q], qCount[q]);
                    medB[q] = median(qB[q], qCount[q]);
                    fallbackR += medR[q];
                    fallbackG += medG[q];
                    fallbackB += medB[q];
                    validQ++;
                } else {
                    medR[q] = -1;
                }
            }
            if (validQ == 0) return;
            fallbackR /= validQ;
            fallbackG /= validQ;
            fallbackB /= validQ;
            for (int q = 0; q < 4; q++) {
                if (medR[q] < 0) {
                    medR[q] = fallbackR;
                    medG[q] = fallbackG;
                    medB[q] = fallbackB;
                }
            }

            float maxQDiff = 0;
            for (int a = 0; a < 4; a++) {
                for (int b = a + 1; b < 4; b++) {
                    float diff = colorDist(medR[a], medG[a], medB[a], medR[b], medG[b], medB[b]);
                    if (diff > maxQDiff) maxQDiff = diff;
                }
            }
            if (maxQDiff > 140f) return;

            int matchRing = 0;
            int contrastInterior = 0;
            for (int y = minY; y <= maxY; y++) {
                float v = (float) (y - minY) / Math.max(1, bboxH - 1);
                int row = y * N;
                for (int x = minX; x <= maxX; x++) {
                    int idx = row + x;
                    if (alpha[idx] >= 128) {
                        int d = dist[idx];
                        float u = (float) (x - minX) / Math.max(1, bboxW - 1);
                        float bgR = bilerp(medR, u, v);
                        float bgG = bilerp(medG, u, v);
                        float bgB = bilerp(medB, u, v);
                        int c = px[idx];
                        float cd = colorDist((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, bgR, bgG, bgB);
                        if (d >= 2 && d <= 4 && cd <= 48f) {
                            matchRing++;
                        } else if (d >= 5 && cd >= 75f) {
                            contrastInterior++;
                        }
                    }
                }
            }

            if ((float) matchRing / (float) ringCount >= 0.78f
                    && contrastInterior >= opaque * 0.015f
                    && contrastInterior <= opaque * 0.38f) {
                for (int y = minY; y <= maxY; y++) {
                    float v = (float) (y - minY) / Math.max(1, bboxH - 1);
                    int row = y * N;
                    for (int x = minX; x <= maxX; x++) {
                        int idx = row + x;
                        if (alpha[idx] > 0 && dist[idx] >= 4) {
                            float u = (float) (x - minX) / Math.max(1, bboxW - 1);
                            float bgR = bilerp(medR, u, v);
                            float bgG = bilerp(medG, u, v);
                            float bgB = bilerp(medB, u, v);
                            int c = px[idx];
                            float cd = colorDist((c >> 16) & 0xFF, (c >> 8) & 0xFF, c & 0xFF, bgR, bgG, bgB);
                            if (cd >= 100f) {
                                alpha[idx] = 0;
                            } else if (cd > 55f) {
                                int factor = Math.round(255f * (100f - cd) / 45f);
                                alpha[idx] = (alpha[idx] * factor) / 255;
                            }
                        }
                    }
                }
            }
        }

        private static float median(int[] arr, int len) {
            Arrays.sort(arr, 0, len);
            return arr[len / 2];
        }

        private static float bilerp(float[] q, float u, float v) {
            float top = q[0] * (1f - u) + q[1] * u;
            float bot = q[2] * (1f - u) + q[3] * u;
            return top * (1f - v) + bot * v;
        }

        private static float clamp01(float v) {
            return v < 0f ? 0f : (v > 1f ? 1f : v);
        }

        private static double clampFill(double f) {
            return f < 0.15 ? 0.15 : (f > 0.85 ? 0.85 : f);
        }

        private static float colorDist(float r1, float g1, float b1, float r2, float g2, float b2) {
            float dr = r1 - r2;
            float dg = g1 - g2;
            float db = b1 - b2;
            return (float) Math.sqrt(dr * dr + dg * dg + db * db);
        }
    }
}
