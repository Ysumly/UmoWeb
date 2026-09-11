package com.ysumly.umowebbackend.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "旧密码不能为空")
    @Size(max = 200, message = "旧密码长度不能超过 200")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, message = "新密码至少 6 位")
    @Size(max = 200, message = "新密码长度不能超过 200")
    private String newPassword;
}
