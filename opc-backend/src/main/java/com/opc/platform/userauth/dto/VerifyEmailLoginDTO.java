package com.opc.platform.userauth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyEmailLoginDTO {

    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 2, max = 30)
    private String username;

    @NotBlank
    @Pattern(regexp = "\\d{6}", message = "must be a 6-digit code")
    private String code;
}
