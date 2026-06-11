package com.ibox.model.req.web;

import lombok.Data;

/**
 * 解密工具请求。
 */
@Data
public class CryptoDecryptReq {

    /**
     * AES 加密后的报文。
     */
    private String data;

    /**
     * RSA 加密后的 AES key。
     */
    private String encryptKey;

    /**
     * 可选自定义私钥。
     * 不传时默认使用项目内置私钥。
     */
    private String privateKey;
}
