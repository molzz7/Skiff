package com.skiff.gateway.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsDto {

    /** n1n API Key */
    private String apiKey;

    /** API 地址 */
    private String baseUrl;

    /** 用户选择的模型列表 */
    private List<String> models = new ArrayList<>();
}
