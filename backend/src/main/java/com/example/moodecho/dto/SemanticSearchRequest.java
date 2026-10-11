package com.example.moodecho.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 语义搜索请求参数。
 */
@Data
public class SemanticSearchRequest {

    @NotBlank(message = "请输入搜索内容")
    @jakarta.validation.constraints.Size(max = 500, message = "搜索内容不能超过500个字符")
    private String query;

    private Integer limit;
}
