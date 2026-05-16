package com.ibox.model.res.web;

/**
 * 通用响应状态码
 */
public enum ResponseCode {

    /**
     * 成功
     */
    SUCCESS(200, "操作成功"),

    /**
     * 失败
     */
    FAIL(500, "操作失败"),

    /**
     * 参数错误
     */
    BAD_REQUEST(400, "请求参数错误"),

    /**
     * 未授权
     */
    UNAUTHORIZED(401, "未授权访问"),

    /**
     * 无权限
     */
    FORBIDDEN(403, "没有访问权限"),

    /**
     * 资源不存在
     */
    NOT_FOUND(404, "资源不存在");

    private final Integer code;
    private final String message;

    ResponseCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
