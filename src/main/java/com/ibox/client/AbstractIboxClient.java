package com.ibox.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibox.model.ProxyInfo;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.utils.IboxCryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base iBox client with shared request, crypto, and response handling.
 */
@Slf4j
public abstract class AbstractIboxClient implements IboxClient {

    /**
     * Character set used to build random AES keys.
     */
    private static final String AES_KEY_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    /**
     * Request timeout in milliseconds.
     */
    private static final int REQUEST_TIMEOUT_MILLIS = 150;

    /**
     * Default direct RestTemplate.
     */
    public static final RestTemplate restTemplate = new RestTemplate();

    /**
     * Shared JSON mapper.
     */
    public static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Cached RestTemplate instances keyed by proxy settings.
     */
    private static final Map<String, RestTemplate> PROXY_TEMPLATE_CACHE = new ConcurrentHashMap<>();

    @Override
    public <T> IboxApiResponse<T> get(String token, String url, Map<String, ?> queryParams, Class<T> responseType) {
        return get(token, url, queryParams, responseType, null);
    }

    @Override
    public <T> IboxApiResponse<T> get(String token, String url, Map<String, ?> queryParams, Class<T> responseType, ProxyInfo proxyInfo) {
        URI uri = buildUri(url, queryParams);
        HttpHeaders headers = buildHeaders(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String rawBody = exchangeForBody(uri, HttpMethod.GET, entity, proxyInfo);
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(IboxApiResponse.class, responseType);
        return decodeResponseBody(rawBody, javaType);
    }

    @Override
    public <T> IboxApiResponse<T> get(String token, String url, Map<String, ?> queryParams, TypeReference<T> typeReference) {
        return get(token, url, queryParams, typeReference, null);
    }

    @Override
    public <T> IboxApiResponse<T> get(String token, String url, Map<String, ?> queryParams, TypeReference<T> typeReference, ProxyInfo proxyInfo) {
        URI uri = buildUri(url, queryParams);
        HttpHeaders headers = buildHeaders(token);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        String rawBody = exchangeForBody(uri, HttpMethod.GET, entity, proxyInfo);
        JavaType bodyType = objectMapper.getTypeFactory().constructType(typeReference);
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(IboxApiResponse.class, bodyType);
        return decodeResponseBody(rawBody, javaType);
    }

    @Override
    public <T> IboxApiResponse<T> post(String token, String url, Object requestBody, Class<T> responseType) {
        return post(token, url, requestBody, responseType, null);
    }

    @Override
    public <T> IboxApiResponse<T> post(String token, String url, Object requestBody, Class<T> responseType, ProxyInfo proxyInfo) {
        HttpHeaders headers = buildHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String requestJson = writeJson(requestBody);
        Map<String, String> encryptedBody = encryptRequestBody(requestJson);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(encryptedBody, headers);
        String rawBody = exchangeForBody(URI.create(url), HttpMethod.POST, entity, proxyInfo);
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(IboxApiResponse.class, responseType);
        return decodeResponseBody(rawBody, javaType);
    }

    @Override
    public <T> IboxApiResponse<T> post(String token, String url, Object requestBody, TypeReference<T> typeReference) {
        return post(token, url, requestBody, typeReference, null);
    }

    @Override
    public <T> IboxApiResponse<T> post(String token, String url, Object requestBody, TypeReference<T> typeReference, ProxyInfo proxyInfo) {
        HttpHeaders headers = buildHeaders(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String requestJson = writeJson(requestBody);
        Map<String, String> encryptedBody = encryptRequestBody(requestJson);
        HttpEntity<Map<String, String>> entity = new HttpEntity<>(encryptedBody, headers);
        String rawBody = exchangeForBody(URI.create(url), HttpMethod.POST, entity, proxyInfo);
        JavaType bodyType = objectMapper.getTypeFactory().constructType(typeReference);
        JavaType javaType = objectMapper.getTypeFactory().constructParametricType(IboxApiResponse.class, bodyType);
        return decodeResponseBody(rawBody, javaType);
    }

    /**
     * Decrypts and deserializes a raw response body.
     */
    protected <T> IboxApiResponse<T> decodeResponseBody(String rawBody, JavaType javaType) {
        if (rawBody == null || rawBody.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            if (root.hasNonNull("encryptKey") && root.hasNonNull("data")) {
                String decryptedJson = IboxCryptoUtil.decryptResponse(
                        root.get("data").asText(),
                        root.get("encryptKey").asText()
                );
                return parseResponse(decryptedJson, javaType);
            }
            return parseResponse(rawBody, javaType);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("解析iBox响应失败", e);
        }
    }

    protected abstract <T> IboxApiResponse<T> parseResponse(String plainJson, JavaType javaType);

    /**
     * Wraps request JSON into the encrypted payload structure.
     */
    protected Map<String, String> encryptRequestBody(String requestJson) {
        String aesKey = generateAesKey();
        return IboxCryptoUtil.encryptPayload(requestJson, aesKey);
    }

    /**
     * Generates a random 16-character AES key.
     */
    protected String generateAesKey() {
        SecureRandom secureRandom = new SecureRandom();
        StringBuilder builder = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            builder.append(AES_KEY_CHARS.charAt(secureRandom.nextInt(AES_KEY_CHARS.length())));
        }
        return builder.toString();
    }

    /**
     * Builds common HTTP headers for iBox requests.
     */
    protected HttpHeaders buildHeaders(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setAcceptLanguageAsLocales(List.of(java.util.Locale.SIMPLIFIED_CHINESE, java.util.Locale.CHINA));
        headers.set(HttpHeaders.ACCEPT_LANGUAGE, "zh-CN,zh;q=0.9");
        headers.set(HttpHeaders.CACHE_CONTROL, "no-cache");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        headers.set(HttpHeaders.CONNECTION, "keep-alive");
        headers.set(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Linux; Android 12; M2012K11AC Build/SKQ1.211006.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.0.0 Mobile Safari/537.36 iBox/1.0");
        headers.set("Origin", "https://www.ibox.art");
        headers.set("Referer", "https://www.ibox.art/");
        headers.set("X-Requested-With", "com.ibox.app");
        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token.replaceFirst("^Bearer\\s+", ""));
        }
        return headers;
    }

    /**
     * Builds a request URI from the base URL and query params.
     */
    protected URI buildUri(String url, Map<String, ?> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach((key, value) -> {
                if (value != null) {
                    builder.queryParam(key, value);
                }
            });
        }
        return builder.build(true).toUri();
    }

    /**
     * Executes the request and returns the raw response body.
     */
    protected String exchangeForBody(URI uri, HttpMethod method, HttpEntity<?> entity, ProxyInfo proxyInfo) {
        try {
            RestTemplate executeTemplate = buildRestTemplate(proxyInfo);
            ResponseEntity<String> responseEntity = executeTemplate.exchange(uri, method, entity, String.class);
            return responseEntity.getBody() == null ? "" : responseEntity.getBody();
        } catch (RestClientException e) {
            if (e.getMessage().contains("429")) {
                log.info("【iBox客户端】请求iBox接口失败:【429】--------------{}|{}", proxyInfo.getHost(), proxyInfo.getUsername());
            } else if (e.getMessage().contains("Read timed out") || e.getMessage().contains("Connect timed out")) {
                log.info("【iBox客户端】请求iBox接口失败:【超时】--------------{}|{}", proxyInfo.getHost(), proxyInfo.getUsername());
            } else {
                log.info("【iBox客户端】解析iBox接口响应失败:{}", e.getMessage());
                log.info("【iBox客户端】请求iBox接口失败:{}|{}", proxyInfo.getHost(), proxyInfo.getUsername());
            }
            return "";
        }
    }

    /**
     * Serializes a request object into JSON.
     */
    protected String writeJson(Object value) {
        try {
            if (value == null) {
                return "{}";
            }
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化请求体失败", e);
        }
    }

    /**
     * Returns a reusable RestTemplate for the given proxy settings.
     */
    private RestTemplate buildRestTemplate(ProxyInfo proxyInfo) {
        if (proxyInfo == null || proxyInfo.getHost() == null || proxyInfo.getHost().isBlank() || proxyInfo.getPort() == null) {
            return restTemplate;
        }
        return PROXY_TEMPLATE_CACHE.computeIfAbsent(buildProxyCacheKey(proxyInfo), key -> createProxyRestTemplate(proxyInfo));
    }

    /**
     * Builds the cache key used for proxy client reuse.
     */
    private String buildProxyCacheKey(ProxyInfo proxyInfo) {
        return String.join("|",
                nullToEmpty(proxyInfo.getHost()),
                String.valueOf(proxyInfo.getPort()),
                nullToEmpty(proxyInfo.getUsername()),
                nullToEmpty(proxyInfo.getPassword()));
    }

    /**
     * Converts null text into an empty string for key building.
     */
    private String nullToEmpty(String text) {
        return text == null ? "" : text;
    }

    /**
     * Creates a proxy-enabled RestTemplate instance.
     */
    private RestTemplate createProxyRestTemplate(ProxyInfo proxyInfo) {
        if (proxyInfo.getUsername() == null || proxyInfo.getUsername().isBlank()) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyInfo.getHost(), proxyInfo.getPort())));
            requestFactory.setConnectTimeout(REQUEST_TIMEOUT_MILLIS);
            requestFactory.setReadTimeout(REQUEST_TIMEOUT_MILLIS);
            return new RestTemplate(requestFactory);
        }

        HttpHost proxyHost = new HttpHost(proxyInfo.getHost(), proxyInfo.getPort());
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
                new AuthScope(proxyHost),
                new UsernamePasswordCredentials(
                        proxyInfo.getUsername(),
                        (proxyInfo.getPassword() == null ? "" : proxyInfo.getPassword()).toCharArray()
                )
        );

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(REQUEST_TIMEOUT_MILLIS))
                .setResponseTimeout(Timeout.ofMilliseconds(REQUEST_TIMEOUT_MILLIS))
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(REQUEST_TIMEOUT_MILLIS))
                .setProxyPreferredAuthSchemes(List.of(StandardAuthScheme.BASIC))
                .build();

        CloseableHttpClient httpClient = HttpClients.custom()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(32)
                        .setMaxConnPerRoute(8)
                        .build())
                .setRoutePlanner(new DefaultProxyRoutePlanner(proxyHost))
                .setDefaultCredentialsProvider(credentialsProvider)
                .setDefaultRequestConfig(requestConfig)
                .disableCookieManagement()
                .build();

        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(REQUEST_TIMEOUT_MILLIS);
        return new RestTemplate(requestFactory);
    }
}
