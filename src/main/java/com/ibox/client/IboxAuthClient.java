package com.ibox.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibox.constants.UrlConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * iBox明文认证接口客户端。
 * 登录接口不是业务加密接口，因此不能复用 IboxClient 的加密 POST。
 */
@Slf4j
@Component
public class IboxAuthClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 使用手机号和短信验证码登录。
     *
     * @return 登录响应中的 data 节点
     */
    public JsonNode login(String phoneNumber, String code) {
        Map<String, String> request = new HashMap<>();
        request.put("phoneNumber", phoneNumber);
        request.put("code", code);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setAcceptLanguageAsLocales(List.of(java.util.Locale.SIMPLIFIED_CHINESE, java.util.Locale.CHINA));
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        headers.set(HttpHeaders.CONNECTION, "keep-alive");
        headers.set(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate, br");
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Linux; Android 12; M2012K11AC Build/SKQ1.211006.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36 iBox/1.0");
        headers.set("Origin", "https://www.ibox.art");
        headers.set("Referer", "https://www.ibox.art/");
        headers.set("X-Requested-With", "com.ibox.app");
        headers.set("device-id", " dev_2f2958"+ UUID.randomUUID());
        headers.set("X-Request-Id", UUID.randomUUID().toString().replace("-", ""));
        try {
            String body = restTemplate.postForObject(
                    UrlConstant.LOGIN_VERIFY_URL,
                    new HttpEntity<>(request, headers),
                    String.class
            );
            if (body == null || body.isBlank()) {
                throw new IllegalStateException("iBox登录返回为空");
            }
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("code").isNumber() || root.path("code").asInt() != 0) {
                String message = root.path("message").asText(root.path("msg").asText("iBox登录失败"));
                throw new IllegalStateException(message);
            }
            JsonNode data = root.get("data");
            if (data == null || data.path("token").asText().isBlank() || data.path("refreshToken").asText().isBlank()) {
                throw new IllegalStateException("iBox登录响应缺少Token");
            }
            return data;
        } catch (RestClientException e) {
            throw new IllegalStateException("调用iBox登录接口失败", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException("解析iBox登录响应失败", e);
        }
    }
}
