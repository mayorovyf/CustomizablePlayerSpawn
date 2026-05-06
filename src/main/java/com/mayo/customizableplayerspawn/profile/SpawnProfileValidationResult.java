package com.mayo.customizableplayerspawn.profile;

import java.util.List;

public record SpawnProfileValidationResult(boolean valid, List<String> errors, List<String> warnings) {
    public static SpawnProfileValidationResult valid(List<String> warnings) {
        return new SpawnProfileValidationResult(true, List.of(), List.copyOf(warnings));
    }

    public static SpawnProfileValidationResult invalid(List<String> errors, List<String> warnings) {
        return new SpawnProfileValidationResult(false, List.copyOf(errors), List.copyOf(warnings));
    }
}
