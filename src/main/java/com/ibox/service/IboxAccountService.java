package com.ibox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.model.req.web.IboxAccountPageReq;

/**
 * iBox账号服务接口
 */
public interface IboxAccountService extends IService<IboxAccountEntity> {

    /**
     * 账户列表接口
     *
     * @param req 账户查询条件
     * @return 账户分页列表
     */
    IPage<IboxAccountEntity> listIboxAccount(IboxAccountPageReq req);
}
