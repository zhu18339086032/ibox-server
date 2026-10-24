package com.ibox.model.req.web;

import lombok.Data;

/**
 * iBox账号登录请求。
 */
@Data
public class IboxAccountLoginReq {

    /** 手机号。 */
    private String phoneNumber;

    /** 短信验证码。 */
    private String code;
}
