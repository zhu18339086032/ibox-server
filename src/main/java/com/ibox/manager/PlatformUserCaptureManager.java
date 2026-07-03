package com.ibox.manager;

import com.ibox.model.req.web.PlatformUserCaptureReq;
import com.ibox.model.res.web.PlatformUserCaptureRes;

/**
 * 平台用户抓取管理器
 */
public interface PlatformUserCaptureManager {

    /**
     * 抓取平台用户
     */
    PlatformUserCaptureRes capture(PlatformUserCaptureReq req);
}
