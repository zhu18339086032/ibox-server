package com.ibox.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代理信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProxyInfo {

    /**
     * 代理主机
     */
    private String host;

    /**
     * 代理端口
     */
    private Integer port;

    /**
     * 代理用户名
     */
    private String username;

    /**
     * 代理密码
     */
    private String password;
}
