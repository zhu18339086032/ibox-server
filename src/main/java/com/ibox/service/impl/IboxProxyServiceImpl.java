package com.ibox.service.impl;

import com.ibox.common.IboxProxyProperties;
import com.ibox.model.ProxyInfo;
import com.ibox.service.IboxProxyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * iBox代理服务实现类
 */
@Slf4j
@Service
public class IboxProxyServiceImpl implements IboxProxyService {

    private static final AtomicInteger CONFIG_PROXY_INDEX = new AtomicInteger(0);

    /**
     * 代理配置
     */
    @Autowired
    private IboxProxyProperties iboxProxyProperties;

    private final RestTemplate restTemplate;

    public IboxProxyServiceImpl() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(200);
        requestFactory.setReadTimeout(200);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    /**
     * 获取一个新的动态代理
     *
     * @return 代理信息
     */
    @Override
    public ProxyInfo getNewProxy() {
        String apiUrl = iboxProxyProperties.getApiUrl();
        if (apiUrl == null || apiUrl.isBlank()) {
            throw new IllegalStateException("代理接口地址未配置");
        }
        ResponseEntity<String> responseEntity = restTemplate.getForEntity(apiUrl, String.class);
        String body = responseEntity.getBody();
        if (body == null || body.isBlank()) {
            throw new IllegalStateException("代理接口未返回可用代理");
        }
        String proxyText = body.trim();
        ProxyInfo proxyInfo = parseProxy(proxyText);
        log.info("【代理服务】获取新代理成功:{}", proxyText);
        return proxyInfo;
    }

    /**
     * 轮询获取配置文件中的代理
     *
     * @return 代理信息
     */
    @Override
    public ProxyInfo getNextConfigProxy() {
        List<String> configProxies = iboxProxyProperties.getConfigProxies();
        if (configProxies == null || configProxies.isEmpty()) {
            throw new IllegalStateException("配置文件中未配置可用代理");
        }
        int index = Math.floorMod(CONFIG_PROXY_INDEX.getAndIncrement(), configProxies.size());
        String proxyText = configProxies.get(index);
        if (Objects.equals(proxyText,"local")){
//            log.info(proxyText);
            return null;
        }
//        log.info("【代理服务】获取配置代理:{}", proxyText);
        return parseProxy(proxyText);
    }

    private ProxyInfo parseProxy(String proxyText) {
        if (proxyText == null || proxyText.isBlank()) {
            throw new IllegalStateException("代理内容不能为空");
        }
        String[] parts = proxyText.trim().split(":");
        if (parts.length == 2) {
            return new ProxyInfo(parts[0], Integer.parseInt(parts[1]), null, null);
        }
        if (parts.length == 4) {
            return new ProxyInfo(parts[0], Integer.parseInt(parts[1]), parts[2], parts[3]);
        }
        throw new IllegalStateException("代理格式不正确:" + proxyText);
    }
}
