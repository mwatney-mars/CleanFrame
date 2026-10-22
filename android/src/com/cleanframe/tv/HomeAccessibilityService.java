package com.cleanframe.tv;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public class HomeAccessibilityService extends AccessibilityService {

    public static volatile boolean stockHomeExplicitlyOpened = false;
    public static volatile long allowStockHomeUntil = 0L;
    private static volatile long lastLaunchMs = 0L;

    private ScreenWakeReceiver screenWakeReceiver;

    public static void allowStockHomeTemporarily() {
        stockHomeExplicitlyOpened = true;
        allowStockHomeUntil = System.currentTimeMillis() + 60000L;
    }

    public static void launchCleanFrame(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastLaunchMs < 350L) {
            return;
        }
        lastLaunchMs = now;
        try {
            Intent intent = new Intent(context, MainActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
                            | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            | Intent.FLAG_ACTIVITY_NO_ANIMATION
            );
            context.startActivity(intent);
        } catch (Exception ignored) {
        }
    }

    public static class ScreenWakeReceiver extends BroadcastReceiver {
        private final HomeAccessibilityService service;

        public ScreenWakeReceiver(HomeAccessibilityService service) {
            this.service = service;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(action)
                    || Intent.ACTION_USER_PRESENT.equals(action)
                    || Intent.ACTION_DREAMING_STOPPED.equals(action)) {
                stockHomeExplicitlyOpened = false;
                allowStockHomeUntil = 0L;
                launchCleanFrame(service);
            }
        }
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            if (info == null) {
                info = new AccessibilityServiceInfo();
            }
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
                    | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            setServiceInfo(info);
        } catch (Exception ignored) {
        }

        if (screenWakeReceiver == null) {
            screenWakeReceiver = new ScreenWakeReceiver(this);
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_USER_PRESENT);
            filter.addAction(Intent.ACTION_DREAMING_STOPPED);
            registerReceiver(screenWakeReceiver, filter);
        }
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_HOME) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                stockHomeExplicitlyOpened = false;
                allowStockHomeUntil = 0L;
                launchCleanFrame(this);
            }
            return true;
        }
        return super.onKeyEvent(event);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;
        String pkg = pkgSeq.toString();

        if ("com.google.android.apps.tv.launcherx".equals(pkg) || "com.google.android.tvlauncher".equals(pkg)) {
            if (!stockHomeExplicitlyOpened && System.currentTimeMillis() > allowStockHomeUntil) {
                launchCleanFrame(this);
            }
        } else if (!"com.cleanframe.tv".equals(pkg) && !"com.android.systemui".equals(pkg)) {
            if (System.currentTimeMillis() > allowStockHomeUntil) {
                stockHomeExplicitlyOpened = false;
            }
        }
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        if (screenWakeReceiver != null) {
            try {
                unregisterReceiver(screenWakeReceiver);
            } catch (Exception ignored) {
            }
            screenWakeReceiver = null;
        }
        super.onDestroy();
    }
}
