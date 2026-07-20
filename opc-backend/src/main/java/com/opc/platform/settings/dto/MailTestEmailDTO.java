package com.opc.platform.settings.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MailTestEmailDTO extends MailSettingsUpdateDTO {

    @NotBlank
    @Email
    private String recipient;
}
