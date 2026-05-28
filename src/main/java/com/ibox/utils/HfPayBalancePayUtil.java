package com.ibox.utils;

import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.client.impl.DefaultIboxClient;
import com.ibox.constants.UrlConstant;
import com.ibox.model.res.ibox.*;
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
     * 提取uuid
     */
    public static String extractUuidFromLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        int idx = link.indexOf("uuid=");
        if (idx < 0) {
            return null;
        }
        String uuid = link.substring(idx + 5);
        int end = uuid.indexOf('&');
        return end >= 0 ? uuid.substring(0, end) : uuid;
    }


    /**
     * 本地联调入口。
     * 可直接修改 uuid 和 password 进行测试。
     */
    public static void main(String[] args) {
        String token1 = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJHYUtfN0VMOFFDNU5oc0FOMTJzOGFFYUdPdVJPcEJEcXFERnViOUpLY09nIn0.eyJleHAiOjE3ODI0ODMzOTgsImlhdCI6MTc3OTg5MTM5OCwianRpIjoiZmFlNjdkYmMtMTZhZS00ODlkLTkwMmEtZWFkNWM4M2RlOTFlIiwiaXNzIjoiaHR0cHM6Ly9rZXljbG9hay5pYm94LmNvbS9yZWFsbXMvaWJveCIsImF1ZCI6WyJyZWFsbS1tYW5hZ2VtZW50IiwiYWNjb3VudCJdLCJzdWIiOiJjNTA5ODJkMC1jNjY5LTQ4M2QtYmZjZC0yMjMzMmM3YjFjMzYiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJ1c2VyLWNsaWVudCIsInNlc3Npb25fc3RhdGUiOiI5YTMxZDk2Ny1kMGUwLTRlMDYtYmZjOC01ZTI5MDI1ZDJhZWMiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbIi8qIl0sInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJvZmZsaW5lX2FjY2VzcyIsImRlZmF1bHQtcm9sZXMtaWJveCIsInVtYV9hdXRob3JpemF0aW9uIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsicmVhbG0tbWFuYWdlbWVudCI6eyJyb2xlcyI6WyJtYW5hZ2UtdXNlcnMiXX0sImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoiaWJveC1zY29wZSBwcm9maWxlIGVtYWlsIiwic2lkIjoiOWEzMWQ5NjctZDBlMC00ZTA2LWJmYzgtNWUyOTAyNWQyYWVjIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJpZFZlcmlmaWVkIjoxLCJwYXltZW50VXNlcklkIjoiMTAwMDg1MDA2IiwicHJlZmVycmVkX3VzZXJuYW1lIjoiMTgzMzkwODYwMzIiLCJ3YWxsZXRBZGRyZXNzIjoiMHhjOTBkMWVjNjEzYzNjMzM4OTYzYmUzZmIxMzY0ZGRhYmI0ZmVlOTA0IiwidXNlcklkIjoxODI0MTk3fQ.Zzrefmst7yS1LL8BtOZNkFw-Zxeb2mL_J98LCjuygLjmQZXYA7PK3_30CFpub9zPQl0qNA9HhF-tKaJSkKF0JvHvvUYg-HglpCNZXG1FA_bihWc1bK3qwIHz1oNsu_M_mZ-qKQO1BloAnTkVMVeTQRUG6umdnwj3_PTCRyylVHNdbRYROoKP2nd3E86969ql1LkZ3XuYu0giwSL8Y1DY5gWQg9vgUHdbOYuKk7YpKv3QbKf6x-wTZMIBA63tmcCWX6zw9MpCR-Q_yWnwq4kxpkQkr7tkTIRSA2jrLW1bnkILLDkng2FvGA7G_xXH9J0UEmQ9MINUVCsLRcbjpgrJ8w";
        String token2 = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJHYUtfN0VMOFFDNU5oc0FOMTJzOGFFYUdPdVJPcEJEcXFERnViOUpLY09nIn0.eyJleHAiOjE3ODEwOTE3NjMsImlhdCI6MTc3ODQ5OTc2MywianRpIjoiMGFkMTljZjMtMmUzOC00NmQxLTg2OTEtZDRhZGVlYjZhNDg2IiwiaXNzIjoiaHR0cHM6Ly9rZXljbG9hay5pYm94LmNvbS9yZWFsbXMvaWJveCIsImF1ZCI6WyJyZWFsbS1tYW5hZ2VtZW50IiwiYWNjb3VudCJdLCJzdWIiOiI5MjJhNTE5NC1lYjE1LTRlYTAtOGQ1Yy0wNzVlNWYyM2ZmYTciLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJ1c2VyLWNsaWVudCIsInNlc3Npb25fc3RhdGUiOiI1YjdiNDliNi0xM2IyLTQ2ODktOTE1Yy04M2JjMTZkM2M4NjMiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbIi8qIl0sInJlYWxtX2FjY2VzcyI6eyJyb2xlcyI6WyJvZmZsaW5lX2FjY2VzcyIsImRlZmF1bHQtcm9sZXMtaWJveCIsInVtYV9hdXRob3JpemF0aW9uIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsicmVhbG0tbWFuYWdlbWVudCI6eyJyb2xlcyI6WyJtYW5hZ2UtdXNlcnMiXX0sImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoiaWJveC1zY29wZSBwcm9maWxlIGVtYWlsIiwic2lkIjoiNWI3YjQ5YjYtMTNiMi00Njg5LTkxNWMtODNiYzE2ZDNjODYzIiwiaGFzRGVzdHJveWVkIjowLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsImlkVmVyaWZpZWQiOjEsInBheW1lbnRVc2VySWQiOiI5MjJhNTE5NC1lYjE1LTRlYTAtOGQ1Yy0wNzVlNWYyM2ZmYTciLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiIxOTEwNjcxMTU5MCIsIndhbGxldEFkZHJlc3MiOiIweDQ5YzIwNjRjODU4MDI4NmJlNTI4MzEyYWEyNmEzM2UxZDgzOGViOGIiLCJpc1Rlc3RlciI6MCwidXNlcklkIjozOTE5NjA4fQ.Vy0ZCD5YaJMPHL_vIOxg8LWmcwaDOE6dVX6v8AWgoJTLBu9po68kZJJ5UwgX4B28HYSiyx-CFTW9ug0ttNwUPG8QfOeWfuKha1ftpecOg0t0GXVlqYV47zTsK0aet-dO8NWCqgmPNe5M0amsBbKaFqEqrdMJ_QxXBngNa1zLxw6wo9k5pQ_DQiH9I8GGYSH3ZLw3qmI6NFGRIeZdo70i2rN9ficxfZhu5bpt366Q9Jrrx8fqhO7f30fw-Jc3SoKpLY7zt-Uj_6sgk-0v3bsD3e11NoSc3pRqDfjuKUX9QANWVMeFwVcHBhAwkcDnMbjXt76euc3cLTmCV-rKQN5AKA";
        IboxClient iboxClient = new DefaultIboxClient();
        IboxApiResponse<IboxPageDataRes<OrderRes>> listResponse  = iboxClient.get(token1, UrlConstant.ORDERS_URL, null, new TypeReference<>() {
        });
        System.out.println(listResponse);
        OrderRes orderRes = listResponse.getData().getList().getFirst();
        IboxApiResponse<OrderPaymentInfoRes> paymentInfoResponse = iboxClient.get(token1, String.format(UrlConstant.ORDER_PAYMENT_INFO_URL, orderRes.getOrderUuid(), 0), null, OrderPaymentInfoRes.class);
        System.out.println(paymentInfoResponse);
        String uuid = extractUuidFromLink(paymentInfoResponse.getData().getLink());
        String password = "347364";
        String response1 = transverifyquery(uuid);
        String response2 = balancePay(uuid, password);
        System.out.println("响应结果1: " + response1);
        System.out.println("响应结果2: " + response2);
        //响应结果1: {"resp_code":"C00000","resp_desc":"成功","unique_id":"default task-487","req_seq_id":"HFPWALLET26052823395791805011","req_date":"20260528","password_verify_flag":"0","face_verify_flag":"1","wish_person_flag":"1","sms_verify_flag":"1","nsms_verify_flag":"1","ai_call_verify_flag":"1","verify_trans_method":"0"}
        //响应结果2: {"resp_code":"C00000","resp_desc":"成功","unique_id":"default task-350","req_seq_id":"HFPWALLET26052823395891805033","req_date":"20260528"}
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
