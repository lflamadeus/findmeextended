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
        /** 黑名单容器高亮框的颜色，默认亮红：黑色名单语义是“别动这个容器”，也要足够显眼。 */
        public String BLACKLIST_HIGHLIGHT_COLOR = "#FF3B30";
        private transient Color currentBlacklistHighlightColor = null;


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

        public Color getBlacklistHighlightColor() {
            if (currentBlacklistHighlightColor == null) {
                try {
                    currentBlacklistHighlightColor = Color.decode(BLACKLIST_HIGHLIGHT_COLOR.toLowerCase());
                } catch (NumberFormatException e) {
                    currentBlacklistHighlightColor = Color.decode("#FF3B30");
                }
            }
            return currentBlacklistHighlightColor;
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
