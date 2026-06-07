package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 我的藏品单个列表返回模型
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDigitalCollectionGroupPageRes {
    private Boolean hasMore;
    private Integer total;
    private List<UserDigitalCollectionItemRes> list;

}