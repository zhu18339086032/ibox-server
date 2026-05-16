package com.ibox.manager;

import com.ibox.model.req.web.IboxAccountPageReq;
import com.ibox.model.res.web.IboxAccountListRes;
import com.ibox.model.res.web.PageResponse;

/**
 * iBox账号服务接口
 */
public interface IboxAccountManager {

    /**
     * 账户列表接口
     *
     * @param req 查询条件
     * @return 账户分页列表
     */
    PageResponse<IboxAccountListRes> list(IboxAccountPageReq req);
}
