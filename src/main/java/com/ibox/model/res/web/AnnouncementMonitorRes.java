package com.ibox.model.res.web;

import lombok.Data;

import java.util.List;

/**
 * 公告监控响应
 */
@Data
public class AnnouncementMonitorRes {

    /**
     * 公告ID
     */
    private Long announcementId;

    /**
     * 公告UUID
     */
    private String announcementUuid;

    /**
     * 公告标题
     */
    private String announcementTitle;

    /**
     * 公告正文
     */
    private String announcementContent;

    /**
     * 是否是最新公告
     */
    private Boolean latest = false;

    /**
     * 解析出的藏品名称列表
     */
    private List<String> collectionNames;

    /**
     * 匹配到的市场藏品列表
     */
    private List<MatchedMarketCollectionRes> matchedCollections;

    /**
     * 购买成功数量
     */
    private Integer purchaseCount = 0;

    /**
     * 购买订单ID列表
     */
    private List<String> purchaseOrderIds;

    /**
     * 结果消息
     */
    private String message;
}
