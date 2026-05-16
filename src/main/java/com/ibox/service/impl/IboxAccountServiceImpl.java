package com.ibox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.mapper.IboxAccountMapper;
import com.ibox.model.req.web.IboxAccountPageReq;
import com.ibox.service.IboxAccountService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * iBox账号Service业务层处理
 */
@Service
public class IboxAccountServiceImpl extends ServiceImpl<IboxAccountMapper, IboxAccountEntity> implements IboxAccountService {

    /**
     * 账户列表接口
     *
     * @param req 账户查询条件
     * @return 账户分页列表
     */
    @Override
    public IPage<IboxAccountEntity> listIboxAccount(IboxAccountPageReq req) {
        LambdaQueryWrapper<IboxAccountEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(req.getAccountName()), IboxAccountEntity::getAccountName, req.getAccountName());
        queryWrapper.like(StringUtils.hasText(req.getPhone()), IboxAccountEntity::getPhone, req.getPhone());
        queryWrapper.orderByDesc(IboxAccountEntity::getId);
        return this.page(new Page<>(req.getPageNum(), req.getPageSize()), queryWrapper);
    }
}
