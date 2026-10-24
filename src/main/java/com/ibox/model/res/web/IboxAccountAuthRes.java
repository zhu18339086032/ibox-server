package com.ibox.model.res.web;

import lombok.Data;

/**
 * 登录结果（不返回敏感 Token）。
 */
@Data
public class IboxAccountAuthRes {

    private Long id;
    private String accountName;
    private Long iboxUid;
    private String phone;
    private Long tokenExpiredAt;
    private Long refreshExpiredAt;
}
