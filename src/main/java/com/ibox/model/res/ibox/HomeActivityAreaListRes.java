package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 推荐位列表响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HomeActivityAreaListRes {

    private Long total;

    private Boolean hasMore;

    private Integer isShown;

    private List<HomeActivityAreaItemRes> list;
}
