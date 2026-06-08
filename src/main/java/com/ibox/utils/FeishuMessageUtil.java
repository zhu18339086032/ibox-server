package com.ibox.utils;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 飞书消息工具类。
 */
@Slf4j
@Component
public class FeishuMessageUtil {

    private static final String FEISHU_WEBHOOK_URL = "https://open.feishu.cn/open-apis/bot/v2/hook/520f8052-58c8-49db-bd53-71fe4330a535";
    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final long RETRY_SLEEP_MILLIS = 300L;
    private static final Map<String, Long> TEXT_SEND_CACHE = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate;

    public FeishuMessageUtil() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1000);
        requestFactory.setReadTimeout(1000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public void sendText(String text) {
        Map<String, Object> content = new HashMap<>();
        content.put("text", text);

        Map<String, Object> body = new HashMap<>();
        body.put("msg_type", "text");
        body.put("content", content);
        postMessage(body, JSONObject.toJSONString(text));
    }

    public void sendText(String text, String key, int minutes) {
        if (minutes <= 0 || key == null || key.isBlank()) {
            sendText(text);
            return;
        }

        long now = System.currentTimeMillis();
        long windowMillis = minutes * 60L * 1000L;
        Long lastSendTime = TEXT_SEND_CACHE.get(key);
        if (lastSendTime != null && now - lastSendTime < windowMillis) {
            log.info("【飞书通知】命中去重时间窗，跳过发送，key:{}, minutes:{}, remainMs:{}", key, minutes, windowMillis - (now - lastSendTime));
            return;
        }

        TEXT_SEND_CACHE.put(key, now);
        sendText(text);
    }

    public void sendCard(Map<String, Object> card) {
        Map<String, Object> body = new HashMap<>();
        body.put("msg_type", "interactive");
        body.put("card", card);
        postMessage(body, JSONObject.toJSONString(card));
    }

    public void sendCard(Map<String, Object> card, String key, int minutes) {
        if (minutes <= 0 || key == null || key.isBlank()) {
            sendCard(card);
            return;
        }

        long now = System.currentTimeMillis();
        long windowMillis = minutes * 60L * 1000L;
        Long lastSendTime = TEXT_SEND_CACHE.get(key);
        if (lastSendTime != null && now - lastSendTime < windowMillis) {
            log.info("【飞书通知】命中去重时间窗，跳过发送卡片，key:{}, minutes:{}, remainMs:{}", key, minutes, windowMillis - (now - lastSendTime));
            return;
        }

        TEXT_SEND_CACHE.put(key, now);
        sendCard(card);
    }

    private void postMessage(Map<String, Object> body, String logContent) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        Exception lastException = null;
        int attempt = 1;
        while (true) {
            try {
                ResponseEntity<String> responseEntity = restTemplate.postForEntity(FEISHU_WEBHOOK_URL, entity, String.class);
                log.info("【飞书通知】发送成功，attempt:{}, 响应:{}", attempt, responseEntity.getBody());
                return;
            } catch (Exception e) {
                lastException = e;
                if (attempt == MAX_SEND_ATTEMPTS) {
                    break;
                }
                log.warn("【飞书通知】发送失败，准备重试，attempt:{}/{}, retryAfterMs:{}, 消息内容:{}", attempt, MAX_SEND_ATTEMPTS, RETRY_SLEEP_MILLIS, logContent, e);
                sleepBeforeRetry();
                attempt++;
            }
        }
        log.error("【飞书通知】发送失败，已达到最大重试次数:{}, 消息内容:{}", MAX_SEND_ATTEMPTS, logContent, lastException);
    }

    private void sleepBeforeRetry() {
        try {
            TimeUnit.MILLISECONDS.sleep(RETRY_SLEEP_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
