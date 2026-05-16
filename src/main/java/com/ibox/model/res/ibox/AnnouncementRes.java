package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 公告列表响应模型
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnnouncementRes {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 公告唯一标识
     */
    private String uuid;


    /**
     * 公告标题
     */
    private String title;

    /**
     * 公告内容，HTML格式
     */
    private String content;

    /**
     * 公告跳转地址
     */
    private String itemUrl;

    /**
     * 前置公告ID
     */
    private Long preAnnouncementId;

    /**
     * 展示状态
     */
    private Integer display;

    /**
     * 公告分类
     */
    private AnnouncementCategoryRes category;
}
