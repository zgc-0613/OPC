package com.opc.platform.ai.tool;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GetSourceArguments {

    @NotNull
    private Long sourceId;
}
