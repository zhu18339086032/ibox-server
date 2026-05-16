package com.ibox.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.model.ProxyInfo;
import com.ibox.model.res.ibox.IboxApiResponse;

import java.util.Map;

/**
 * iBox客户端
 */
public interface IboxClient {

    /**
     * 执行GET请求，响应自动解密
     *
     * @param token      用户token
     * @param url        请求地址
     * @param queryParams 查询参数
     * @param responseType 响应数据类型
     * @return iBox响应
     */
    <T> IboxApiResponse<T> get(String token, String url, Map<String, ?> queryParams, Class<T> responseType);

    /**
     * 执行GET请求，支持代理，响应自动解密
     *
     * @param token        用户token
     * @param url          请求地址
     * @param queryParams  查询参数
     * @param responseType 响应数据类型
     * @param proxyInfo    代理信息
     * @return iBox响应
     */
    <T> IboxApiResponse<T> get(String token, String url, Map<String, ?> queryParams, Class<T> responseType, ProxyInfo proxyInfo);

    /**
     * 执行GET请求，响应自动解密
     *
     * @param token        用户token
     * @param url          请求地址
     * @param queryParams  查询参数
     * @param typeReference 响应数据类型引用
     * @return iBox响应
     */
    <T> IboxApiResponse<T> get(String token, String url, Map<String, ?> queryParams, TypeReference<T> typeReference);

    /**
     * 执行GET请求，支持代理，响应自动解密
     *
     * @param token         用户token
     * @param url           请求地址
     * @param queryParams   查询参数
     * @param typeReference 响应数据类型引用
     * @param proxyInfo     代理信息
     * @return iBox响应
     */
    <T> IboxApiResponse<T> get(String token, String url, Map<String, ?> queryParams, TypeReference<T> typeReference, ProxyInfo proxyInfo);

    /**
     * 执行POST请求，请求自动加密，响应自动解密
     *
     * @param token        用户token
     * @param url          请求地址
     * @param requestBody  请求体
     * @param responseType 响应数据类型
     * @return iBox响应
     */
    <T> IboxApiResponse<T> post(String token, String url, Object requestBody, Class<T> responseType);

    /**
     * 执行POST请求，支持代理，请求自动加密，响应自动解密
     *
     * @param token        用户token
     * @param url          请求地址
     * @param requestBody  请求体
     * @param responseType 响应数据类型
     * @param proxyInfo    代理信息
     * @return iBox响应
     */
    <T> IboxApiResponse<T> post(String token, String url, Object requestBody, Class<T> responseType, ProxyInfo proxyInfo);

    /**
     * 执行POST请求，请求自动加密，响应自动解密
     *
     * @param token         用户token
     * @param url           请求地址
     * @param requestBody   请求体
     * @param typeReference 响应数据类型引用
     * @return iBox响应
     */
    <T> IboxApiResponse<T> post(String token, String url, Object requestBody, TypeReference<T> typeReference);

    /**
     * 执行POST请求，支持代理，请求自动加密，响应自动解密
     *
     * @param token         用户token
     * @param url           请求地址
     * @param requestBody   请求体
     * @param typeReference 响应数据类型引用
     * @param proxyInfo     代理信息
     * @return iBox响应
     */
    <T> IboxApiResponse<T> post(String token, String url, Object requestBody, TypeReference<T> typeReference, ProxyInfo proxyInfo);
}
