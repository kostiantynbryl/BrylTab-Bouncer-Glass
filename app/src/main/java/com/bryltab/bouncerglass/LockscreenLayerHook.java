package com.bryltab.bouncerglass;

import android.app.WallpaperManager;
import android.graphics.drawable.Drawable;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * v0.1.3: preserve the actual lockscreen visual layer while PRIMARY_BOUNCER is active.
 *
 * The supplied Android 16 SystemUI has a final KeyguardRootViewBinder collector which calls
 * View.setAlpha() after the LOCKSCREEN -> PRIMARY_BOUNCER transition flow. That final collector
 * can still make the lockscreen root invisible even when an earlier transition flow is pinned.
 *
 * The transparent bouncer also exposes the home wallpaper window on this target build. While the
 * bouncer is resumed, this hook temporarily uses WallpaperManager.FLAG_LOCK as the background of
 * the keyguard root. The original background is restored when the bouncer pauses.
 */
public final class LockscreenLayerHook implements IXposedHookLoadPackage {
    private static final String TAG = "BrylTabBouncerGlass";
    private static final String SYSTEM_UI = "com.android.systemui";

    private static volatile boolean bouncerActive = false;
    private static WeakReference<ViewGroup> keyguardRootRef = new WeakReference<>(null);
    private static Drawable originalRootBackground;
    private static ViewGroup originalRoot;
    private static Drawable lockWallpaperDrawable;

    private static final AtomicBoolean ROOT_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean ALPHA_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean WALLPAPER_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean RESUME_LOGGED = new AtomicBoolean(false);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SYSTEM_UI.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + ": loading v0.1.3 lockscreen-layer hook");

        try {
            hookKeyguardRootBinder(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": v0.1.3 root binder hook failed: " + t);
        }

        try {
            hookFinalRootAlpha(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": v0.1.3 final root alpha hook failed: " + t);
        }

        try {
            hookBouncerLifecycle(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": v0.1.3 bouncer lifecycle hook failed: " + t);
        }
    }

    private static void hookKeyguardRootBinder(ClassLoader classLoader) {
        final Class<?> binder = XposedHelpers.findClass(
                "com.android.systemui.keyguard.ui.binder.KeyguardRootViewBinder",
                classLoader
        );

        XposedBridge.hookAllMethods(binder, "bind", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0
                        || !(param.args[0] instanceof ViewGroup)) {
                    return;
                }

                final ViewGroup root = (ViewGroup) param.args[0];
                captureRoot(root);

                if (ROOT_LOGGED.compareAndSet(false, true)) {
                    XposedBridge.log(TAG + ": v0.1.3 keyguard root captured: "
                            + root.getClass().getName());
                }

                if (bouncerActive) {
                    forceRootForBouncer(root);
                }
            }
        });
    }

    /**
     * Exact target collector found in the supplied SystemUI.dex. Its emit() contains the final
     * android.view.View.setAlpha() call for the bound keyguard root. We only override boxed Float
     * emissions while the bouncer is active; other synthetic branches/types are left untouched.
     */
    private static void hookFinalRootAlpha(ClassLoader classLoader) {
        final Class<?> collector = XposedHelpers.findClass(
                "com.android.systemui.keyguard.ui.binder."
                        + "KeyguardRootViewBinder$bind$2$1$1$1",
                classLoader
        );

        XposedBridge.hookAllMethods(collector, "emit", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!bouncerActive || param.args == null || param.args.length == 0
                        || !(param.args[0] instanceof Float)) {
                    return;
                }

                param.args[0] = Float.valueOf(1.0f);
                final ViewGroup root = keyguardRootRef.get();
                if (root != null) {
                    forceRootForBouncer(root);
                }

                if (ALPHA_LOGGED.compareAndSet(false, true)) {
                    XposedBridge.log(TAG + ": v0.1.3 final KeyguardRootView alpha pinned to 1.0");
                }
            }
        });
    }

    private static void hookBouncerLifecycle(ClassLoader classLoader) {
        final Class<?> controller = XposedHelpers.findClass(
                "com.android.keyguard.KeyguardSecurityContainerController",
                classLoader
        );

        XposedBridge.hookAllMethods(controller, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                bouncerActive = true;
                final ViewGroup root = keyguardRootRef.get();
                if (root != null) {
                    forceRootForBouncer(root);
                }
                if (RESUME_LOGGED.compareAndSet(false, true)) {
                    XposedBridge.log(TAG + ": v0.1.3 PRIMARY_BOUNCER active");
                }
            }
        });

        XposedBridge.hookAllMethods(controller, "onPause", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                bouncerActive = false;
                restoreRootBackground();
                XposedBridge.log(TAG + ": v0.1.3 PRIMARY_BOUNCER inactive; root background restored");
            }
        });
    }

    private static void captureRoot(ViewGroup root) {
        if (root == null) {
            return;
        }
        if (originalRoot != root) {
            originalRoot = root;
            originalRootBackground = root.getBackground();
            lockWallpaperDrawable = null;
        }
        keyguardRootRef = new WeakReference<>(root);
    }

    private static void forceRootForBouncer(ViewGroup root) {
        if (root == null) {
            return;
        }
        root.setAlpha(1.0f);
        applyLockWallpaper(root);
    }

    private static void applyLockWallpaper(ViewGroup root) {
        try {
            if (lockWallpaperDrawable == null) {
                final WallpaperManager manager = WallpaperManager.getInstance(root.getContext());
                lockWallpaperDrawable = manager.getDrawable(WallpaperManager.FLAG_LOCK);
            }

            if (lockWallpaperDrawable != null && root.getBackground() != lockWallpaperDrawable) {
                root.setBackground(lockWallpaperDrawable);
                if (WALLPAPER_LOGGED.compareAndSet(false, true)) {
                    XposedBridge.log(TAG + ": v0.1.3 FLAG_LOCK wallpaper applied to keyguard root");
                }
            } else if (lockWallpaperDrawable == null
                    && WALLPAPER_LOGGED.compareAndSet(false, true)) {
                XposedBridge.log(TAG + ": v0.1.3 FLAG_LOCK wallpaper returned null");
            }
        } catch (Throwable t) {
            if (WALLPAPER_LOGGED.compareAndSet(false, true)) {
                XposedBridge.log(TAG + ": v0.1.3 FLAG_LOCK wallpaper load failed: " + t);
            }
        }
    }

    private static void restoreRootBackground() {
        final ViewGroup root = keyguardRootRef.get();
        if (root == null || root != originalRoot) {
            return;
        }
        try {
            root.setBackground(originalRootBackground);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": v0.1.3 root background restore failed: " + t);
        }
    }
}
