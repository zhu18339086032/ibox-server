package com.ibox.manager.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.manager.IboxAccountManager;
import com.ibox.model.req.web.IboxAccountPageReq;
import com.ibox.model.res.web.IboxAccountListRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.service.IboxAccountService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
}
