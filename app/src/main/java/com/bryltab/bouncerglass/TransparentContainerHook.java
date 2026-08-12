package com.bryltab.bouncerglass;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * v0.1.2 target-specific fix for the supplied DOOGEE U10 Android 16 SystemUI.apk.
 *
 * The target KeyguardSecurityContainer contains mTransparentModeEnabled and
 * reloadBackgroundColor(). Its bytecode selects a transparent background only
 * when mTransparentModeEnabled is true; otherwise it applies an opaque system
 * color. v0.1.1 already proved that the ScrimView is transparent, so this is
 * the remaining full-screen fill layer.
 */
public final class TransparentContainerHook implements IXposedHookLoadPackage {
    private static final String TAG = "BrylTabBouncerGlass";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final String CONTAINER = "com.android.keyguard.KeyguardSecurityContainer";
    private static final AtomicBoolean LOGGED = new AtomicBoolean(false);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SYSTEM_UI.equals(lpparam.packageName)) {
            return;
        }

        try {
            final Class<?> containerClass = XposedHelpers.findClass(CONTAINER, lpparam.classLoader);

            // Critical hook: reloadBackgroundColor() checks mTransparentModeEnabled
            // before choosing transparent vs opaque background.
            XposedBridge.hookAllMethods(containerClass, "reloadBackgroundColor", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    forceTransparent(param.thisObject);
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    forceTransparent(param.thisObject);
                    logOnce();
                }
            });

            // Ensure the first inflated bouncer is transparent even before a later
            // theme/configuration reload invokes reloadBackgroundColor().
            XposedBridge.hookAllMethods(containerClass, "onFinishInflate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    forceTransparent(param.thisObject);
                    logOnce();
                }
            });

            XposedBridge.log(TAG + ": v0.1.2 KeyguardSecurityContainer hook registered");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": v0.1.2 container hook failed: " + t);
        }
    }

    private static void forceTransparent(Object container) {
        if (container == null) {
            return;
        }
        try {
            XposedHelpers.setBooleanField(container, "mTransparentModeEnabled", true);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": mTransparentModeEnabled write failed: " + t);
        }

        try {
            XposedHelpers.callMethod(container, "setBackgroundColor", 0x00000000);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": KeyguardSecurityContainer background clear failed: " + t);
        }
    }

    private static void logOnce() {
        if (LOGGED.compareAndSet(false, true)) {
            XposedBridge.log(TAG + ": v0.1.2 KeyguardSecurityContainer forced transparent");
        }
    }
}
