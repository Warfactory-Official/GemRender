package com.wf.gemrender.direct;

public enum DirectPass {

    GUI,

    LEVEL,

    HAND,

    /** LEVEL copies queued inside a shaderpack's shadow pass; {@link DirectGuest} only. */
    SHADOW
}
