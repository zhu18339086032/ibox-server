package com.ibox.model.req.web;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 新增寄售任务请求
 */
@Data
public class ConsignmentTaskSaveReq {

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * 账号ID
     */
    private Long accountId;

    /**
     * 商品组ID
     */
    private Long digitalCollectionGroupId;

    /**
     * 商品名称
     */
    private String collectionName;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 目标数量
     */
    private Integer targetCount;

    /**
     * 间隔毫秒
     */
    private Long intervalMillis;

    /**
     * 上架价格
     */
    private BigDecimal consignPrice;

    /**
     * 任务状态
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}
