package com.autonavi.companion;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Environment;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;

final class FontManager {
    private static final String[] FONT_PATHS = {
            "amap_companion/font.ttf",
            "amap_companion/font.otf",
            "amap_companion/custom_font.ttf",
            "amap_companion/custom_font.otf",
            "amap_companion/diy/font.ttf",
            "amap_companion/diy/font.otf",
            "amap_companion/diy/custom_font.ttf",
            "amap_companion/diy/custom_font.otf"
    };

    private static String cachedPath;
    private static long cachedModified;
    private static Typeface cachedTypeface;

    private FontManager() {
    }

    static Typeface regular(Context context) {
        return customTypeface(context);
    }

    static Typeface styled(Context context, int style) {
        Typeface base = customTypeface(context);
        if (base == null) {
            return style == Typeface.BOLD ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT;
        }
        return Typeface.create(base, style);
    }

    static Typeface bold(Context context) {
        return styled(context, Typeface.BOLD);
    }

    static void applyToViewTree(Context context, View view) {
        Typeface custom = customTypeface(context);
        if (custom == null || view == null) {
            return;
        }
        applyToViewTreeInternal(custom, view);
    }

    private static void applyToViewTreeInternal(Typeface custom, View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            Typeface current = textView.getTypeface();
            int style = current == null ? Typeface.NORMAL : current.getStyle();
            textView.setTypeface(Typeface.create(custom, style));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToViewTreeInternal(custom, group.getChildAt(i));
            }
        }
    }

    private static Typeface customTypeface(Context context) {
        File file = findFontFile();
        if (file == null) {
            cachedPath = null;
            cachedModified = 0L;
            cachedTypeface = null;
            return null;
        }
        String path = file.getAbsolutePath();
        long modified = file.lastModified();
        if (cachedTypeface != null && path.equals(cachedPath) && modified == cachedModified) {
            return cachedTypeface;
        }
        try {
            Typeface typeface = Typeface.createFromFile(file);
            cachedPath = path;
            cachedModified = modified;
            cachedTypeface = typeface;
            return typeface;
        } catch (Throwable ignored) {
            cachedPath = null;
            cachedModified = 0L;
            cachedTypeface = null;
            return null;
        }
    }

    private static File findFontFile() {
        File root = Environment.getExternalStorageDirectory();
        if (root == null) {
            return null;
        }
        for (String relativePath : FONT_PATHS) {
            File file = new File(root, relativePath);
            if (file.isFile() && file.length() > 0) {
                return file;
            }
        }
        return null;
    }
}
