package com.lai.findmeextended.mixin;

import com.lai.findmeextended.client.BlacklistClient;
import com.lai.findmeextended.client.BlacklistHighlightStyle;
import com.lai.findmeextended.client.BlacklistHighlighter;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 在关卡渲染结束时画出黑名单容器的高亮框。
 * <p>
 * 用 Mixin 而不是平台事件：common 模块拿不到 Forge / Fabric 的渲染事件，而这个模组本来就用
 * common Mixin（见 {@link ParticleEngineAccessor}、{@link MixinSlotRenderer}），两个平台都能生效。
 * 注入点用 TAIL 而不是 RETURN：TAIL 只落在方法最后一个出口，将来原版若加了提前 return，
 * 也不会把同一帧的高亮画上好几遍。
 */
@Mixin(LevelRenderer.class)
public class MixinBlacklistHighlight {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void findmeextended$renderBlacklistHighlight(PoseStack poseStack, float partialTick, long finishNanos,
                                                         boolean renderBlockOutline, Camera camera,
                                                         GameRenderer gameRenderer, LightTexture lightTexture,
                                                         Matrix4f projectionMatrix, CallbackInfo ci) {
        BlacklistHighlighter.render(poseStack, camera, BlacklistClient.highlightedPositions(),
                BlacklistHighlightStyle.current());
    }
}
