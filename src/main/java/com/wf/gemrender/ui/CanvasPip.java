package com.wf.gemrender.ui;

//? if >=26.1 {
/*import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.wf.gemrender.render.Vanilla;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import java.util.ArrayList;
import java.util.List;

public final class CanvasPip extends PictureInPictureRenderer<CanvasPass> {
    private static int frame;

    private final List<Target> targets = new ArrayList<>();
    private int seenFrame = -1;
    private int used;
    private int usedLastFrame;

    public CanvasPip(MultiBufferSource.BufferSource bufferSource) {
        super(bufferSource);
    }

    public static void nextFrame() {
        frame++;
    }

    @Override
    public void prepare(CanvasPass pass, GuiRenderState guiRenderState, int guiScale) {
        if (seenFrame != frame) {
            seenFrame = frame;
            usedLastFrame = used;
            used = 0;
            while (targets.size() > usedLastFrame) {
                targets.removeLast().close();
            }
        }
        Target target = target(used++, pass.pixelWidth, pass.pixelHeight);
        Canvas.shared().replay(pass, Vanilla.textureId(target.texture), pass.pixelWidth, pass.pixelHeight);
        pass.recycle();
        guiRenderState.addBlitToCurrentLayer(new BlitRenderState(
                RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
                TextureSetup.singleTexture(target.view, RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)),
                pass.pose(), pass.x0(), pass.y0(), pass.x1(), pass.y1(),
                0.0f, pass.pixelWidth / (float) target.width, pass.pixelHeight / (float) target.height, 0.0f,
                -1, pass.scissorArea(), null));
    }

    private Target target(int index, int width, int height) {
        if (index < targets.size()) {
            Target existing = targets.get(index);
            if (existing.width >= width && existing.height >= height) {
                return existing;
            }
            existing.close();
            targets.set(index, create(Math.max(width, existing.width), Math.max(height, existing.height)));
            return targets.get(index);
        }
        Target created = create(width, height);
        targets.add(created);
        return created;
    }

    private static Target create(int width, int height) {
        var device = RenderSystem.getDevice();
        GpuTexture texture = device.createTexture(() -> "gemrender canvas", 13, TextureFormat.RGBA8, width, height, 1, 1);
        return new Target(texture, device.createTextureView(texture), width, height);
    }

    @Override
    public void close() {
        targets.forEach(Target::close);
        targets.clear();
        super.close();
    }

    @Override
    public Class<CanvasPass> getRenderStateClass() {
        return CanvasPass.class;
    }

    @Override
    protected void renderToTexture(CanvasPass pass, PoseStack poseStack) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected String getTextureLabel() {
        return "gemrender canvas";
    }

    private record Target(GpuTexture texture, GpuTextureView view, int width, int height) {
        void close() {
            view.close();
            texture.close();
        }
    }
}
*///?}
