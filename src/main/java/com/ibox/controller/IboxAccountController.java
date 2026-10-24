package com.ibox.controller;

import com.alibaba.fastjson.JSONObject;
import com.ibox.manager.IboxAccountManager;
import com.ibox.model.req.web.IboxAccountPageReq;
import com.ibox.model.req.web.IboxAccountLoginReq;
import com.ibox.model.res.web.IboxAccountAuthRes;
import com.ibox.model.res.web.IboxAccountListRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.WebResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    /** 使用手机号和短信验证码登录 iBox。 */
    @PostMapping("/{id}/login")
    @Operation(summary = "iBox账号登录")
    public WebResponse<IboxAccountAuthRes> login(@PathVariable("id") Long id,
                                                  @RequestBody IboxAccountLoginReq req) {
        try {
            return WebResponse.success("iBox登录成功", iboxAccountManager.login(id, req));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("【iBox账号登录】失败, accountId:{}, message:{}", id, e.getMessage());
            return WebResponse.fail(e.getMessage());
        }
    }
}
