package com.ibox.model.res.web;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用Web响应对象
 *
 * @param <T> 响应数据类型
 */
@Data
public class WebResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 响应码
     */
    private Integer code;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    public static <T> WebResponse<T> success() {
        WebResponse<T> response = new WebResponse<>();
        response.setCode(ResponseCode.SUCCESS.getCode());
        response.setMessage(ResponseCode.SUCCESS.getMessage());
        return response;
    }

    public static <T> WebResponse<T> success(T data) {
        WebResponse<T> response = new WebResponse<>();
        response.setCode(ResponseCode.SUCCESS.getCode());
        response.setMessage(ResponseCode.SUCCESS.getMessage());
        response.setData(data);
        return response;
    }

    public static <T> WebResponse<T> success(String message, T data) {
        WebResponse<T> response = new WebResponse<>();
        response.setCode(ResponseCode.SUCCESS.getCode());
        response.setMessage(message);
        response.setData(data);
        return response;
    }

    public static <T> WebResponse<T> fail() {
        WebResponse<T> response = new WebResponse<>();
        response.setCode(ResponseCode.FAIL.getCode());
        response.setMessage(ResponseCode.FAIL.getMessage());
        return response;
    }

    public static <T> WebResponse<T> fail(String message) {
        WebResponse<T> response = new WebResponse<>();
        response.setCode(ResponseCode.FAIL.getCode());
        response.setMessage(message);
        return response;
    }

    public static <T> WebResponse<T> fail(Integer code, String message) {
        WebResponse<T> response = new WebResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }

    public static <T> WebResponse<T> fail(ResponseCode responseCode) {
        WebResponse<T> response = new WebResponse<>();
        response.setCode(responseCode.getCode());
        response.setMessage(responseCode.getMessage());
        return response;
    }
}
