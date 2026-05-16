package com.ibox.utils;

import com.alibaba.fastjson.JSONObject;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 汇付余额支付工具类。
 * 传入 uuid 和支付密码，即可生成加密后的请求体、请求头以及签名值，并直接发起请求。
 */
public final class HfPayBalancePayUtil {

    /**
     * 汇付余额支付接口地址。
     */
    public static final String BALANCE_PAY_URL = "https://hfpay.cloudpnr.com/api/hfpwalleth5/balancepay";

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
     * 固定支付发起方类型。
     */
    private static final String PAYMENT_INITIATOR_TYPE = "0";

    /**
     * 支付页面链接中的 uuid 提取规则。
     */
    private static final Pattern PAY_LINK_UUID_PATTERN = Pattern.compile("[?&]uuid=([^&]+)");

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

    /**
     * 直接发起汇付余额支付请求。
     *
     * @param uuid     支付链接 uuid
     * @param password 支付密码
     * @return 接口原始响应文本
     */
    public static String balancePay(String uuid, String password) {
        BalancePayRequest request = build(uuid, password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        request.headers().forEach(headers::set);

        HttpEntity<String> entity = new HttpEntity<>(request.bodyJson(), headers);
        ResponseEntity<String> response = REST_TEMPLATE.postForEntity(request.url(), entity, String.class);
        return response.getBody();
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
            return java.util.Base64.getEncoder().encodeToString(encrypted);
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
            return toHexLower(digest);
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
        return uuid.length() <= 16 ? uuid : uuid.substring(0, 16);
    }

    /**
     * 生成支付请求时需要的 paymentInitiatorType。
     *
     * @return 固定值 0
     */
    public static String getPaymentInitiatorType() {
        return PAYMENT_INITIATOR_TYPE;
    }

    /**
     * 从支付链接中提取 uuid。
     *
     * @param link 支付页面链接
     * @return uuid，提取失败返回 null
     */
    public static String extractUuidFromLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        Matcher matcher = PAY_LINK_UUID_PATTERN.matcher(link);
        if (!matcher.find()) {
            return null;
        }
        return matcher.group(1);
    }

    /**
     * 本地联调入口。
     * 可直接修改 uuid 和 password 进行测试。
     */
    public static void main(String[] args) {
        String uuid = "hfpwallet6666000162413365pay309cc39b-a573-4013-93de-bb210a84a5f2";
        String password = "347364";

        BalancePayRequest request = build(uuid, password);
        System.out.println("请求地址: " + request.url());
        System.out.println("商户号: " + request.merCustId());
        System.out.println("加密密码: " + request.encryptedPassword());
        System.out.println("请求头: " + JSONObject.toJSONString(request.headers()));
        System.out.println("请求体: " + request.bodyJson());

        String response = balancePay(uuid, password);
        System.out.println("响应结果: " + response);
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
}
