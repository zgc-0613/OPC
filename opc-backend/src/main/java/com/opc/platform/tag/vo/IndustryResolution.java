package com.opc.platform.tag.vo;

public record IndustryResolution(
        Long tagId,
        String name,
        String tagType,
        String method,
        double confidence,
        boolean requiresConfirmation
) {
    public static IndustryResolution unresolved() {
        return new IndustryResolution(null, null, null, "unresolved", 0, true);
    }
}
