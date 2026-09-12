package com.wf.gemrender.direct;

import com.wf.gemrender.water.GpuPassTimer;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class DirectStats {
    private static final boolean ENABLED = Boolean.getBoolean("gemrender.directstats");

    private static final Map<DirectPass, Counters> PASSES = new EnumMap<>(DirectPass.class);

    private static long submitNanos;
    private static long submitStart;

    private DirectStats() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    private static Counters of(DirectPass pass) {
        return PASSES.computeIfAbsent(pass, key -> new Counters());
    }

    static void submitBegin() {
        if (ENABLED) {
            submitStart = System.nanoTime();
        }
    }

    static void submitEnd(DirectPass pass) {
        if (!ENABLED) {
            return;
        }
        submitNanos += System.nanoTime() - submitStart;
        of(pass).copies++;
    }

    static void palette(DirectPass pass, boolean reused) {
        if (!ENABLED) {
            return;
        }
        Counters counters = of(pass);
        if (reused) {
            counters.palettesReused++;
        } else {
            counters.palettesEvaluated++;
        }
    }

    static void flushBegin(DirectPass pass) {
        if (ENABLED) {
            of(pass).timer.begin();
        }
    }

    static void flushEnd(DirectPass pass, int draws, int instances) {
        if (!ENABLED) {
            return;
        }
        Counters counters = of(pass);
        counters.timer.end();
        counters.flushes++;
        counters.draws += draws;
        counters.instances += instances;
    }

    public static void reset() {
        submitNanos = 0;
        for (Counters counters : PASSES.values()) {
            counters.timer.reset();
            counters.flushes = 0;
            counters.copies = 0;
            counters.palettesEvaluated = 0;
            counters.palettesReused = 0;
            counters.draws = 0;
            counters.instances = 0;
        }
    }

    public static String report() {
        if (!ENABLED) {
            return "off";
        }

        StringBuilder out = new StringBuilder();
        for (Map.Entry<DirectPass, Counters> entry : PASSES.entrySet()) {
            Counters counters = entry.getValue();
            if (counters.flushes == 0) {
                continue;
            }

            out.append(out.isEmpty() ? "" : " ")
                    .append(String.format(Locale.ROOT,
                            "%s(flushes=%d,copies=%d,palettes=%d/%d,draws=%d,instances=%d,"
                                    + "gpu=%.1fus,cpu=%.1fus)",
                            entry.getKey()
                                    .name()
                                    .toLowerCase(Locale.ROOT),
                            counters.flushes, counters.copies,
                            counters.palettesEvaluated,
                            counters.palettesEvaluated + counters.palettesReused,
                            counters.draws, counters.instances,
                            counters.timer.meanGpuNanos() / 1000.0,
                            counters.timer.meanCpuNanos() / 1000.0));
        }

        out.append(String.format(Locale.ROOT, " submitTotal=%dus", submitNanos / 1000));
        return out.isEmpty() ? "idle" : out.toString();
    }

    private static final class Counters {
        private final GpuPassTimer timer = new GpuPassTimer();

        private long flushes;
        private long copies;
        private long palettesEvaluated;
        private long palettesReused;
        private long draws;
        private long instances;
    }
}
