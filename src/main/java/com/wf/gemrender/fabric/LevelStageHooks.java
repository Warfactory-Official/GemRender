package com.wf.gemrender.fabric;

import com.wf.gemrender.render.LevelStage;
//? if >=26.1 {
/*import com.wf.gemrender.render.Vanilla;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
*///?} else {
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
//?}

public final class LevelStageHooks {
    private LevelStageHooks() {
    }

    // On 26.1 the NeoForge stages map to their Fabric counterparts. AfterLevel and AfterWeather both land
    // on END_MAIN, the last hook Fabric offers, which runs before clouds and weather.
    //? if >=26.1 {
    /*public static void init() {
        LevelRenderEvents.AFTER_SOLID_FEATURES.register(LevelStageHooks::afterSolidFeatures);
        LevelRenderEvents.AFTER_TRANSLUCENT_FEATURES.register(LevelStageHooks::afterTranslucentFeatures);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(LevelStageHooks::afterTranslucentTerrain);
        LevelRenderEvents.END_MAIN.register(LevelStageHooks::endMain);
    }

    private static void afterSolidFeatures(LevelRenderContext context) {
        Vanilla.intoMainTarget(LevelStageHooks::directFlush);
        waterAfterEntities(new LevelStage(context.sectionsToRender()));
    }

    private static void afterTranslucentFeatures(LevelRenderContext context) {
        Vanilla.intoMainTarget(LevelStageHooks::directFlush);
    }

    private static void afterTranslucentTerrain(LevelRenderContext context) {
        waterAfterTranslucent();
    }

    private static void endMain(LevelRenderContext context) {
        Vanilla.intoMainTarget(LevelStageHooks::directFlush);
        waterAfterWeather();
    }
    *///?} else {
    public static void init() {
        WorldRenderEvents.AFTER_ENTITIES.register(LevelStageHooks::afterEntities);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(LevelStageHooks::beforeDebugRender);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(LevelStageHooks::afterTranslucent);
        WorldRenderEvents.LAST.register(LevelStageHooks::last);
    }

    private static void afterEntities(WorldRenderContext context) {
        directFlush();
        waterAfterEntities(stageOf(context));
    }

    private static void beforeDebugRender(WorldRenderContext context) {
        directFlush();
    }

    private static void afterTranslucent(WorldRenderContext context) {
        waterAfterTranslucent();
    }

    private static void last(WorldRenderContext context) {
        directFlush();
        waterAfterWeather();
    }
    //?}

    //? if water {
    private static void waterAfterEntities(LevelStage stage) {
        com.wf.gemrender.water.WaterSplit.getInstance()
                .onAfterEntities(stage);
    }

    private static void waterAfterTranslucent() {
        com.wf.gemrender.water.WaterSplit.getInstance()
                .onAfterTranslucent();
    }

    private static void waterAfterWeather() {
        com.wf.gemrender.water.WaterSplit.getInstance()
                .onAfterWeather();
    }
    //?} else {
    /*private static void waterAfterEntities(LevelStage stage) {
    }

    private static void waterAfterTranslucent() {
    }

    private static void waterAfterWeather() {
    }
    *///?}

    //? if direct {
    private static void directFlush() {
        com.wf.gemrender.direct.DirectRenderer.flush(com.wf.gemrender.direct.DirectPass.LEVEL);
    }
    //?} else {
    /*private static void directFlush() {
    }
    *///?}

    //? if >=1.21 <26.1 {
    private static LevelStage stageOf(WorldRenderContext context) {
        return new LevelStage(context.worldRenderer(), context.camera(), context.frustum(),
                context.matrixStack(), context.positionMatrix(), context.projectionMatrix());
    }
    //?} elif <1.21 {
	/*private static LevelStage stageOf(WorldRenderContext context) {
		return new LevelStage(context.worldRenderer(), context.camera(), context.frustum(),
				context.matrixStack(), null, context.projectionMatrix());
	}
*///?}
}
