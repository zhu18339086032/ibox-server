package com.ibox.client.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.ibox.client.AbstractIboxClient;
import com.ibox.model.res.ibox.IboxApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * iBox客户端默认实现
 */
@Slf4j
@Component
public class DefaultIboxClient extends AbstractIboxClient {

    @Override
    protected <T> IboxApiResponse<T> parseResponse(String plainJson, JavaType javaType) {
        try {
            return objectMapper.readValue(plainJson, javaType);
        } catch (JsonProcessingException e) {
            log.info("【iBox客户端】解析iBox接口响应失败:{}", e.getMessage());
            return null;
        }
    }
}
