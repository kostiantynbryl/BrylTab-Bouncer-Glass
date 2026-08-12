package com.bryltab.bouncerglass;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Targeted module for the supplied DOOGEE U10 Android 16 SystemUI.apk.
 *
 * Verified target classes in supplied APK:
 *   ScrimState$3  = BOUNCER
 *   ScrimState$4  = BOUNCER_SCRIMMED
 *   LockscreenToPrimaryBouncerTransitionViewModel$$ExternalSyntheticLambda0
 *      classId 0/1 participates in the lockscreen -> primary bouncer alpha transition.
 *
 * No authentication, GateKeeper, Keyguard PIN validation or credential storage code is touched.
 */
public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "BrylTabBouncerGlass";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final float BOUNCER_SCRIM_ALPHA = 0.10f; // 10% opaque = 90% transparent

    private static final AtomicBoolean SCRIM_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean ALPHA_LOGGED = new AtomicBoolean(false);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SYSTEM_UI.equals(lpparam.packageName)) {
            return;
        }

        try {
            hookBouncerScrim(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": scrim hook failed: " + t);
        }

        try {
            hookLockscreenFade(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": lockscreen alpha hook failed: " + t);
        }
    }

    private static void hookBouncerScrim(ClassLoader classLoader) {
        hookScrimState(
                "com.android.systemui.statusbar.phone.ScrimState$3",
                classLoader,
                false
        );
        hookScrimState(
                "com.android.systemui.statusbar.phone.ScrimState$4",
                classLoader,
                true
        );
    }

    private static void hookScrimState(String className, ClassLoader classLoader, boolean scrimmed) {
        final Class<?> stateClass = XposedHelpers.findClass(className, classLoader);

        XposedBridge.hookAllMethods(stateClass, "prepare", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    // Normal BOUNCER darkens the layer behind the bouncer.
                    // BOUNCER_SCRIMMED uses the front scrim instead.
                    if (scrimmed) {
                        XposedHelpers.setFloatField(param.thisObject, "mBehindAlpha", 0.0f);
                        XposedHelpers.setFloatField(param.thisObject, "mFrontAlpha", BOUNCER_SCRIM_ALPHA);
                    } else {
                        XposedHelpers.setFloatField(param.thisObject, "mBehindAlpha", BOUNCER_SCRIM_ALPHA);
                        XposedHelpers.setFloatField(param.thisObject, "mFrontAlpha", 0.0f);
                    }

                    if (SCRIM_LOGGED.compareAndSet(false, true)) {
                        XposedBridge.log(TAG + ": bouncer scrim active, alpha=" + BOUNCER_SCRIM_ALPHA);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": unable to apply scrim alpha in " + className + ": " + t);
                }
            }
        });
    }

    private static void hookLockscreenFade(ClassLoader classLoader) {
        final String lambdaName =
                "com.android.systemui.keyguard.ui.viewmodel." +
                "LockscreenToPrimaryBouncerTransitionViewModel$$ExternalSyntheticLambda0";

        final Class<?> lambdaClass = XposedHelpers.findClass(lambdaName, classLoader);

        XposedBridge.hookAllMethods(lambdaClass, "invoke", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    final int classId = XposedHelpers.getIntField(param.thisObject, "$r8$classId");
                    if (classId != 0 && classId != 1) {
                        return;
                    }

                    // In the supplied SystemUI both branches return boxed Float values.
                    // Force alpha to 1.0 during LOCKSCREEN -> PRIMARY_BOUNCER.
                    final Object result = param.getResult();
                    if (result instanceof Float) {
                        param.setResult(Float.valueOf(1.0f));

                        if (ALPHA_LOGGED.compareAndSet(false, true)) {
                            XposedBridge.log(TAG + ": lockscreen content alpha pinned to 1.0");
                        }
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": alpha lambda hook error: " + t);
                }
            }
        });
    }
}
