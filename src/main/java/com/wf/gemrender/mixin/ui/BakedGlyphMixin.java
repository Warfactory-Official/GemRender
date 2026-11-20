package com.wf.gemrender.mixin.ui;

import com.wf.gemrender.ui.SharpText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BakedGlyph.class)
abstract class BakedGlyphMixin {
    @Inject(method = "renderType", at = @At("RETURN"), cancellable = true)
    private void gemrender$sharp(Font.DisplayMode mode, CallbackInfoReturnable<RenderType> cir) {
        cir.setReturnValue(SharpText.select(cir.getReturnValue(), mode));
    }
}
