package com.autonavi.companion;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * Parses AMap service-area broadcasts into UI-independent display rows.
 *
 * AMap Auto commonly exposes the nearest service area through SAPA_* and the
 * next one through NEXT_SAPA_*. Some builds may emit arrays or JSON payloads,
 * so this parser accepts both single and multi-service-area forms.
 */
public final class ServiceAreaParser {
    private static final String TAG = "AmapCompanion";

    private static final String[] PAYLOAD_KEYS = {
            "SAPA_DIST", "SAPA_DIST_AUTO", "SAPA_NAME", "SAPA_TYPE", "SAPA_NUM",
            "NEXT_SAPA_DIST", "NEXT_SAPA_DIST_AUTO", "NEXT_SAPA_NAME", "NEXT_SAPA_TYPE",
            "SAPA_NAMES", "SAPA_NAME_LIST", "SAPA_NAME_ARRAY", "sapaNames",
            "SAPA_DISTS", "SAPA_DIST_LIST", "SAPA_DIST_ARRAY", "sapaDistances",
            "SAPA_DIST_AUTOS", "SAPA_DIST_AUTO_LIST", "sapaDistanceTexts",
            "SAPA_LIST", "SAPA_INFO", "SAPA_INFOS", "sapaList", "sapaInfo",
            "serviceAreas", "serviceAreaInfos", "serviceAreaList"
    };

    private static final String[] LIST_KEYS = {
            "SAPA_LIST", "SAPA_INFO", "SAPA_INFOS", "sapaList", "sapaInfo",
            "serviceAreas", "serviceAreaInfos", "serviceAreaList"
    };

    private static final String[] NAME_ARRAY_KEYS = {
            "SAPA_NAMES", "SAPA_NAME_LIST", "SAPA_NAME_ARRAY", "sapaNames",
            "serviceAreaNames"
    };

    private static final String[] DIST_ARRAY_KEYS = {
            "SAPA_DISTS", "SAPA_DIST_LIST", "SAPA_DIST_ARRAY", "sapaDistances",
            "serviceAreaDistances"
    };

    private static final String[] DIST_TEXT_ARRAY_KEYS = {
            "SAPA_DIST_AUTOS", "SAPA_DIST_AUTO_LIST", "sapaDistanceTexts",
            "serviceAreaDistanceTexts"
    };

    private ServiceAreaParser() {
    }

    public static Result parse(Bundle extras) {
        if (extras == null || !hasAny(extras, PAYLOAD_KEYS)) {
            return Result.notHandled();
        }

        ArrayList<Entry> entries = new ArrayList<>();
        parseListPayloads(extras, entries);
        parseArrayPayloads(extras, entries);
        addEntry(entries,
                valueString(extras, "SAPA_NAME"),
                intValue(extras, "SAPA_DIST", -1),
                valueString(extras, "SAPA_DIST_AUTO"));
        addEntry(entries,
                valueString(extras, "NEXT_SAPA_NAME"),
                intValue(extras, "NEXT_SAPA_DIST", -1),
                valueString(extras, "NEXT_SAPA_DIST_AUTO"));

        return new Result(true, dedupe(entries));
    }

    private static void parseListPayloads(Bundle extras, ArrayList<Entry> entries) {
        for (String key : LIST_KEYS) {
            parseListValue(safeExtra(extras, key), entries);
        }
    }

    private static void parseListValue(Object value, ArrayList<Entry> entries) {
        if (value == null) {
            return;
        }
        if (value instanceof Bundle) {
            Bundle bundle = (Bundle) value;
            addEntry(entries,
                    valueString(bundle, "name", "sapaName", "SAPA_NAME", "serviceAreaName"),
                    intValue(bundle, "dist", intValue(bundle, "distance", intValue(bundle, "SAPA_DIST", -1))),
                    valueString(bundle, "distAuto", "distanceText", "SAPA_DIST_AUTO"));
            for (String key : bundle.keySet()) {
                parseListValue(safeExtra(bundle, key), entries);
            }
            return;
        }
        Class<?> cls = value.getClass();
        if (cls.isArray()) {
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                parseListValue(Array.get(value, i), entries);
            }
            return;
        }
        String text = String.valueOf(value);
        if (TextUtils.isEmpty(text) || "null".equals(text)) {
            return;
        }
        parseJsonText(text, entries);
    }

    private static void parseJsonText(String text, ArrayList<Entry> entries) {
        String trimmed = text.trim();
        if (TextUtils.isEmpty(trimmed) || (!trimmed.startsWith("[") && !trimmed.startsWith("{"))) {
            return;
        }
        try {
            if (trimmed.startsWith("[")) {
                JSONArray array = new JSONArray(trimmed);
                for (int i = 0; i < array.length(); i++) {
                    parseJsonValue(array.opt(i), entries);
                }
            } else {
                parseJsonObject(new JSONObject(trimmed), entries);
            }
        } catch (Throwable t) {
            Log.d(TAG, "parse service area json failed: " + text, t);
        }
    }

    private static void parseJsonValue(Object value, ArrayList<Entry> entries) {
        if (value instanceof JSONObject) {
            parseJsonObject((JSONObject) value, entries);
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                parseJsonValue(array.opt(i), entries);
            }
        }
    }

    private static void parseJsonObject(JSONObject object, ArrayList<Entry> entries) {
        JSONArray array = object.optJSONArray("serviceAreas");
        if (array == null) {
            array = object.optJSONArray("sapaList");
        }
        if (array == null) {
            array = object.optJSONArray("sapaInfos");
        }
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                parseJsonValue(array.opt(i), entries);
            }
        }
        addEntry(entries,
                firstString(object, "name", "sapaName", "SAPA_NAME", "serviceAreaName"),
                firstInt(object, -1, "dist", "distance", "SAPA_DIST", "sapaDist"),
                firstString(object, "distAuto", "distanceText", "SAPA_DIST_AUTO", "sapaDistAuto"));
    }

    private static void parseArrayPayloads(Bundle extras, ArrayList<Entry> entries) {
        String[] names = stringArrayValue(extras, NAME_ARRAY_KEYS);
        int[] dists = intArrayValue(extras, DIST_ARRAY_KEYS);
        String[] distTexts = stringArrayValue(extras, DIST_TEXT_ARRAY_KEYS);
        int count = Math.max(lengthOf(names), Math.max(lengthOf(dists), lengthOf(distTexts)));
        for (int i = 0; i < count; i++) {
            addEntry(entries, valueAt(names, i, null), valueAt(dists, i, -1), valueAt(distTexts, i, null));
        }
    }

    private static void addEntry(ArrayList<Entry> entries, String name, int meters, String distanceText) {
        name = cleanName(name);
        distanceText = cleanDistanceText(distanceText);
        boolean hasDistance = isPositiveDistanceText(distanceText) || meters > 0;
        if (TextUtils.isEmpty(name) && !hasDistance) {
            return;
        }
        if (TextUtils.isEmpty(distanceText) && meters > 0) {
            distanceText = formatDistance(meters);
        }
        entries.add(new Entry(name, distanceText));
    }

    private static List<Entry> dedupe(ArrayList<Entry> entries) {
        ArrayList<Entry> result = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Entry entry : entries) {
            String key = (entry.name == null ? "" : entry.name) + "|" + (entry.distanceText == null ? "" : entry.distanceText);
            if (seen.add(key)) {
                result.add(entry);
            }
        }
        return result;
    }

    private static String cleanName(String name) {
        if (TextUtils.isEmpty(name) || "null".equals(name) || "0".equals(name)) {
            return null;
        }
        return name.trim();
    }

    private static String cleanDistanceText(String text) {
        if (TextUtils.isEmpty(text) || "null".equals(text) || "0".equals(text) || "0米".equals(text)) {
            return null;
        }
        return text.trim();
    }

    private static boolean isPositiveDistanceText(String text) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        String digits = text.replaceAll("[^0-9]", "");
        if (TextUtils.isEmpty(digits)) {
            return true;
        }
        return parseInt(digits, 0) > 0;
    }

    private static String formatDistance(int meters) {
        if (meters >= 1000) {
            return String.format(Locale.US, "%.1fkm", meters / 1000f);
        }
        return meters + "m";
    }

    private static Object safeExtra(Bundle extras, String key) {
        try {
            return extras.get(key);
        } catch (Throwable t) {
            Log.d(TAG, "skip unreadable service-area extra " + key, t);
            return null;
        }
    }

    private static boolean hasAny(Bundle extras, String... keys) {
        for (String key : keys) {
            if (extras.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    private static String valueString(Bundle extras, String... keys) {
        for (String key : keys) {
            Object value = safeExtra(extras, key);
            if (value == null) {
                continue;
            }
            String s = String.valueOf(value);
            if (!TextUtils.isEmpty(s) && !"0".equals(s) && !"null".equals(s)) {
                return s;
            }
        }
        return null;
    }

    private static String firstString(JSONObject object, String... keys) {
        for (String key : keys) {
            String value = object.optString(key, null);
            if (!TextUtils.isEmpty(value) && !"null".equals(value) && !"0".equals(value)) {
                return value;
            }
        }
        return null;
    }

    private static int firstInt(JSONObject object, int fallback, String... keys) {
        for (String key : keys) {
            if (object.has(key)) {
                return object.optInt(key, fallback);
            }
        }
        return fallback;
    }

    private static int intValue(Bundle extras, String key, int fallback) {
        Object value = safeExtra(extras, key);
        if (value == null) {
            return fallback;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return parseInt(String.valueOf(value), fallback);
    }

    private static String[] stringArrayValue(Bundle extras, String... keys) {
        for (String key : keys) {
            String[] parsed = parseStringArray(safeExtra(extras, key));
            if (parsed != null && parsed.length > 0) {
                return parsed;
            }
        }
        return null;
    }

    private static int[] intArrayValue(Bundle extras, String... keys) {
        for (String key : keys) {
            int[] parsed = parseIntArray(safeExtra(extras, key));
            if (parsed != null && parsed.length > 0) {
                return parsed;
            }
        }
        return null;
    }

    private static String[] parseStringArray(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String[]) {
            return (String[]) value;
        }
        Class<?> cls = value.getClass();
        if (cls.isArray()) {
            int length = Array.getLength(value);
            String[] out = new String[length];
            for (int i = 0; i < length; i++) {
                Object item = Array.get(value, i);
                out[i] = item == null ? null : String.valueOf(item);
            }
            return out;
        }
        String s = String.valueOf(value).trim();
        if (TextUtils.isEmpty(s) || s.startsWith("{") || s.startsWith("[")) {
            return null;
        }
        return s.split("[,;|]+");
    }

    private static int[] parseIntArray(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof int[]) {
            return (int[]) value;
        }
        Class<?> cls = value.getClass();
        if (cls.isArray()) {
            int length = Array.getLength(value);
            int[] out = new int[length];
            for (int i = 0; i < length; i++) {
                Object item = Array.get(value, i);
                out[i] = item instanceof Number ? ((Number) item).intValue() : parseInt(String.valueOf(item), -1);
            }
            return out;
        }
        String s = String.valueOf(value).replace('[', ' ').replace(']', ' ').trim();
        if (TextUtils.isEmpty(s)) {
            return null;
        }
        String[] parts = s.split("[,;| ]+");
        int[] out = new int[parts.length];
        int count = 0;
        for (String part : parts) {
            if (!TextUtils.isEmpty(part)) {
                out[count++] = parseInt(part, -1);
            }
        }
        if (count == out.length) {
            return out;
        }
        int[] compact = new int[count];
        System.arraycopy(out, 0, compact, 0, count);
        return compact;
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int lengthOf(Object[] values) {
        return values == null ? 0 : values.length;
    }

    private static int lengthOf(int[] values) {
        return values == null ? 0 : values.length;
    }

    private static String valueAt(String[] values, int index, String fallback) {
        if (values == null || values.length == 0) {
            return fallback;
        }
        return index < values.length ? values[index] : values[values.length - 1];
    }

    private static int valueAt(int[] values, int index, int fallback) {
        if (values == null || values.length == 0) {
            return fallback;
        }
        return index < values.length ? values[index] : values[values.length - 1];
    }

    public static final class Entry {
        public final String name;
        public final String distanceText;

        Entry(String name, String distanceText) {
            this.name = name;
            this.distanceText = distanceText;
        }

        public String displayText() {
            if (TextUtils.isEmpty(name)) {
                return distanceText == null ? "" : distanceText;
            }
            if (TextUtils.isEmpty(distanceText)) {
                return name;
            }
            return name + " " + distanceText;
        }
    }

    public static final class Result {
        public final boolean handled;
        public final List<Entry> entries;

        private Result(boolean handled, List<Entry> entries) {
            this.handled = handled;
            this.entries = entries;
        }

        static Result notHandled() {
            return new Result(false, new ArrayList<>());
        }

        public boolean hasEntries() {
            return entries != null && !entries.isEmpty();
        }
    }
}
