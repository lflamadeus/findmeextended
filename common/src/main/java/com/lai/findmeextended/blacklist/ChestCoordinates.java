package com.lai.findmeextended.blacklist;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

/**
 * 双箱子的坐标归一化。
 * <p>
 * 双箱子的两半共用同一份库存：Forge 侧两半暴露的都是合并后的物品处理器，搜索与取出都会看到
 * 同一批物品。如果黑名单只记录玩家点到的那一半，另一半仍然能被搜索到、取出，黑名单就会静默失效。
 * 因此增删与命中判定统一换算成坐标较小的那一半，点哪一半都一样有效。
 * <p>
 * 归一化只依赖该方块自己的方块状态（朝向 + 左/右），不读取另一半的区块，客户端与服务端必然算出
 * 同一个结果。
 */
public final class ChestCoordinates {

    private ChestCoordinates() {
    }

    /** 双箱子返回坐标较小的那一半，其余方块原样返回。 */
    public static BlockPos canonical(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)
                || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) {
            return pos;
        }
        BlockPos connected = pos.relative(ChestBlock.getConnectedDirection(state));
        return pos.compareTo(connected) <= 0 ? pos : connected;
    }
}
