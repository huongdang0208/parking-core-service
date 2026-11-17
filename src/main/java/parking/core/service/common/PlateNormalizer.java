package parking.core.service.common;

import java.util.Locale;

public final class PlateNormalizer {
    private PlateNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
