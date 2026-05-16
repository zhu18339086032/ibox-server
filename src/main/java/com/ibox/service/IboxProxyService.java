package com.ibox.service;

import com.ibox.model.ProxyInfo;

/**
 * iBox代理服务接口
 */
public interface IboxProxyService {

    /**
     * 获取一个新的动态代理
     *
     * @return 代理信息
     */
    ProxyInfo getNewProxy();

    /**
     * 轮询获取配置文件中的代理
     *
     * @return 代理信息
     */
    ProxyInfo getNextConfigProxy();
}
