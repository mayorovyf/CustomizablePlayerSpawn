package com.mayo.customizableplayerspawn.config;

import com.mayo.customizableplayerspawn.CustomizablePlayerSpawnCommon;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class SimpleTomlConfig {
    private final Map<String, Object> values;

    private SimpleTomlConfig(Map<String, Object> values) {
        this.values = values;
    }

    public static SimpleTomlConfig empty() {
        return new SimpleTomlConfig(Map.of());
    }

    public static SimpleTomlConfig load(Path path) {
        if (!Files.isRegularFile(path)) {
            return empty();
        }

        try {
            return parse(Files.readAllLines(path));
        } catch (IOException exception) {
            CustomizablePlayerSpawnCommon.LOGGER.error("Unable to read TOML config {}.", path, exception);
            return empty();
        }
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public Optional<Object> get(String path) {
        return Optional.ofNullable(values.get(path));
    }

    public static SimpleTomlConfig parse(List<String> lines) {
        Map<String, Object> values = new LinkedHashMap<>();
        String section = "";

        for (String rawLine : lines) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                continue;
            }

            int equalsIndex = indexOfEquals(line);
            if (equalsIndex < 0) {
                continue;
            }

            String key = line.substring(0, equalsIndex).trim();
            String value = line.substring(equalsIndex + 1).trim();
            if (key.isEmpty()) {
                continue;
            }

            String path = section.isEmpty() ? key : section + "." + key;
            values.put(path, parseValue(value));
        }

        return new SimpleTomlConfig(values);
    }

    private static int indexOfEquals(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            }

            if (current == '=' && !quoted) {
                return i;
            }
        }

        return -1;
    }

    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"' && (i == 0 || line.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            }

            if (current == '#' && !quoted) {
                return line.substring(0, i);
            }
        }

        return line;
    }

    private static Object parseValue(String value) {
        if (value.startsWith("[") && value.endsWith("]")) {
            return parseList(value.substring(1, value.length() - 1));
        }

        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
            return unquote(value);
        }

        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }

        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }

        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }

            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static List<Object> parseList(String value) {
        List<Object> result = new ArrayList<>();
        for (String entry : splitList(value)) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                result.add(parseValue(trimmed));
            }
        }
        return result;
    }

    private static List<String> splitList(String value) {
        List<String> result = new ArrayList<>();
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '"' && (i == 0 || value.charAt(i - 1) != '\\')) {
                quoted = !quoted;
            }

            if (current == ',' && !quoted) {
                result.add(value.substring(start, i));
                start = i + 1;
            }
        }
        result.add(value.substring(start));
        return result;
    }

    private static String unquote(String value) {
        return value.substring(1, value.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}

