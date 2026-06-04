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

/**
 * 飞书消息工具类
 */
@Slf4j
@Component
public class FeishuMessageUtil {

    /**
     * 飞书机器人 webhook 地址
     */
    private static final String FEISHU_WEBHOOK_URL = "https://open.feishu.cn/open-apis/bot/v2/hook/520f8052-58c8-49db-bd53-71fe4330a535";

    /**
     * 文本消息去重缓存，key 为业务唯一键，value 为最近一次发送时间戳
     */
    private static final Map<String, Long> TEXT_SEND_CACHE = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate;

    public FeishuMessageUtil() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(1000);
        requestFactory.setReadTimeout(1000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    /**
     * 发送文本消息
     *
     * @param text 消息内容
     */
    public void sendText(String text) {
        Map<String, Object> content = new HashMap<>();
        content.put("text", text);

        Map<String, Object> body = new HashMap<>();
        body.put("msg_type", "text");
        body.put("content", content);
        postMessage(body, JSONObject.toJSONString(text));
    }

    /**
     * 发送带去重时间窗的文本消息。
     * 相同 key 在指定分钟数内只发送一次，避免短时间重复通知。
     *
     * @param text 消息内容
     * @param key 去重业务键
     * @param minutes 去重时间窗，单位分钟
     */
    public void sendText(String text, String key, int minutes) {
        if (minutes <= 0 || key == null || key.isBlank()) {
            sendText(text);
            return;
        }

        long now = System.currentTimeMillis();
        long windowMillis = minutes * 60L * 1000L;
        Long lastSendTime = TEXT_SEND_CACHE.get(key);
        if (lastSendTime != null && now - lastSendTime < windowMillis) {
            log.info("【飞书通知】命中去重时间窗，跳过发送，key:{}, minutes:{}, remainMs:{}",
                    key,
                    minutes,
                    windowMillis - (now - lastSendTime));
            return;
        }

        TEXT_SEND_CACHE.put(key, now);
        sendText(text);
    }

    /**
     * 发送飞书卡片消息
     *
     * @param card 卡片内容
     */
    public void sendCard(Map<String, Object> card) {
        Map<String, Object> body = new HashMap<>();
        body.put("msg_type", "interactive");
        body.put("card", card);
        postMessage(body, JSONObject.toJSONString(card));
    }

    /**
     * 发送带去重时间窗的飞书卡片消息。
     * 相同 key 在指定分钟数内只发送一次，避免短时间重复卡片轰炸。
     *
     * @param card 卡片内容
     * @param key 去重业务键
     * @param minutes 去重时间窗，单位分钟
     */
    public void sendCard(Map<String, Object> card, String key, int minutes) {
        if (minutes <= 0 || key == null || key.isBlank()) {
            sendCard(card);
            return;
        }

        long now = System.currentTimeMillis();
        long windowMillis = minutes * 60L * 1000L;
        Long lastSendTime = TEXT_SEND_CACHE.get(key);
        if (lastSendTime != null && now - lastSendTime < windowMillis) {
            log.info("【飞书通知】命中去重时间窗，跳过发送卡片，key:{}, minutes:{}, remainMs:{}",
                    key,
                    minutes,
                    windowMillis - (now - lastSendTime));
            return;
        }

        TEXT_SEND_CACHE.put(key, now);
        sendCard(card);
    }

    private void postMessage(Map<String, Object> body, String logContent) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> responseEntity = restTemplate.postForEntity(FEISHU_WEBHOOK_URL, entity, String.class);
            log.info("【飞书通知】发送成功，响应:{}", responseEntity.getBody());
        } catch (Exception e) {
            log.error("【飞书通知】发送失败，消息内容:{}", logContent, e);
        }
    }
}
