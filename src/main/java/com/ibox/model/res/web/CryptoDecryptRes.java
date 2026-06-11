package com.ibox.model.res.web;

import lombok.Data;

/**
 * 解密工具响应。
 */
@Data
public class CryptoDecryptRes {

    /**
     * 解密后的原始明文。
     */
    private String plainText;

    /**
     * 如果明文是 JSON，则返回格式化后的文本。
     */
    private String jsonPretty;

    /**
     * 明文是否为合法 JSON。
     */
    private boolean json;
}
