package com.ibox.model.req.web;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 监控任务配置保存请求。
 */
@Data
public class IboxMonitorTaskConfigSaveReq {

    /**
     * 任务名称。
     */
    private String taskName;

    /**
     * 监控类型。
     */
    private String monitorType;

    /**
     * 账号ID列表。
     */
    private List<Long> accountIds;

    /**
     * 最大次数。
     */
    private Integer maxCount;

    /**
     * 最大价格。
     */
    private BigDecimal maxPrice;

    /**
     * 状态。
     */
    private Integer status;

    /**
     * 支付平台编码。
     */
    private Integer paymentPlatformCode;

    /**
     * 备注。
     */
    private String remark;
}
