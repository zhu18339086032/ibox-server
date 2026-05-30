package com.ibox.model.req.ibox;

import lombok.Data;

import java.util.List;

/**
 * 活动合成确认请求。
 */
@Data
public class SyntheticConfirmReq {

    /**
     * 合成通道 ID。
     */
    private Long syntheticId;

    /**
     * 本次提交的合成次数。
     * 第一版固定按 1 次执行，便于控制节奏和排查问题。
     */
    private Integer syntheticNum;

    /**
     * 本次合成使用的材料藏品 ID 列表。
     */
    private List<Long> preferentialAlbumIds;
}
