package com.lai.findmeextended;

import com.lai.findmeextended.client.BlacklistClient;
import com.lai.findmeextended.client.ClientTickHandler;
import com.lai.findmeextended.client.ParticlePosition;
import com.lai.findmeextended.network.PositionRequestMessage;
import com.lai.findmeextended.network.PullItemRequestMessage;
import com.mojang.blaze3d.platform.InputConstants;
import dev.architectury.event.EventResult;
import dev.architectury.event.events.client.ClientRawInputEvent;
import dev.architectury.event.events.client.ClientTickEvent;
import dev.architectury.event.events.client.ClientTooltipEvent;
import dev.architectury.registry.client.keymappings.KeyMappingRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

import static net.minecraft.client.particle.ParticleEngine.RENDER_ORDER;

public class FindMeModClient {


    public static KeyMapping KEY = new KeyMapping("key.findmeextended.search", InputConstants.getKey("key.keyboard.y").getValue(), "key.findmeextended.category");
    public static KeyMapping PULL_ONE = new KeyMapping("key.findmeextended.pull_one", InputConstants.getKey("key.keyboard.keypad.0").getValue(), "key.findmeextended.category");
    public static KeyMapping PULL_STACK = new KeyMapping("key.findmeextended.pull_stack", InputConstants.getKey("key.keyboard.keypad.1").getValue(), "key.findmeextended.category");



    public static long lastTooltipTime = 0;
    public static ItemStack lastRenderedStack = ItemStack.EMPTY;

    public FindMeModClient() {
        init();
    }

    private static void init() {
        KeyMappingRegistry.register(KEY);
        KeyMappingRegistry.register(PULL_ONE);
        KeyMappingRegistry.register(PULL_STACK);
        ClientTickEvent.CLIENT_PRE.register(instance -> ClientTickHandler.clientTick());
        ClientTickEvent.CLIENT_PRE.register(BlacklistClient::tick);
        // 潜行左右键的黑名单操作走原始鼠标事件：返回 interruptFalse() 会取消这次按键的原版处理，
        // 因此不会打开容器界面、也不会开始挖方块。
        ClientRawInputEvent.MOUSE_CLICKED_PRE.register(BlacklistClient::onMouseClicked);
        ClientTooltipEvent.ITEM.register((stack, lines, flag) -> {
            if (!stack.isEmpty() && Minecraft.getInstance().level != null) {
                lastRenderedStack = stack;
                lastTooltipTime = Minecraft.getInstance().level.getGameTime();
            }
        });
        ClientRawInputEvent.KEY_PRESSED.register((client, keyCode, scanCode, action, modifiers) -> {
            if (!lastRenderedStack.isEmpty() && client.level != null && client.level.getGameTime() - lastTooltipTime < 3) {
                if (KEY.matches(keyCode, scanCode))
                    FindMeMod.CHANNEL.sendToServer(new PositionRequestMessage(lastRenderedStack));
                if (PULL_ONE.matches(keyCode, scanCode) && action == 1)
                    FindMeMod.CHANNEL.sendToServer(new PullItemRequestMessage(lastRenderedStack, 1));
                if (PULL_STACK.matches(keyCode, scanCode) && action == 1) {
                    // 按住 Alt：尽可能填满背包；服务端按背包剩余空间截断，装不下的不会被丢在地上。
                    int amount = isAltDown()
                            ? PullItemRequestMessage.FILL_INVENTORY
                            : lastRenderedStack.getMaxStackSize();
                    FindMeMod.CHANNEL.sendToServer(new PullItemRequestMessage(lastRenderedStack, amount));
                }
            }
            return EventResult.pass();
        });
        if (!RENDER_ORDER.contains(ParticlePosition.CUSTOM)) {
            RENDER_ORDER = new ArrayList<>(RENDER_ORDER);
            RENDER_ORDER.add(ParticlePosition.CUSTOM);
        }
    }

    /**
     * Alt 是否按下。
     * <p>
     * 直接查 GLFW 的实时按键状态，而不是用事件里的修饰键位：两个平台的原始输入事件对 mods 的
     * 填充情况并不一致，查按键状态两边都准。
     */
    private static boolean isAltDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

}
