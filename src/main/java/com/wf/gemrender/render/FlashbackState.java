package com.wf.gemrender.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Flashback video export. Wall-clock budgets (progressive fills, upload slices) protect a live frame rate; an export
 * has no frame rate to protect and renders each frame once, so a budget only leaves early frames incomplete. Flashback
 * is optional, hence reflective: absent or failing => not exporting.
 */
public final class FlashbackState {
    private static final MethodHandle EXPORTING = handle();

    private FlashbackState() {
    }

    public static boolean exporting() {
        if (EXPORTING == null) {
            return false;
        }
        try {
            return (boolean) EXPORTING.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    private static MethodHandle handle() {
        try {
            return MethodHandles.publicLookup().findStatic(Class.forName("com.moulberry.flashback.Flashback"),
                    "isExporting", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
