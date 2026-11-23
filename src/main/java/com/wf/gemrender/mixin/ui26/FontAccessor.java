package com.wf.gemrender.mixin.ui26;

import net.minecraft.client.gui.Font;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Font.class)
public interface FontAccessor {
    @Accessor("provider")
    Font.Provider gemrender$provider();
}
