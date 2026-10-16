package com.wf.gemrender.iris.guest;

import net.irisshaders.iris.shaderpack.loading.ProgramId;

/** Pack program a direct batch draws through; Iris's own pick for the vanilla entity render types. */
enum Role {
    ENTITIES(ProgramId.Entities, false),
    ENTITIES_TRANSLUCENT(ProgramId.EntitiesTrans, false),
    // Iris draws non-block translucent items in its solid hand pass, with the solid hand program.
    HAND(ProgramId.Hand, false),
    //? if >=1.21 {
    SHADOW(ProgramId.ShadowEntities, true);
    //?} else {
    /*SHADOW(ProgramId.Shadow, true);
    *///?}

    final ProgramId programId;
    final boolean shadow;

    Role(ProgramId programId, boolean shadow) {
        this.programId = programId;
        this.shadow = shadow;
    }
}
