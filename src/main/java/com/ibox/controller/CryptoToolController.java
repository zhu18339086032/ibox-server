package com.ibox.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibox.model.req.web.CryptoDecryptReq;
import com.ibox.model.res.web.CryptoDecryptRes;
import com.ibox.model.res.web.WebResponse;
import com.ibox.utils.IboxCryptoUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

/**
 * 加解密调试工具控制器。
 */
@Slf4j
@RestController
@RequestMapping("/ibox/tool/crypto")
@Tag(name = "加解密工具")
public class CryptoToolController {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 解密 iBox 加密报文。
     */
    @PostMapping("/decrypt")
    @Operation(summary = "解密加密报文")
    public WebResponse<CryptoDecryptRes> decrypt(@RequestBody CryptoDecryptReq req) {
        validateReq(req);

        String plainText = StringUtils.hasText(req.getPrivateKey())
                ? IboxCryptoUtil.decryptResponse(req.getData(), req.getEncryptKey(), req.getPrivateKey())
                : IboxCryptoUtil.decryptResponse(req.getData(), req.getEncryptKey());

        CryptoDecryptRes res = new CryptoDecryptRes();
        res.setPlainText(plainText);

        try {
            Object json = OBJECT_MAPPER.readValue(plainText, Object.class);
            res.setJson(true);
            res.setJsonPretty(OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(json));
        } catch (JsonProcessingException e) {
            res.setJson(false);
            res.setJsonPretty(null);
        }
        return WebResponse.success(res);
    }

    private void validateReq(CryptoDecryptReq req) {
        if (req == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (!StringUtils.hasText(req.getData())) {
            throw new IllegalArgumentException("data 不能为空");
        }
        if (!StringUtils.hasText(req.getEncryptKey())) {
            throw new IllegalArgumentException("encryptKey 不能为空");
        }
    }
}
