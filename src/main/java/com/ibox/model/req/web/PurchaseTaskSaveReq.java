package com.ibox.model.req.web;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 批量购买任务保存请求
 */
@Data
public class PurchaseTaskSaveReq {

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 商品ID
     */
    private Long digitalCollectionGroupId;

    /**
     * 商品名称
     */
    private String collectionName;

    /**
     * 账号ID列表
     */
    private List<Long> accountIds;

    /**
     * 最大购买数量
     */
    private Integer maxCount;

    /**
     * 最大价格
     */
    private BigDecimal maxSinglePrice;

    /**
     * 间隔频率（毫秒）
     */
    private Long intervalMillis;

    /**
     * 支付平台编码
     */
    private Integer paymentPlatformCode;

    /**
     * 任务类型，1-快速购买，2-批量购买
     */
    private Integer taskType;

    /**
     * 任务状态
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}
