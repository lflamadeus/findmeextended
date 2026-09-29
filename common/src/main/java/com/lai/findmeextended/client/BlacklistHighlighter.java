package com.lai.findmeextended.client;

import com.lai.findmeextended.FindMeMod;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.List;

/**
 * 黑名单容器的持续高亮：每个方块一圈描边。
 * <p>
 * 不用粒子而用描边的原因：粒子是朝向摄像机的贴图，又小又会随时间淡出，只能靠定期补发看起来连续，
 * 补发之间还会明暗跳变；描边每帧都画，位置精确贴合方块，边框清晰，天然不闪。
 * 画法就是原版方块选中框那一套（{@link LevelRenderer#renderLineBox} + {@code RenderType.lines()}），
 * 区别是关掉深度测试，被墙挡住也能看见，方便找到并移出黑名单。
 */
public final class BlacklistHighlighter {

    private BlacklistHighlighter() {
    }

    /** 在关卡渲染结束时画出所有高亮框；此时 {@code poseStack} 是相机相对坐标。 */
    public static void render(PoseStack poseStack, Camera camera, List<BlockPos> positions) {
        if (positions.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        Color color = FindMeMod.CONFIG.CLIENT.getBlacklistHighlightColor();
        float red = color.getRed() / 255.0F;
        float green = color.getGreen() / 255.0F;
        float blue = color.getBlue() / 255.0F;
        Vec3 eye = minecraft.player.getEyePosition();

        Vec3 cameraPosition = camera.getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lines());
        poseStack.pushPose();
        poseStack.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        RenderSystem.disableDepthTest();
        try {
            for (BlockPos pos : positions) {
                if (!isWithinRenderDistance(pos, eye)) {
                    continue;
                }
                // 略微外扩一点，避免边框与方块表面共面而被自身遮住。
                LevelRenderer.renderLineBox(poseStack, consumer,
                        pos.getX() - 0.002D, pos.getY() - 0.002D, pos.getZ() - 0.002D,
                        pos.getX() + 1.002D, pos.getY() + 1.002D, pos.getZ() + 1.002D,
                        red, green, blue, 1.0F);
            }
        } finally {
            // 深度测试必须还原：lines 的渲染类型不带深度状态，漏还原会影响同一帧后续的绘制。
            RenderSystem.enableDepthTest();
            poseStack.popPose();
        }
        buffers.endBatch(RenderType.lines());
    }

    /** 超出客户端渲染距离的方块本来就看不见，不必为它们生成顶点。 */
    private static boolean isWithinRenderDistance(BlockPos pos, Vec3 eye) {
        int limit = Math.max(16, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16);
        double dx = pos.getX() + 0.5D - eye.x;
        double dy = pos.getY() + 0.5D - eye.y;
        double dz = pos.getZ() + 0.5D - eye.z;
        return dx * dx + dy * dy + dz * dz <= (double) limit * limit;
    }
}
