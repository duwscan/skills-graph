package com.sk.skillsgraph.util;

import com.sk.skillsgraph.config.AppConstants;
import java.text.Normalizer;
import java.util.Locale;

public final class SlugUtils {

    private SlugUtils() {
    }

    public static String generateSlug(String name) {
        if (name == null || name.isBlank()) {
            return "skill";
        }

        String normalized = Normalizer.normalize(name, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s-]", " ")
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-");

        if (normalized.isBlank()) {
            normalized = "skill";
        }
        if (normalized.length() > AppConstants.SLUG_MAX_LENGTH) {
            normalized = normalized.substring(0, AppConstants.SLUG_MAX_LENGTH);
            normalized = normalized.replaceAll("-+$", "");
        }
        return normalized.isBlank() ? "skill" : normalized;
    }
}
