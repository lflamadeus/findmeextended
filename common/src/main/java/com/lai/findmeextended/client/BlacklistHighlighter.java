package com.lai.findmeextended.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.List;

/**
 * 黑名单容器的持续高亮：每个方块一层半透明填充 + 一圈描边。
 * <p>
 * 画法照 WorldEditCUI（{@code BufferBuilderRenderSink} + {@code CUIListenerWorldRender}）：
 * <b>不走 {@code RenderType} / {@code MultiBufferSource}</b>，而是自己开 {@link Tesselator}、
 * 自己设 GL 状态。原因是 {@code RenderType} 会在 {@code endBatch} 里把整套状态重新设一遍，
 * 从外面压不住深度测试——而「穿墙」恰恰只能靠直接改深度状态实现。
 * <p>
 * 具体对应关系：
 * <ul>
 *   <li>穿墙 = {@code RenderSystem.depthFunc(GL_ALWAYS)}，也就是 WorldEditCUI 的 {@code ANY(519)}。</li>
 *   <li>顶点用「相对摄像机的世界坐标」，和原版方块选中框一致。摄像机的旋转由 model-view 提供，
 *       所以先把当前 {@code poseStack} 并进 model-view 再画，不依赖调用点把 poseStack 留在什么状态。</li>
 *   <li>{@code FogRenderer.setupNoFog()}：{@code rendertype_lines} 的片元着色器会做线性雾，
 *       远处的框会被雾洗淡，而穿墙的意义正是找远处的箱子。</li>
 *   <li>所有改过的状态都在 {@code finally} 里还原。</li>
 * </ul>
 * 填充与描边分两批画，<b>顺序不能调换</b>：两批都不写深度，谁后画谁在上面，描边必须压在填充上。
 */
public final class BlacklistHighlighter {

    /**
     * 相对方块表面的外扩量。
     * <p>
     * 深度测试关掉之后它不再影响可见性，留着是防共面：万一以后有人加一个「只在看得见时高亮」的
     * 开关（把深度测试打开），外扩能让边框稳定地压在方块表面之上。
     */
    private static final double EXPAND = 0.002D;

    /** 面编号，用于在 {@link #FACE_CORNERS} 与 {@link #FACE_SHADE} 里取数据。 */
    private static final int WEST = 0;
    private static final int EAST = 1;
    private static final int DOWN = 2;
    private static final int UP = 3;
    private static final int NORTH = 4;
    private static final int SOUTH = 5;

    /**
     * 每个面的 4 个角，每个角 3 个数：0 表示取包围盒的 min、1 表示取 max。
     * <p>
     * 写成常量表而不是一长串 double 参数，是为了让「哪个面用哪四个角」一眼可查，也避免每次调用
     * 产生临时对象。下标顺序与上面的面编号一致。
     */
    private static final int[][] FACE_CORNERS = {
            {0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0},   // -X
            {1, 0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1},   // +X
            {0, 0, 1, 1, 0, 1, 1, 0, 0, 0, 0, 0},   // -Y
            {1, 1, 1, 0, 1, 1, 0, 1, 0, 1, 1, 0},   // +Y
            {1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0},   // -Z
            {0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 1, 1}    // +Z
    };

    /**
     * 各面的方向光衰减，取值和原版方块一致：顶 1.0、底 0.5、南北 0.8、东西 0.6。
     * <p>
     * {@code position_color} 管线不做光照，不自己衰减的话填充就是一整块平色，看不出体积。
     */
    private static final float[] FACE_SHADE = {0.6F, 0.6F, 0.5F, 1.0F, 0.8F, 0.8F};

    /**
     * 12 条棱，每条两个端点，每个端点 3 个数：0 表示取包围盒的 min、1 表示取 max。
     * <p>
     * 按方向分组，方便核对「每个角恰好被 3 条棱共用」。
     */
    private static final int[][] EDGE_CORNERS = {
            {0, 0, 0, 1, 0, 0}, {0, 0, 1, 1, 0, 1}, {0, 1, 0, 1, 1, 0}, {0, 1, 1, 1, 1, 1},   // 沿 X
            {0, 0, 0, 0, 1, 0}, {1, 0, 0, 1, 1, 0}, {0, 0, 1, 0, 1, 1}, {1, 0, 1, 1, 1, 1},   // 沿 Y
            {0, 0, 0, 0, 0, 1}, {1, 0, 0, 1, 0, 1}, {0, 1, 0, 0, 1, 1}, {1, 1, 0, 1, 1, 1}    // 沿 Z
    };

    private BlacklistHighlighter() {
    }

    /**
     * 在关卡渲染结束时画出所有高亮框。
     *
     * @param poseStack 调用点当前的姿态；会被并进 model-view，顶点随后用「相对摄像机的世界坐标」
     * @param positions 要画的方块坐标，通常是服务端回报的黑名单快照
     * @param style     轮廓与填充的颜色，由 {@link BlacklistHighlightStyle} 从配置解析而来
     */
    public static void render(PoseStack poseStack, Camera camera, List<BlockPos> positions,
                              BlacklistHighlightStyle style) {
        if (positions.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        // 相机位置本身就是「从哪里看」，而且 getPosition() 返回的是字段，不像玩家眼睛位置那样
        // 每帧新建一个 Vec3。
        Vec3 origin = camera.getPosition();
        // 渲染距离是配置读取，提到循环外面只取一次。
        double limit = renderDistanceLimit();

        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.mulPoseMatrix(poseStack.last().pose());
        RenderSystem.applyModelViewMatrix();

        float fogStart = RenderSystem.getShaderFogStart();
        ShaderInstance previousShader = RenderSystem.getShader();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 穿墙：GL_ALWAYS 让所有片段都通过深度测试，包括被墙挡住的那部分。
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        // 不写深度：高亮是叠加层，不该往深度缓冲里留东西影响后面的手部渲染。
        RenderSystem.depthMask(false);
        FogRenderer.setupNoFog();
        try {
            drawFill(positions, origin, limit, style.fill());
            drawOutline(positions, origin, limit, style.line());
        } finally {
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.setShader(() -> previousShader);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void drawFill(List<BlockPos> positions, Vec3 origin, double limit, Color color) {
        if (color.getAlpha() == 0) {
            // 玩家把填充透明度调成 0：只画轮廓，一个顶点都不发。
            return;
        }
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float red = color.getRed() / 255.0F;
        float green = color.getGreen() / 255.0F;
        float blue = color.getBlue() / 255.0F;
        float alpha = color.getAlpha() / 255.0F;
        for (BlockPos pos : positions) {
            if (!isWithinRenderDistance(pos, origin, limit)) {
                continue;
            }
            drawBoxFill(builder, pos, origin, red, green, blue, alpha);
        }
        // 着色器颜色是全局状态，别的地方可能留了非白色；顺手抹平，否则高亮会被染色。
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        tesselator.end();
    }

    private static void drawOutline(List<BlockPos> positions, Vec3 origin, double limit, Color color) {
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
        float red = color.getRed() / 255.0F;
        float green = color.getGreen() / 255.0F;
        float blue = color.getBlue() / 255.0F;
        float alpha = color.getAlpha() / 255.0F;
        for (BlockPos pos : positions) {
            if (!isWithinRenderDistance(pos, origin, limit)) {
                continue;
            }
            drawBoxOutline(builder, pos, origin, red, green, blue, alpha);
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
        tesselator.end();
    }

    /**
     * 只发朝向摄像机的那 3 个面。
     * <p>
     * 6 个面全发的话一条视线会穿过 2 个面，半透明被叠两次，实际浓度远高于配置值。自己剔除背面
     * 顺带把顶点数从 24 降到 12。
     */
    private static void drawBoxFill(BufferBuilder builder, BlockPos pos, Vec3 origin,
                                    float red, float green, float blue, float alpha) {
        double x0 = pos.getX() - EXPAND - origin.x;
        double y0 = pos.getY() - EXPAND - origin.y;
        double z0 = pos.getZ() - EXPAND - origin.z;
        double x1 = pos.getX() + 1.0D + EXPAND - origin.x;
        double y1 = pos.getY() + 1.0D + EXPAND - origin.y;
        double z1 = pos.getZ() + 1.0D + EXPAND - origin.z;
        // 顶点已经相对摄像机，所以摄像机就在原点：包围盒落在哪个半轴，就画哪一侧的面。
        if (x0 > 0.0D) {
            drawFace(builder, WEST, x0, y0, z0, x1, y1, z1, red, green, blue, alpha);
        } else if (x1 < 0.0D) {
            drawFace(builder, EAST, x0, y0, z0, x1, y1, z1, red, green, blue, alpha);
        }
        if (y0 > 0.0D) {
            drawFace(builder, DOWN, x0, y0, z0, x1, y1, z1, red, green, blue, alpha);
        } else if (y1 < 0.0D) {
            drawFace(builder, UP, x0, y0, z0, x1, y1, z1, red, green, blue, alpha);
        }
        if (z0 > 0.0D) {
            drawFace(builder, NORTH, x0, y0, z0, x1, y1, z1, red, green, blue, alpha);
        } else if (z1 < 0.0D) {
            drawFace(builder, SOUTH, x0, y0, z0, x1, y1, z1, red, green, blue, alpha);
        }
    }

    private static void drawFace(BufferBuilder builder, int face,
                                 double x0, double y0, double z0, double x1, double y1, double z1,
                                 float red, float green, float blue, float alpha) {
        // 方向光衰减每个面算一次，不放进顶点循环。
        float shade = FACE_SHADE[face];
        float shadedRed = red * shade;
        float shadedGreen = green * shade;
        float shadedBlue = blue * shade;
        int[] corners = FACE_CORNERS[face];
        for (int index = 0; index < corners.length; index += 3) {
            builder.vertex(corners[index] == 0 ? x0 : x1,
                            corners[index + 1] == 0 ? y0 : y1,
                            corners[index + 2] == 0 ? z0 : z1)
                    .color(shadedRed, shadedGreen, shadedBlue, alpha)
                    .endVertex();
        }
    }

    /**
     * 12 条棱，每条两个顶点。
     * <p>
     * 法线不是给光照用的：{@code rendertype_lines} 的顶点着色器拿它把线段扩成屏幕空间的四边形，
     * 所以必须传这条棱的方向单位向量，否则线段的粗细和朝向都会错。
     */
    private static void drawBoxOutline(BufferBuilder builder, BlockPos pos, Vec3 origin,
                                       float red, float green, float blue, float alpha) {
        double x0 = pos.getX() - EXPAND - origin.x;
        double y0 = pos.getY() - EXPAND - origin.y;
        double z0 = pos.getZ() - EXPAND - origin.z;
        double x1 = pos.getX() + 1.0D + EXPAND - origin.x;
        double y1 = pos.getY() + 1.0D + EXPAND - origin.y;
        double z1 = pos.getZ() + 1.0D + EXPAND - origin.z;
        for (int[] edge : EDGE_CORNERS) {
            double startX = edge[0] == 0 ? x0 : x1;
            double startY = edge[1] == 0 ? y0 : y1;
            double startZ = edge[2] == 0 ? z0 : z1;
            double endX = edge[3] == 0 ? x0 : x1;
            double endY = edge[4] == 0 ? y0 : y1;
            double endZ = edge[5] == 0 ? z0 : z1;
            double deltaX = endX - startX;
            double deltaY = endY - startY;
            double deltaZ = endZ - startZ;
            double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
            float normalX = (float) (deltaX / length);
            float normalY = (float) (deltaY / length);
            float normalZ = (float) (deltaZ / length);
            builder.vertex(startX, startY, startZ).color(red, green, blue, alpha)
                    .normal(normalX, normalY, normalZ).endVertex();
            builder.vertex(endX, endY, endZ).color(red, green, blue, alpha)
                    .normal(normalX, normalY, normalZ).endVertex();
        }
    }

    /** 客户端渲染距离（方块）。 */
    private static double renderDistanceLimit() {
        return Math.max(16, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16);
    }

    /** 超出客户端渲染距离的方块本来就看不见，不必为它们生成顶点。 */
    private static boolean isWithinRenderDistance(BlockPos pos, Vec3 origin, double limit) {
        double dx = pos.getX() + 0.5D - origin.x;
        double dy = pos.getY() + 0.5D - origin.y;
        double dz = pos.getZ() + 0.5D - origin.z;
        return dx * dx + dy * dy + dz * dz <= limit * limit;
    }
}
