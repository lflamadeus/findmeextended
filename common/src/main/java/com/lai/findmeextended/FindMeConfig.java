package com.lai.findmeextended;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class FindMeConfig {

    public Common COMMON = new Common();
    public Client CLIENT = new Client();


    public static class Client {

        public int CONTAINER_TRACK_TIME = 30 * 20;
        public boolean CONTAINER_TRACKING = true;
        public String CONTAINER_HIGHLIGHT_COLOR = "#cf9d15";
        private transient Color currentColor = null;
        public String PARTICLE_HIGHLIGHT_COLOR = "#ffffff";
        private transient Color currentParticleColor = null;
        /**
         * 黑名单容器高亮的轮廓颜色，默认亮黄。
         * <p>
         * 用黄而不是红：红色在 MC 里已经被「危险 / 敌对 / 掉血」占满了，玩家看到红框猜不到
         * 「这个容器被排除在搜索之外」。黄色配上深灰填充更像「警示 / 别动这里」。
         * 色值与 {@link #CONTAINER_HIGHLIGHT_COLOR}（搜索结果的金色）刻意拉开明度和饱和度。
         */
        public String BLACKLIST_HIGHLIGHT_COLOR = "#FFE533";
        /** 黑名单高亮的填充颜色，默认深灰；配合黄色轮廓表达「这个容器不可用」。 */
        public String BLACKLIST_FILL_COLOR = "#2D2D2D";
        /** 黑名单填充的不透明度（0~1）。设为 0 就只剩轮廓。 */
        public double BLACKLIST_FILL_ALPHA = 0.52D;


        public Color getColor() {
            if (currentColor == null) {
                try {
                    currentColor = Color.decode(CONTAINER_HIGHLIGHT_COLOR.toLowerCase());
                } catch (NumberFormatException e) {
                    //FindMe.LOG.error("Unable to parse color value '" + CONTAINER_HIGHLIGHT_COLOR.get() + "'", e);
                    currentColor = Color.decode("#cf9d15");
                }
            }
            return currentColor;
        }

        public Color getParticleColor() {
            if (currentParticleColor == null) {
                try {
                    currentParticleColor = Color.decode(PARTICLE_HIGHLIGHT_COLOR.toLowerCase());
                } catch (NumberFormatException e) {
                    //FindMe.LOG.error("Unable to parse color value '" + PARTICLE_HIGHLIGHT_COLOR.get() + "'", e);
                    currentParticleColor = Color.decode("#ffffff");
                }
            }
            return currentParticleColor;
        }


    }

    public static class Common {
        public int RADIUS_RANGE = 8;
        public boolean IGNORE_ITEM_DAMAGE = false;
        /**
         * 黑名单工具的物品 ID，可以填多个。手持其中之一（主手）时：视距内的黑名单容器持续高亮，
         * 潜行右键把目标容器加入黑名单，潜行左键移出黑名单。默认木剑。
         */
        public List<String> BLACKLIST_TOOLS = new ArrayList<>(List.of("minecraft:wooden_sword"));

    }
}
