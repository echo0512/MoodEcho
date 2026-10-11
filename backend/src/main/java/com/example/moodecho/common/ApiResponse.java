package com.example.moodecho.common;

import lombok.Data;
/**
 * 统一API响应封装类
 * 用于标准化接口返回数据结构，包含状态码、消息和数据体
 *
 * @param <T> 响应数据类型
 */
@Data
public class ApiResponse<T> {

    /**
     * 响应状态码
     * 200表示成功，400表示失败
     */
    private Integer code;

    /**
     * 响应消息描述
     */
    private String message;

    /**
     * 响应数据体
     */
    private T data;

    /**
     * 构建成功响应
     *
     * @param data 响应数据
     * @param <T> 数据类型
     * @return 状态码为200的成功响应对象
     */
    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(200);
        response.setMessage("成功");
        response.setData(data);
        return response;
    }

    /**
     * 构建失败响应
     *
     * @param message 错误消息描述
     * @param <T> 数据类型
     * @return 状态码为400的失败响应对象
     */
    public static <T> ApiResponse<T> fail(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(400);
        response.setMessage(message);
        response.setData(null);
        return response;
    }
}

