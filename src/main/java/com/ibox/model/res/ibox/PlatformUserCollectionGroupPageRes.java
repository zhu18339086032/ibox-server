package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 平台用户抓取专用-用户仓库分页响应
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlatformUserCollectionGroupPageRes {

    /**
     * 是否还有更多
     */
    private Boolean hasMore;

    /**
     * 总数
     */
    private Integer total;

    /**
     * 商品列表
     */
    private List<PlatformUserCollectionGroupItemRes> list;
}
