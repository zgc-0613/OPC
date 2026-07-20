package com.opc.platform.userauth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordLoginDTO {

    @NotBlank
    @Size(max = 255)
    private String identifier;

    @NotBlank
    @Size(min = 8, max = 64)
    private String password;
}
