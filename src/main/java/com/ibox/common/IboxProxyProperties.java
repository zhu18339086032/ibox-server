package com.ibox.common;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * iBox代理配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "ibox.proxy")
public class IboxProxyProperties {

    /**
     * 动态代理获取接口地址
     */
    private String apiUrl;

    /**
     * 配置文件中的静态代理列表
     */
    private List<String> configProxies = new ArrayList<>();
}
