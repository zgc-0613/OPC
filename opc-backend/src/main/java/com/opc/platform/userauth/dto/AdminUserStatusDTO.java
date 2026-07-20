package com.opc.platform.userauth.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AdminUserStatusDTO {

    @Pattern(regexp = "active|disabled")
    private String status;
}
