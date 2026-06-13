package com.autonavi.companion;

/**
 * Registry for overlay UI styles.
 *
 * Add a new style here first, then implement the corresponding panel builder in
 * OverlayService.buildPanelForStyle(). Keeping ids and labels centralized makes
 * downstream UI forks less likely to miss preference, dialog, or preview wiring.
 */
public final class OverlayUiStyles {

    public static final String OLD = "old";
    public static final String NEW = "new";
    public static final String DYNAMIC_ISLAND_FULL = "dynamic_island_full";
    public static final String CARD = "card";
    public static final String PLUGIN_PREFIX = "plugin:";

    public static final Style[] ALL = {
            new Style(OLD, "\u7ecf\u5178 UI\uff08\u9ed8\u8ba4\uff09", "\u7ecf\u5178 UI\uff08\u9ed8\u8ba4\uff09", false),
            new Style(CARD, "\u5361\u7247 UI", "\u5361\u7247 UI", true),
            new Style(DYNAMIC_ISLAND_FULL, "\u7075\u52a8\u5c9b", "\u7075\u52a8\u5c9b", true),
            new Style(NEW, "\u65b0 UI\uff08\u5361\u7247\u6837\u5f0f\uff0c\u6d4b\u8bd5\u4e2d\uff09", "\u65b0 UI\uff08\u6d4b\u8bd5\u4e2d\uff09", true)
    };

    private OverlayUiStyles() {
    }

    public static String normalize(String styleId) {
        if ("dynamic_island_full".equals(styleId) || "dynamic_island".equals(styleId)) {
            return DYNAMIC_ISLAND_FULL;
        }
        if (isPluginStyle(styleId)) {
            return styleId;
        }
        for (Style style : ALL) {
            if (style.id.equals(styleId)) {
                return style.id;
            }
        }
        return OLD;
    }

    public static boolean isCardLike(String styleId) {
        if (isPluginStyle(styleId)) {
            return false;
        }
        return find(styleId).cardLike;
    }

    public static int indexOf(String styleId) {
        String normalized = normalize(styleId);
        for (int i = 0; i < ALL.length; i++) {
            if (ALL[i].id.equals(normalized)) {
                return i;
            }
        }
        return 0;
    }

    public static String[] labels() {
        String[] labels = new String[ALL.length];
        for (int i = 0; i < ALL.length; i++) {
            labels[i] = ALL[i].dialogLabel;
        }
        return labels;
    }

    public static String displayName(String styleId) {
        return find(styleId).displayName;
    }

    public static String pluginStyleId(String pluginId) {
        try {
            PluginManifest.validatePluginId(pluginId);
            return PLUGIN_PREFIX + pluginId;
        } catch (Throwable ignored) {
            return OLD;
        }
    }

    public static boolean isPluginStyle(String styleId) {
        if (styleId == null || !styleId.startsWith(PLUGIN_PREFIX)) {
            return false;
        }
        try {
            PluginManifest.validatePluginId(pluginIdFromStyle(styleId));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String pluginIdFromStyle(String styleId) {
        if (styleId == null || !styleId.startsWith(PLUGIN_PREFIX)) {
            return "";
        }
        return styleId.substring(PLUGIN_PREFIX.length());
    }

    private static Style find(String styleId) {
        String normalized = normalize(styleId);
        for (Style style : ALL) {
            if (style.id.equals(normalized)) {
                return style;
            }
        }
        return ALL[0];
    }

    public static final class Style {
        public final String id;
        public final String dialogLabel;
        public final String displayName;
        public final boolean cardLike;

        private Style(String id, String dialogLabel, String displayName, boolean cardLike) {
            this.id = id;
            this.dialogLabel = dialogLabel;
            this.displayName = displayName;
            this.cardLike = cardLike;
        }
    }
}
