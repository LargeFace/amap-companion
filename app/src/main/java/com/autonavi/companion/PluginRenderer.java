package com.autonavi.companion;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;

public final class PluginRenderer {
    private final Context context;
    private final PluginManifest manifest;
    private final float scale;
    private final ArrayList<Binding> bindings = new ArrayList<>();
    private static final String[] STATE_KEYS = new String[]{
            "mode", "roadName", "heading", "turnText", "turnDistance",
            "turnRoad", "turnIcon", "eta", "alert", "detail", "limitSpeed",
            "currentSpeed", "cameraType", "raw.keyType"
    };

    private PluginRenderer(Context context, PluginManifest manifest, float scale) {
        this.context = context;
        this.manifest = manifest;
        this.scale = scale <= 0f ? 1f : scale;
    }

    public static Result render(Context context, PluginManifest manifest, float scale, State initialState) throws Exception {
        return render(context, manifest, PluginManifest.CAP_UI, scale, initialState);
    }

    public static Result render(Context context, PluginManifest manifest, String capability,
                                float scale, State initialState) throws Exception {
        if (manifest == null || !manifest.hasCapability(capability)) {
            return null;
        }
        File uiFile = manifest.entryFile(capability);
        if (uiFile == null || !uiFile.isFile()) {
            String label = PluginManifest.CAP_OVERLAY_STYLE.equals(capability) ? "悬浮窗样式插件" : "全局界面插件";
            throw new IllegalArgumentException(label + "缺少入口文件");
        }
        PluginRenderer renderer = new PluginRenderer(context, manifest, scale);
        JSONObject rootJson = new JSONObject(readText(uiFile));
        JSONObject rootComponent = rootJson.optJSONObject("root");
        if (rootComponent == null) {
            rootComponent = rootJson;
        }
        View rootView = renderer.build(rootComponent);
        if (!(rootView instanceof LinearLayout)) {
            LinearLayout wrapper = new LinearLayout(context);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.setGravity(Gravity.CENTER);
            wrapper.addView(rootView);
            rootView = wrapper;
        }
        Result result = renderer.result;
        result.root = (LinearLayout) rootView;
        result.renderer = renderer;
        renderer.refresh(initialState);
        return result;
    }

    private final Result result = new Result();

    public void refresh(State state) {
        if (state == null) {
            state = State.EMPTY;
        }
        for (Binding binding : bindings) {
            binding.apply(state);
        }
    }

    private View build(JSONObject object) {
        String type = object.optString("type", "column");
        if ("row".equals(type) || "column".equals(type)) {
            return buildContainer(object, "row".equals(type));
        }
        if ("text".equals(type) || "badge".equals(type)) {
            return buildText(object, "badge".equals(type));
        }
        if ("image".equals(type)) {
            return buildImage(object);
        }
        if ("turnIcon".equals(type)) {
            ImageView view = new ImageView(context);
            view.setScaleType(ImageView.ScaleType.FIT_CENTER);
            result.turnIconView = view;
            applyLayout(view, object);
            addVisibilityBinding(view, object);
            return view;
        }
        if ("laneBar".equals(type)) {
            LinearLayout section = new LinearLayout(context);
            section.setOrientation(LinearLayout.VERTICAL);
            section.setGravity(Gravity.CENTER);
            LaneBarView lane = new LaneBarView(context);
            lane.setShowBackground(object.optBoolean("background", true));
            lane.setShowDividers(object.optBoolean("dividers", true));
            section.addView(lane, new LinearLayout.LayoutParams(-2, -2));
            result.laneSection = section;
            result.laneBar = lane;
            applyLayout(section, object);
            addVisibilityBinding(section, object);
            return section;
        }
        if ("trafficLights".equals(type)) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            result.lightRow = row;
            applyLayout(row, object);
            addVisibilityBinding(row, object);
            return row;
        }
        if ("edog".equals(type)) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            TextView text = new TextView(context);
            text.setTextColor(color(object, "textColor", 0xFFE8EAED));
            text.setTextSize(sp(object.optDouble("textSize", 12)));
            row.addView(text, new LinearLayout.LayoutParams(-2, -2));
            result.alertRow = row;
            result.alertText = text;
            applyLayout(row, object);
            addVisibilityBinding(row, object);
            return row;
        }
        if ("spacer".equals(type)) {
            View spacer = new View(context);
            applyLayout(spacer, object);
            addVisibilityBinding(spacer, object);
            return spacer;
        }
        TextView unsupported = new TextView(context);
        unsupported.setText("");
        applyLayout(unsupported, object);
        return unsupported;
    }

    private View buildContainer(JSONObject object, boolean horizontal) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        layout.setGravity(gravity(object.optString("gravity", horizontal ? "center_vertical" : "center")));
        styleBackground(layout, object);
        applyPadding(layout, object);
        JSONArray children = object.optJSONArray("children");
        if (children != null) {
            for (int i = 0; i < children.length(); i++) {
                JSONObject child = children.optJSONObject(i);
                if (child == null) {
                    continue;
                }
                View childView = build(child);
                LinearLayout.LayoutParams lp = childLayoutParams(child);
                layout.addView(childView, lp);
            }
        }
        applyLayout(layout, object);
        addVisibilityBinding(layout, object);
        return layout;
    }

    private View buildText(JSONObject object, boolean badge) {
        TextView text = new TextView(context);
        text.setText(object.optString("text", ""));
        text.setTextColor(color(object, "textColor", 0xFFE8EAED));
        text.setTextSize(sp(object.optDouble("textSize", badge ? 12 : 14)));
        text.setGravity(gravity(object.optString("gravity", "center")));
        text.setSingleLine(object.optBoolean("singleLine", false));
        if (object.optBoolean("bold", badge)) {
            text.setTypeface(Typeface.DEFAULT_BOLD);
        }
        if (badge) {
            styleBackground(text, object, 0x332563EB, 999);
            applyPadding(text, object, 8, 3, 8, 3);
        } else {
            styleBackground(text, object);
            applyPadding(text, object);
        }
        String bind = object.optString("bind", "");
        VisibilityRule visibilityRule = visibilityRule(object);
        if (!TextUtils.isEmpty(bind) || text.getText().toString().contains("{")) {
            bindings.add(new TextBinding(text, bind, text.getText().toString(),
                    visibilityRule, object.optBoolean("hideWhenEmpty", true)));
        } else {
            addVisibilityBinding(text, visibilityRule);
        }
        String ref = object.optString("ref", "");
        if ("mode".equals(ref) || "mode".equals(bind)) result.modeText = text;
        if ("turn".equals(ref) || "turnText".equals(bind)) result.turnText = text;
        if ("turnDistance".equals(ref) || "turnDistance".equals(bind)) result.turnDistanceText = text;
        if ("eta".equals(ref) || "eta".equals(bind)) result.etaText = text;
        if ("detail".equals(ref) || "detail".equals(bind)) result.detailText = text;
        applyLayout(text, object);
        return text;
    }

    private View buildImage(JSONObject object) {
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        String name = object.optString("name", object.optString("src", ""));
        File file = null;
        if (manifest.directory != null) {
            try {
                PluginManifest.validateRelativePath(name);
                file = new File(manifest.directory, name);
            } catch (Throwable ignored) {
            }
        }
        if (file == null || !file.isFile()) {
            file = PluginAssets.activeImageFile(context, name);
        }
        if (file != null && file.isFile()) {
            Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(file.getAbsolutePath());
            if (bitmap != null) {
                image.setImageBitmap(bitmap);
            }
        }
        applyLayout(image, object);
        addVisibilityBinding(image, object);
        return image;
    }

    private LinearLayout.LayoutParams childLayoutParams(JSONObject object) {
        int width = size(object, "width", -2);
        int height = size(object, "height", -2);
        float weight = (float) object.optDouble("weight", 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height, weight);
        int margin = dp(object.optDouble("margin", 0));
        lp.setMargins(
                dp(object.optDouble("marginLeft", margin)),
                dp(object.optDouble("marginTop", margin)),
                dp(object.optDouble("marginRight", margin)),
                dp(object.optDouble("marginBottom", margin)));
        return lp;
    }

    private void applyLayout(View view, JSONObject object) {
        view.setVisibility(object.optBoolean("visible", true) ? View.VISIBLE : View.GONE);
        if (view.getLayoutParams() == null) {
            view.setLayoutParams(childLayoutParams(object));
        }
        if (object.has("minWidth")) {
            view.setMinimumWidth(dp(object.optDouble("minWidth", 0)));
        }
        if (object.has("minHeight")) {
            view.setMinimumHeight(dp(object.optDouble("minHeight", 0)));
        }
    }

    private void applyPadding(View view, JSONObject object) {
        if (!object.has("padding")
                && !object.has("paddingLeft")
                && !object.has("paddingTop")
                && !object.has("paddingRight")
                && !object.has("paddingBottom")) {
            return;
        }
        double padding = object.optDouble("padding", 0);
        applyPadding(view, object,
                object.has("paddingLeft") ? object.optDouble("paddingLeft", 0) : padding,
                object.has("paddingTop") ? object.optDouble("paddingTop", 0) : padding,
                object.has("paddingRight") ? object.optDouble("paddingRight", 0) : padding,
                object.has("paddingBottom") ? object.optDouble("paddingBottom", 0) : padding);
    }

    private void applyPadding(View view, JSONObject object, double left, double top, double right, double bottom) {
        view.setPadding(dp(left), dp(top), dp(right), dp(bottom));
    }

    private void styleBackground(View view, JSONObject object) {
        if (!object.has("background") && !object.has("strokeColor")) {
            return;
        }
        styleBackground(view, object, color(object, "background", 0x00000000),
                (float) object.optDouble("cornerRadius", 0));
    }

    private void styleBackground(View view, JSONObject object, int fallbackColor, float fallbackRadius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color(object, "background", fallbackColor));
        bg.setCornerRadius(dp(object.optDouble("cornerRadius", fallbackRadius)));
        if (object.has("strokeColor")) {
            bg.setStroke(dp(object.optDouble("strokeWidth", 1)), color(object, "strokeColor", 0x33FFFFFF));
        }
        view.setBackground(bg);
    }

    private int size(JSONObject object, String key, int fallback) {
        if (!object.has(key)) {
            return fallback;
        }
        Object value = object.opt(key);
        if (value instanceof String) {
            String text = (String) value;
            if ("match".equals(text)) return -1;
            if ("wrap".equals(text)) return -2;
        }
        return dp(object.optDouble(key, fallback));
    }

    private int dp(double value) {
        if (value == -1 || value == -2) {
            return (int) value;
        }
        return Math.round((float) value * scale * context.getResources().getDisplayMetrics().density);
    }

    private float sp(double value) {
        return (float) value * scale;
    }

    private int color(JSONObject object, String key, int fallback) {
        String value = object.optString(key, "");
        if (TextUtils.isEmpty(value)) {
            return fallback;
        }
        try {
            return Color.parseColor(value);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private int gravity(String value) {
        if ("left".equals(value) || "start".equals(value)) return Gravity.LEFT | Gravity.CENTER_VERTICAL;
        if ("right".equals(value) || "end".equals(value)) return Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        if ("top".equals(value)) return Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        if ("bottom".equals(value)) return Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        if ("center_vertical".equals(value)) return Gravity.CENTER_VERTICAL;
        if ("center_horizontal".equals(value)) return Gravity.CENTER_HORIZONTAL;
        return Gravity.CENTER;
    }

    private static String readText(File file) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } finally {
            input.close();
        }
        return out.toString("UTF-8");
    }

    private VisibilityRule visibilityRule(JSONObject object) {
        JSONObject visibleEquals = object.optJSONObject("visibleEquals");
        JSONObject visibleNotEquals = object.optJSONObject("visibleNotEquals");
        return new VisibilityRule(
                object.optBoolean("visible", true),
                object.optString("visibleWhen", object.optString("showWhen", "")).trim(),
                object.optString("hiddenWhen", object.optString("hideWhen", "")).trim(),
                conditionField(visibleEquals),
                conditionValue(visibleEquals),
                conditionField(visibleNotEquals),
                conditionValue(visibleNotEquals));
    }

    private void addVisibilityBinding(View view, JSONObject object) {
        addVisibilityBinding(view, visibilityRule(object));
    }

    private void addVisibilityBinding(View view, VisibilityRule rule) {
        if (rule.dynamic()) {
            bindings.add(new VisibilityBinding(view, rule));
        }
    }

    private static String conditionField(JSONObject object) {
        if (object == null) {
            return "";
        }
        return object.optString("field",
                object.optString("bind", object.optString("key", ""))).trim();
    }

    private static String conditionValue(JSONObject object) {
        if (object == null) {
            return "";
        }
        return object.optString("value", "").trim();
    }

    public static final class Result {
        public LinearLayout root;
        public PluginRenderer renderer;
        public TextView modeText;
        public TextView turnText;
        public TextView turnDistanceText;
        public ImageView turnIconView;
        public LinearLayout laneSection;
        public LaneBarView laneBar;
        public LinearLayout lightRow;
        public TextView etaText;
        public LinearLayout alertRow;
        public TextView alertText;
        public TextView detailText;
    }

    public static final class State {
        static final State EMPTY = new State("", "", "", "", "", "", 0, "", "", "",
                -1, -1, -1, -1);

        public final String mode;
        public final String roadName;
        public final String heading;
        public final String turnText;
        public final String turnDistance;
        public final String turnRoad;
        public final int turnIcon;
        public final String eta;
        public final String alert;
        public final String detail;
        public final int limitSpeed;
        public final int currentSpeed;
        public final int cameraType;
        public final int rawKeyType;

        public State(String mode, String roadName, String heading, String turnText,
                     String turnDistance, String turnRoad, int turnIcon, String eta,
                     String alert, String detail, int limitSpeed, int currentSpeed,
                     int cameraType, int rawKeyType) {
            this.mode = mode;
            this.roadName = roadName;
            this.heading = heading;
            this.turnText = turnText;
            this.turnDistance = turnDistance;
            this.turnRoad = turnRoad;
            this.turnIcon = turnIcon;
            this.eta = eta;
            this.alert = alert;
            this.detail = detail;
            this.limitSpeed = limitSpeed;
            this.currentSpeed = currentSpeed;
            this.cameraType = cameraType;
            this.rawKeyType = rawKeyType;
        }

        String value(String key) {
            if ("mode".equals(key)) return mode;
            if ("roadName".equals(key)) return roadName;
            if ("heading".equals(key)) return heading;
            if ("turnText".equals(key)) return turnText;
            if ("turnDistance".equals(key)) return turnDistance;
            if ("turnRoad".equals(key)) return turnRoad;
            if ("turnIcon".equals(key)) return turnIcon > 0 ? String.valueOf(turnIcon) : "";
            if ("eta".equals(key)) return eta;
            if ("alert".equals(key)) return alert;
            if ("detail".equals(key)) return detail;
            if ("limitSpeed".equals(key)) return limitSpeed > 0 ? String.valueOf(limitSpeed) : "";
            if ("currentSpeed".equals(key)) return currentSpeed >= 0 ? String.valueOf(currentSpeed) : "";
            if ("cameraType".equals(key)) return cameraType >= 0 ? String.valueOf(cameraType) : "";
            if ("raw.keyType".equals(key)) return rawKeyType >= 0 ? String.valueOf(rawKeyType) : "";
            return "";
        }
    }

    private interface Binding {
        void apply(State state);
    }

    private static final class VisibilityRule {
        private final boolean baseVisible;
        private final String visibleWhen;
        private final String hiddenWhen;
        private final String equalsField;
        private final String equalsValue;
        private final String notEqualsField;
        private final String notEqualsValue;

        VisibilityRule(boolean baseVisible, String visibleWhen, String hiddenWhen,
                       String equalsField, String equalsValue,
                       String notEqualsField, String notEqualsValue) {
            this.baseVisible = baseVisible;
            this.visibleWhen = visibleWhen;
            this.hiddenWhen = hiddenWhen;
            this.equalsField = equalsField;
            this.equalsValue = equalsValue;
            this.notEqualsField = notEqualsField;
            this.notEqualsValue = notEqualsValue;
        }

        boolean dynamic() {
            return !TextUtils.isEmpty(visibleWhen)
                    || !TextUtils.isEmpty(hiddenWhen)
                    || !TextUtils.isEmpty(equalsField)
                    || !TextUtils.isEmpty(notEqualsField);
        }

        boolean isVisible(State state) {
            if (!baseVisible) {
                return false;
            }
            if (!TextUtils.isEmpty(visibleWhen) && TextUtils.isEmpty(state.value(visibleWhen))) {
                return false;
            }
            if (!TextUtils.isEmpty(hiddenWhen) && !TextUtils.isEmpty(state.value(hiddenWhen))) {
                return false;
            }
            if (!TextUtils.isEmpty(equalsField) && !equalsValue.equals(state.value(equalsField))) {
                return false;
            }
            return TextUtils.isEmpty(notEqualsField) || !notEqualsValue.equals(state.value(notEqualsField));
        }
    }

    private static final class VisibilityBinding implements Binding {
        private final View view;
        private final VisibilityRule rule;

        VisibilityBinding(View view, VisibilityRule rule) {
            this.view = view;
            this.rule = rule;
        }

        @Override
        public void apply(State state) {
            view.setVisibility(rule.isVisible(state) ? View.VISIBLE : View.GONE);
        }
    }

    private static final class TextBinding implements Binding {
        private final TextView view;
        private final String bind;
        private final String template;
        private final VisibilityRule visibilityRule;
        private final boolean hideWhenEmpty;

        TextBinding(TextView view, String bind, String template,
                    VisibilityRule visibilityRule, boolean hideWhenEmpty) {
            this.view = view;
            this.bind = bind;
            this.template = template;
            this.visibilityRule = visibilityRule;
            this.hideWhenEmpty = hideWhenEmpty;
        }

        @Override
        public void apply(State state) {
            String text;
            if (!TextUtils.isEmpty(bind)) {
                text = state.value(bind);
            } else {
                text = template;
                for (String key : STATE_KEYS) {
                    text = text.replace("{" + key + "}", state.value(key));
                }
            }
            view.setText(text);
            boolean visible = visibilityRule.isVisible(state)
                    && (!hideWhenEmpty || !TextUtils.isEmpty(text));
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }
}
