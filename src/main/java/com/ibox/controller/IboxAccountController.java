package com.ibox.controller;

import com.alibaba.fastjson.JSONObject;
import com.ibox.manager.IboxAccountManager;
import com.ibox.model.req.web.IboxAccountPageReq;
import com.ibox.model.res.web.IboxAccountListRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.WebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * iBox账号控制器
 */
@RestController
@RequestMapping("/ibox/account")
@Slf4j
@Tag(name = "iBox账号")
public class IboxAccountController {

    /**
     * iBox账号服务
     */
    @Autowired
    private IboxAccountManager iboxAccountManager;

    /**
     * 账户列表接口
     *
     * @param req 查询条件
     * @return 账户分页列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取账户列表")
    public WebResponse<PageResponse<IboxAccountListRes>> list(IboxAccountPageReq req) {
        log.info("【账户列表接口】请求参数:{}", JSONObject.toJSONString(req));
        return WebResponse.success(iboxAccountManager.list(req));
    }
}
