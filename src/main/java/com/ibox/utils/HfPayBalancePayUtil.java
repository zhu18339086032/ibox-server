package com.ibox.utils;

import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 汇付余额支付工具类。
 * 传入 uuid 和支付密码，即可生成加密后的请求体、请求头以及签名值，并直接发起请求。
 */
@Slf4j
public final class HfPayBalancePayUtil {

    /**
     * 汇付余额支付接口地址。
     */
    public static final String BALANCE_PAY_URL = "https://hfpay.cloudpnr.com/api/hfpwalleth5/balancepay";

    /**
     * 前置
     */
    public static final String TRANSVERIFY_QUERY_URL = "https://hfpay.cloudpnr.com/api/hfpwalleth5/transverifyquery";

    /**
     * 3DES 初始化向量。
     */
    private static final byte[] IV = "chinapnr".getBytes(StandardCharsets.UTF_8);

    /**
     * HMAC 签名密钥。
     */
    private static final byte[] SIGN_KEY = "chinapnr".getBytes(StandardCharsets.UTF_8);

    /**
     * 固定设备信息。
     */
    private static final String DEV_INFO_JSON = "{\"devType\":\"2\",\"devSysType\":\"H5\",\"mobileFlag\":\"Y\"}";

    /**
     * 汇付余额支付 HTTP 客户端。
     */
    private static final RestTemplate REST_TEMPLATE = createRestTemplate();

    private HfPayBalancePayUtil() {
    }

    /**
     * 构建余额支付请求。
     *
     * @param uuid     支付链接 uuid
     * @param password 支付密码
     * @return 支付请求结果
     */
    public static BalancePayRequest build(String uuid, String password) {
        String merCustId = buildMerCustId(uuid);
        String encryptedPassword = encryptPassword(uuid, password);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dev_info_json", DEV_INFO_JSON);
        body.put("password", encryptedPassword);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("uuid", uuid);
        headers.put("check_value", buildCheckValue(body));
        headers.put("mer_cust_id", merCustId);

        return new BalancePayRequest(BALANCE_PAY_URL, merCustId, encryptedPassword, headers, JSONObject.toJSONString(body));
    }

    private static TransverifyQueryReq buildTransverifyQuery(String uuid) {
        String merCustId = buildMerCustId(uuid);
        String trans_type = "30";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dev_info_json", DEV_INFO_JSON);
        body.put("trans_type", trans_type);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("uuid", uuid);
        headers.put("check_value", buildCheckValue(body));
        headers.put("mer_cust_id", merCustId);
        return new TransverifyQueryReq(TRANSVERIFY_QUERY_URL, merCustId, trans_type, headers, JSONObject.toJSONString(body));
    }

    /**
     * 状态确认
     *
     * @param uuid
     */
    public static String transverifyquery(String uuid) {
        TransverifyQueryReq request = buildTransverifyQuery(uuid);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        request.headers().forEach(headers::set);

        HttpEntity<String> entity = new HttpEntity<>(request.bodyJson(), headers);
        try {
            log.info("【汇付状态确认】开始发起请求, uuid:{}, merCustId:{}", maskUuid(uuid), request.merCustId());
            ResponseEntity<String> response = REST_TEMPLATE.postForEntity(request.url(), entity, String.class);
            String responseBody = response.getBody();
            log.info("【汇付状态确认】请求完成, statusCode:{}, response:{}", response.getStatusCode().value(), responseBody);
            return responseBody;
        } catch (Exception e) {
            log.error("【汇付状态确认】请求异常, uuid:{}, merCustId:{}, url:{}", maskUuid(uuid), request.merCustId(), request.url(), e);
            throw e;
        }
    }

    public static String paystatquery(String uuid) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("uuid",uuid);
        HttpEntity<String> entity = new HttpEntity<>(JSONObject.toJSONString(new HashMap<>()), headers);
        try {
            log.info("【汇付状态确认】开始发起请求, uuid:{}", uuid);
            ResponseEntity<String> response = REST_TEMPLATE.postForEntity("https://hfpay.cloudpnr.com/api/hfpwalleth5/paystatquery", entity, String.class);
            String responseBody = response.getBody();
            log.info("【汇付状态确认】请求完成, statusCode:{}, response:{}", response.getStatusCode().value(), responseBody);
            return responseBody;
        } catch (Exception e) {
            throw e;
        }
    }


    /**
     * 直接发起汇付余额支付请求。
     *
     * @param uuid     支付链接 uuid
     * @param password 支付密码
     * @return 接口原始响应文本
     */
    public static String balancePay(String uuid, String password) {
        log.info("【汇付余额支付】开始构建支付请求, uuid:{}, passwordLength:{}", maskUuid(uuid), password == null ? 0 : password.length());

        BalancePayRequest request = build(uuid, password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        request.headers().forEach(headers::set);

        HttpEntity<String> entity = new HttpEntity<>(request.bodyJson(), headers);

        log.info("【汇付余额支付】请求参数已生成, url:{}, merCustId:{}, encryptedPassword:{}, headers:{}, body:{}",
                request.url(),
                request.merCustId(),
                maskEncryptedPassword(request.encryptedPassword()),
                JSONObject.toJSONString(request.headers()),
                request.bodyJson());

        try {
            log.info("【汇付余额支付】开始发起请求, uuid:{}, merCustId:{}", maskUuid(uuid), request.merCustId());
            ResponseEntity<String> response = REST_TEMPLATE.postForEntity(request.url(), entity, String.class);
            String responseBody = response.getBody();
            log.info("【汇付余额支付】请求完成, statusCode:{}, response:{}", response.getStatusCode().value(), responseBody);
            return responseBody;
        } catch (Exception e) {
            log.error("【汇付余额支付】请求异常, uuid:{}, merCustId:{}, url:{}", maskUuid(uuid), request.merCustId(), request.url(), e);
            throw e;
        }
    }

    /**
     * 使用 uuid 作为 3DES 密钥来源，加密支付密码。
     *
     * @param uuid     支付链接 uuid
     * @param password 支付密码
     * @return Base64 加密结果
     */
    public static String encryptPassword(String uuid, String password) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("uuid 不能为空");
        }
        if (password == null) {
            throw new IllegalArgumentException("password 不能为空");
        }

        String keyText = uuid.length() >= 24 ? uuid.substring(0, 24) : uuid + "0".repeat(24 - uuid.length());
        byte[] keyBytes = keyText.substring(0, 24).getBytes(StandardCharsets.UTF_8);
        try {
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "DESede");
            Cipher cipher = Cipher.getInstance("DESede/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(IV));
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            String result = java.util.Base64.getEncoder().encodeToString(encrypted);
            log.info("【汇付余额支付】密码加密完成, uuid:{}, encryptedPassword:{}", maskUuid(uuid), maskEncryptedPassword(result));
            return result;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("3DES 加密支付密码失败", e);
        }
    }

    /**
     * 生成 check_value。
     * 规则：参数按 key 排序后拼成 key=value&key=value，再用 HmacSHA256 + chinapnr 签名。
     *
     * @param params 请求参数
     * @return 小写十六进制签名
     */
    public static String buildCheckValue(Map<String, ?> params) {
        if (params == null || params.isEmpty()) {
            throw new IllegalArgumentException("签名参数不能为空");
        }

        Map<String, Object> sorted = new TreeMap<>(params);
        StringBuilder signStr = new StringBuilder();
        for (Map.Entry<String, Object> entry : sorted.entrySet()) {
            if (signStr.length() > 0) {
                signStr.append('&');
            }
            signStr.append(entry.getKey()).append('=').append(entry.getValue());
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SIGN_KEY, "HmacSHA256"));
            byte[] digest = mac.doFinal(signStr.toString().getBytes(StandardCharsets.UTF_8));
            String checkValue = toHexLower(digest);
            log.info("【汇付余额支付】check_value 生成完成, signSource:{}, checkValue:{}", signStr, checkValue);
            return checkValue;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("生成 check_value 失败", e);
        }
    }

    /**
     * 根据 uuid 生成商户号。
     *
     * @param uuid 支付链接 uuid
     * @return 前 16 位字符
     */
    public static String buildMerCustId(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            throw new IllegalArgumentException("uuid 不能为空");
        }
        String merCustId = uuid.length() <= 25 ? uuid : uuid.substring(9, 25);
        log.info("【汇付余额支付】商户号生成完成, uuid:{}, merCustId:{}", maskUuid(uuid), merCustId);
        return merCustId;
    }


    /**
     * 本地联调入口。
     * 可直接修改 uuid 和 password 进行测试。
     */
    public static void main(String[] args) {
        String uuid = "";
        String password = "347364";
        String response0 = paystatquery(uuid);
        String response1 = transverifyquery(uuid);
        String response2 = balancePay(uuid, password);
        System.out.println("响应结果0: " + response0);
        System.out.println("响应结果1: " + response1);
        System.out.println("响应结果2: " + response2);
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(10000);
        return new RestTemplate(requestFactory);
    }

    private static String toHexLower(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    /**
     * 脱敏显示 uuid，避免日志打印完整敏感信息。
     */
    private static String maskUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return "-";
        }
        if (uuid.length() <= 12) {
            return uuid;
        }
        return uuid.substring(0, 8) + "..." + uuid.substring(uuid.length() - 4);
    }

    /**
     * 脱敏显示加密后的支付密码，便于排查但避免完整暴露。
     */
    private static String maskEncryptedPassword(String encryptedPassword) {
        if (encryptedPassword == null || encryptedPassword.isBlank()) {
            return "-";
        }
        if (encryptedPassword.length() <= 8) {
            return encryptedPassword;
        }
        return encryptedPassword.substring(0, 4) + "..." + encryptedPassword.substring(encryptedPassword.length() - 4);
    }

    /**
     * 汇付余额支付请求结果。
     *
     * @param url               接口地址
     * @param merCustId         商户号
     * @param encryptedPassword 加密后的密码
     * @param headers           请求头
     * @param bodyJson          请求体 JSON
     */
    public record BalancePayRequest(String url, String merCustId, String encryptedPassword, Map<String, String> headers,
                                    String bodyJson) {
    }

    public record TransverifyQueryReq(String url, String merCustId, String trans_type, Map<String, String> headers,
                                      String bodyJson) {
    }
}
