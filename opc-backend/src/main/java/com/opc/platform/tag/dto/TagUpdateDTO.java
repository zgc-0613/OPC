package com.opc.platform.tag.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TagUpdateDTO {

    @NotBlank
    private String name;

    @NotBlank
    private String tagType;

    private Integer sortOrder;
}
