package com.opc.platform.userauth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendEmailCodeDTO {

    @NotBlank
    @Email
    private String email;

    @Size(max = 16384)
    private String altcha;
}
