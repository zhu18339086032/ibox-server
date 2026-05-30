package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * 合成成功后的结果项。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyntheticConfirmResultRes {

    /**
     * 目标分组 ID。
     */
    private Long groupId;

    /**
     * 目标名称。
     */
    private String groupName;

    /**
     * 返回的 tokenId 列表。
     */
    private List<Long> tokenIds;
}
