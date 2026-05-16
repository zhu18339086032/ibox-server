package com.ibox.model.req.web;

import lombok.Data;

/**
 * iBox账号分页查询请求
 */
@Data
public class IboxAccountPageReq {

    /**
     * 当前页码
     */
    private Long pageNum = 1L;

    /**
     * 每页条数
     */
    private Long pageSize = 10L;

    /**
     * 账号名称
     */
    private String accountName;

    /**
     * 手机号
     */
    private String phone;
}
