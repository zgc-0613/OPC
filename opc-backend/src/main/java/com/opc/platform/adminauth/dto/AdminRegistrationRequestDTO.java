package com.opc.platform.adminauth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminRegistrationRequestDTO {

    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_]{3,30}$", message = "must contain only letters, numbers, or underscores")
    private String username;

    @NotBlank
    @Size(min = 8, max = 64)
    private String password;
}
