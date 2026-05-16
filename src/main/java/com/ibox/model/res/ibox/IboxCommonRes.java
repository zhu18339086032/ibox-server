package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * iBox通用响应模型
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class IboxCommonRes<T> {

    /**
     * 响应码
     */
    private Integer code;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 消息ID
     */
    private String msgId;

    /**
     * 时间戳
     */
    private Long ts;
}
