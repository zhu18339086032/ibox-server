package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 推荐位项响应。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class HomeActivityAreaItemRes {

    private Integer areaType;

    private Long id;

    private String link;

    private Integer linkType;

    private String picUrl;

    private Integer sortNo;

    private String subTitle;

    private Long targetId;

    private String title;
}
