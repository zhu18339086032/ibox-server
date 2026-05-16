package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * iBox接口通用响应
 *
 * @param <T> 数据类型
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class IboxApiResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
