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
 * v0.1.1 fixes the opacity at the ScrimController render path, because this
 * SystemUI recalculates bouncer alpha after ScrimState.prepare().
 *
 * No authentication, GateKeeper, Keyguard PIN validation or credential storage code is touched.
 */
public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "BrylTabBouncerGlass";
    private static final String SYSTEM_UI = "com.android.systemui";
    private static final float BOUNCER_SCRIM_ALPHA = 0.10f; // 10% opaque = 90% transparent

    private static final AtomicBoolean STATE_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean CONTROLLER_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean RENDER_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean ALPHA_LOGGED = new AtomicBoolean(false);
    private static final AtomicBoolean LEGACY_LOGGED = new AtomicBoolean(false);

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SYSTEM_UI.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + ": loading v0.1.1 for " + lpparam.packageName);

        try {
            hookBouncerScrimState(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ScrimState hook failed: " + t);
        }

        try {
            hookScrimController(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ScrimController hook failed: " + t);
        }

        try {
            hookLockscreenFade(lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": lockscreen alpha hook failed: " + t);
        }

        try {
            hookLegacyBouncerRoot(lpparam.classLoader);
        } catch (Throwable t) {
            // The target build contains both legacy and scene/overlay bouncer paths.
            // Absence/failure of this optional fallback must never break SystemUI.
            XposedBridge.log(TAG + ": legacy bouncer fallback unavailable: " + t);
        }
    }

    /** First line of defence: keep the enum state's requested alphas correct. */
    private static void hookBouncerScrimState(ClassLoader classLoader) {
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
                    if (scrimmed) {
                        setFloatIfPresent(param.thisObject, "mBehindAlpha", 0.0f);
                        setFloatIfPresent(param.thisObject, "mFrontAlpha", BOUNCER_SCRIM_ALPHA);
                    } else {
                        setFloatIfPresent(param.thisObject, "mBehindAlpha", BOUNCER_SCRIM_ALPHA);
                        setFloatIfPresent(param.thisObject, "mFrontAlpha", 0.0f);
                    }
                    setFloatIfPresent(param.thisObject, "mNotifAlpha", 0.0f);

                    if (STATE_LOGGED.compareAndSet(false, true)) {
                        XposedBridge.log(TAG + ": ScrimState bouncer alpha prepared at "
                                + BOUNCER_SCRIM_ALPHA);
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": unable to patch " + className + ".prepare: " + t);
                }
            }
        });
    }

    /**
     * Critical v0.1.1 fix.
     *
     * This target SystemUI copies ScrimState values into ScrimController and then
     * performs additional bouncer/panel-expansion calculations in applyState$1().
     * Hook the controller as well as the final render calls so alpha cannot be
     * restored to an opaque value later in the same frame.
     */
    private static void hookScrimController(ClassLoader classLoader) {
        final Class<?> controllerClass = XposedHelpers.findClass(
                "com.android.systemui.statusbar.phone.ScrimController",
                classLoader
        );

        XposedBridge.hookAllMethods(controllerClass, "applyState$1", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    if (forceControllerAlphas(param.thisObject)
                            && CONTROLLER_LOGGED.compareAndSet(false, true)) {
                        XposedBridge.log(TAG + ": ScrimController.applyState fixed at render target");
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": applyState hook error: " + t);
                }
            }
        });

        XposedBridge.hookAllMethods(controllerClass, "updateScrims", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    forceControllerAlphas(param.thisObject);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": updateScrims pre-hook error: " + t);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    forceControllerAlphas(param.thisObject);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": updateScrims post-hook error: " + t);
                }
            }
        });

        XposedBridge.hookAllMethods(controllerClass, "setScrimAlpha", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    if (param.args == null || param.args.length < 2
                            || !(param.args[1] instanceof Float)) {
                        return;
                    }
                    capRenderedAlpha(param.thisObject, param.args[0], 1, param.args);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": setScrimAlpha hook error: " + t);
                }
            }
        });

        XposedBridge.hookAllMethods(controllerClass, "updateScrimColor", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                try {
                    // Target signature in supplied APK:
                    // updateScrimColor(View scrim, int tint, float alpha)
                    if (param.args == null || param.args.length < 3
                            || !(param.args[2] instanceof Float)) {
                        return;
                    }
                    capRenderedAlpha(param.thisObject, param.args[0], 2, param.args);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": updateScrimColor hook error: " + t);
                }
            }
        });
    }

    private static boolean forceControllerAlphas(Object controller) {
        final Object state = getFieldOrNull(controller, "mState");
        final String stateName = stateName(state);

        if ("BOUNCER".equals(stateName)) {
            setFloatIfPresent(controller, "mBehindAlpha", BOUNCER_SCRIM_ALPHA);
            setFloatIfPresent(controller, "mInFrontAlpha", 0.0f);
            setFloatIfPresent(controller, "mNotificationsAlpha", 0.0f);
            return true;
        }

        if ("BOUNCER_SCRIMMED".equals(stateName)) {
            setFloatIfPresent(controller, "mBehindAlpha", 0.0f);
            setFloatIfPresent(controller, "mInFrontAlpha", BOUNCER_SCRIM_ALPHA);
            setFloatIfPresent(controller, "mNotificationsAlpha", 0.0f);
            return true;
        }

        return false;
    }

    private static void capRenderedAlpha(
            Object controller,
            Object scrimView,
            int alphaArgIndex,
            Object[] args
    ) {
        final Object state = getFieldOrNull(controller, "mState");
        final String stateName = stateName(state);
        if (!"BOUNCER".equals(stateName) && !"BOUNCER_SCRIMMED".equals(stateName)) {
            return;
        }

        final Object behind = getFieldOrNull(controller, "mScrimBehind");
        final Object front = getFieldOrNull(controller, "mScrimInFront");
        final Object notifications = getFieldOrNull(controller, "mNotificationsScrim");

        float target = ((Float) args[alphaArgIndex]).floatValue();

        if ("BOUNCER".equals(stateName)) {
            if (scrimView == behind) {
                target = Math.min(target, BOUNCER_SCRIM_ALPHA);
            } else if (scrimView == front || scrimView == notifications) {
                target = 0.0f;
            } else {
                target = Math.min(target, BOUNCER_SCRIM_ALPHA);
            }
        } else {
            // BOUNCER_SCRIMMED: only the front scrim gets the light 10% veil.
            if (scrimView == front) {
                target = Math.min(target, BOUNCER_SCRIM_ALPHA);
            } else if (scrimView == behind || scrimView == notifications) {
                target = 0.0f;
            } else {
                target = Math.min(target, BOUNCER_SCRIM_ALPHA);
            }
        }

        args[alphaArgIndex] = Float.valueOf(target);

        if (RENDER_LOGGED.compareAndSet(false, true)) {
            XposedBridge.log(TAG + ": final ScrimView alpha capped, state="
                    + stateName + ", alpha=" + target);
        }
    }

    /** Keep lockscreen contents from fading to zero while PIN appears. */
    private static void hookLockscreenFade(ClassLoader classLoader) {
        final String lambdaName =
                "com.android.systemui.keyguard.ui.viewmodel."
                        + "LockscreenToPrimaryBouncerTransitionViewModel$$ExternalSyntheticLambda0";

        final Class<?> lambdaClass = XposedHelpers.findClass(lambdaName, classLoader);

        XposedBridge.hookAllMethods(lambdaClass, "invoke", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try {
                    final int classId = XposedHelpers.getIntField(param.thisObject, "$r8$classId");
                    if (classId != 0 && classId != 1) {
                        return;
                    }

                    final Object result = param.getResult();
                    if (result instanceof Float) {
                        param.setResult(Float.valueOf(1.0f));

                        if (ALPHA_LOGGED.compareAndSet(false, true)) {
                            XposedBridge.log(TAG + ": lockscreen content alpha pinned to 1.0");
                        }
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": lockscreen alpha lambda error: " + t);
                }
            }
        });
    }

    /**
     * Fallback for the legacy View-based bouncer path that is also present in
     * this SystemUI.apk. It only clears the root ViewGroup background and does
     * not touch child PIN buttons or authentication logic.
     */
    private static void hookLegacyBouncerRoot(ClassLoader classLoader) {
        final Class<?> binderClass = XposedHelpers.findClass(
                "com.android.systemui.bouncer.ui.binder.BouncerViewBinder",
                classLoader
        );

        XposedBridge.hookAllMethods(binderClass, "bind", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args == null || param.args.length == 0 || param.args[0] == null) {
                    return;
                }

                try {
                    XposedHelpers.callMethod(param.args[0], "setBackgroundColor", 0x00000000);
                    if (LEGACY_LOGGED.compareAndSet(false, true)) {
                        XposedBridge.log(TAG + ": legacy bouncer root background cleared");
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": legacy root clear failed: " + t);
                }
            }
        });
    }

    private static Object getFieldOrNull(Object object, String fieldName) {
        if (object == null) {
            return null;
        }
        try {
            return XposedHelpers.getObjectField(object, fieldName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setFloatIfPresent(Object object, String fieldName, float value) {
        try {
            XposedHelpers.setFloatField(object, fieldName, value);
        } catch (Throwable ignored) {
            // Field names are target-build-specific; missing optional fields are non-fatal.
        }
    }

    private static String stateName(Object state) {
        if (state == null) {
            return "";
        }
        if (state instanceof Enum<?>) {
            return ((Enum<?>) state).name();
        }
        return String.valueOf(state);
    }
}
