package com.opc.platform.userauth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendEmailCodeDTO {

    @NotBlank
    @Email
    private String email;
}
