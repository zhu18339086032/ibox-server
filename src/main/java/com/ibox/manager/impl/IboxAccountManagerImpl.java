package com.ibox.manager.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.client.IboxAuthClient;
import com.ibox.common.IboxAccountCache;
import com.ibox.manager.IboxAccountManager;
import com.ibox.model.req.web.IboxAccountPageReq;
import com.ibox.model.req.web.IboxAccountLoginReq;
import com.ibox.model.res.web.IboxAccountAuthRes;
import com.ibox.model.res.web.IboxAccountListRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.service.IboxAccountService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * iBox账号服务实现类
 */
@Service
public class IboxAccountManagerImpl implements IboxAccountManager {


    /**
     * iBox账号服务
     */
    @Autowired
    private IboxAccountService iboxAccountService;

    @Autowired
    private IboxAuthClient iboxAuthClient;

    @Autowired
    private IboxAccountCache iboxAccountCache;

    /**
     * 账户列表接口
     *
     * @param req 查询条件
     * @return 账户分页列表
     */
    @Override
    public PageResponse<IboxAccountListRes> list(IboxAccountPageReq req) {
        IPage<IboxAccountEntity> page = iboxAccountService.listIboxAccount(req);
        List<IboxAccountListRes> records = page.getRecords().stream().map(item -> {
            IboxAccountListRes res = new IboxAccountListRes();
            BeanUtils.copyProperties(item, res);
            return res;
        }).toList();
        return PageResponse.of(page.getCurrent(), page.getSize(), page.getTotal(), records);
    }

    @Override
    public IboxAccountAuthRes login(Long accountId, IboxAccountLoginReq req) {
        if (accountId == null) {
            throw new IllegalArgumentException("账号ID不能为空");
        }
        if (req == null || !StringUtils.hasText(req.getPhoneNumber()) || !StringUtils.hasText(req.getCode())) {
            throw new IllegalArgumentException("手机号和验证码不能为空");
        }
        IboxAccountEntity account = iboxAccountService.getById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("账号不存在");
        }

        JsonNode data = iboxAuthClient.login(req.getPhoneNumber().trim(), req.getCode().trim());
        JsonNode userInfo = data.path("userInfo");
        account.setPhone(req.getPhoneNumber().trim());
        if (userInfo.path("userId").canConvertToLong()) {
            account.setIboxUid(userInfo.path("userId").asLong());
        }
        if (StringUtils.hasText(userInfo.path("userName").asText())) {
            account.setAccountName(userInfo.path("userName").asText());
        }
        account.setBearerToken(data.path("token").asText());
        account.setRefreshToken(data.path("refreshToken").asText());
        account.setTokenExpiredAt(readLong(data.path("expiredAt")));
        account.setRefreshExpiredAt(readLong(data.path("refreshExpiredAt")));
        if (!iboxAccountService.updateById(account)) {
            throw new IllegalStateException("iBox登录成功，但账号信息保存失败");
        }
        iboxAccountCache.put(account);
        if (iboxAccountCache.getDefaultAccount() == null) {
            iboxAccountCache.setDefaultAccount(account);
        }

        IboxAccountAuthRes result = new IboxAccountAuthRes();
        BeanUtils.copyProperties(account, result);
        return result;
    }

    private Long readLong(JsonNode node) {
        return node != null && node.isNumber() ? node.asLong() : null;
    }
}
