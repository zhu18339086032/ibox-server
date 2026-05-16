package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 公告列表响应模型
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AnnouncementCategoryRes {

    /**
     * 名称
     */
    private String name;

}
