package com.lai.findmeextended;

import com.lai.findmeextended.network.BlacklistRequestMessage;
import com.lai.findmeextended.network.BlacklistSyncMessage;
import com.lai.findmeextended.network.PositionRequestMessage;
import com.lai.findmeextended.network.PositionResponseMessage;
import com.lai.findmeextended.network.PullItemRequestMessage;
import com.lai.findmeextended.particle.CustomParticleType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.networking.NetworkChannel;
import dev.architectury.platform.Platform;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Predicate;


public class FindMeMod {

    public static final String MOD_ID = "findmeextended";

    public static NetworkChannel CHANNEL = NetworkChannel.create(new ResourceLocation(MOD_ID, "default"));

    public static FindMeConfig CONFIG = new FindMeConfig();

    public static List<BiPredicate<BlockEntity, ItemStack>> BLOCK_CHECKERS = new ArrayList<>();
    public static List<IInventoryPuller> BLOCK_EXTRACTORS = new ArrayList<>();
    /**
     * 平台侧补充的「这个方块实体是不是容器」判定。
     * <p>
     * 原版容器（箱子、桶、漏斗等）实现 {@link Container}，两边都直接认；模组容器各平台暴露方式不同
     * （Forge 是物品处理器能力，Fabric 是 ItemStorage，AE2 是 ME 存储），由各平台在初始化时注册。
     * 黑名单工具用它判断目标能不能加入黑名单，以及清理已经不再是容器的坐标。
     */
    public static List<Predicate<BlockEntity>> BLOCK_CONTAINER_CHECKERS = new ArrayList<>();
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(FindMeMod.MOD_ID, Registries.PARTICLE_TYPE);

    public static CustomParticleType FIND_ME_PARTICLE_TYPE = new CustomParticleType(false);
    public static RegistrySupplier<ParticleType<?>> FINDME = PARTICLES.register("particle", () -> FIND_ME_PARTICLE_TYPE);

    /** 判断方块实体是不是可搜索、可取出的容器。 */
    public static boolean isContainer(BlockEntity blockEntity) {
        if (blockEntity == null) {
            return false;
        }
        if (blockEntity instanceof Container) {
            return true;
        }
        for (Predicate<BlockEntity> checker : BLOCK_CONTAINER_CHECKERS) {
            if (checker.test(blockEntity)) {
                return true;
            }
        }
        return false;
    }

    public static void init() {
        PARTICLES.register();
        CHANNEL.register(PositionRequestMessage.class,
                PositionRequestMessage::toBytes,
                friendlyByteBuf -> new PositionRequestMessage().fromBytes(friendlyByteBuf),
                PositionRequestMessage::handle
        );
        CHANNEL.register(PositionResponseMessage.class,
                PositionResponseMessage::toBytes,
                friendlyByteBuf -> new PositionResponseMessage().fromBytes(friendlyByteBuf),
                PositionResponseMessage::handle
        );
        CHANNEL.register(PullItemRequestMessage.class,
                PullItemRequestMessage::toBytes,
                friendlyByteBuf -> new PullItemRequestMessage().fromBytes(friendlyByteBuf),
                PullItemRequestMessage::handle
        );
        CHANNEL.register(BlacklistRequestMessage.class,
                BlacklistRequestMessage::toBytes,
                friendlyByteBuf -> new BlacklistRequestMessage().fromBytes(friendlyByteBuf),
                BlacklistRequestMessage::handle
        );
        CHANNEL.register(BlacklistSyncMessage.class,
                BlacklistSyncMessage::toBytes,
                friendlyByteBuf -> new BlacklistSyncMessage().fromBytes(friendlyByteBuf),
                BlacklistSyncMessage::handle
        );
        BLOCK_CHECKERS.add((blockEntity, itemStack) -> {
            if (blockEntity instanceof Container inventory) {
                if (inventory.isEmpty()) return false;
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    if (!inventory.getItem(i).isEmpty() && PositionRequestMessage.compareItems(itemStack, inventory.getItem(i))) {
                        return true;
                    }
                }
            }
            return false;
        });

        File file = new File(Platform.getConfigFolder() + File.separator + MOD_ID + ".json");
        if (!file.exists()) {
            createConfig(file);
        }
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            FileReader reader = new FileReader(file);
            CONFIG = gson.fromJson(reader, FindMeConfig.class);
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
            createConfig(file);
        }
    }

    private static void createConfig(File file) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            FileWriter fileWriter = new FileWriter(file);
            gson.toJson(CONFIG, fileWriter);
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
