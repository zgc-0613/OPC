package com.opc.platform.ai.service;

import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.regex.Pattern;

public final class EvidenceUrlPolicy {

    private static final Pattern SAFE_URL = Pattern.compile(
            "^\\s*https?://[a-z0-9](?:[a-z0-9-]*[a-z0-9])?"
                    + "(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*(?:[/?#]\\S*)?\\s*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final String MYSQL_SAFE_URL_REGEX =
            "^[[:space:]]*https?://[a-z0-9]([a-z0-9-]*[a-z0-9])?"
                    + "([.][a-z0-9]([a-z0-9-]*[a-z0-9])?)*"
                    + "([/?#][^[:space:]]*)?[[:space:]]*$";

    private EvidenceUrlPolicy() {
    }

    public static boolean isSafe(String value) {
        if (!StringUtils.hasText(value) || !SAFE_URL.matcher(value).matches()) {
            return false;
        }
        try {
            URI uri = URI.create(value.trim());
            return uri.isAbsolute()
                    && uri.getUserInfo() == null
                    && StringUtils.hasText(uri.getHost())
                    && uri.getPort() == -1
                    && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static String sqlPredicate(String column) {
        return "LOWER(" + column + ") REGEXP '" + MYSQL_SAFE_URL_REGEX + "'";
    }
}
