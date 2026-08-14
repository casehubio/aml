package io.casehub.aml.compliance;

import java.util.regex.Pattern;

public final class AmlContentSanitiser {

    private static final Pattern IBAN_PATTERN =
            Pattern.compile("\\b[A-Z]{2}\\d{2}[A-Z0-9]{4,30}\\b");

    public String sanitise(String decisionContextJson) {
        if (decisionContextJson == null) return null;
        return IBAN_PATTERN.matcher(decisionContextJson)
                .replaceAll("[REDACTED:account]");
    }
}
