package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 市场藏品实体
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketCollectionRes {

    /**
     * 商品id
     */
    @JsonAlias({"itemId", "id"})
    private Long id;

    /**
     * 商品类型：album-商品，collection-合集。
     */
    private String itemType;

    /**
     * 藏品名称
     */
    private String name;

    /**
     * 封面图地址
     */
    @JsonAlias({"coverUrl", "coverPicUrl"})
    private String coverPicUrl;

    /**
     * 资源文件地址
     */
    private String fileUrl;

    /**
     * 发行总量
     */
    @JsonAlias({"issueAmount", "issueNum"})
    private Long issueNum;

    /**
     * 流通数量
     */
    @JsonAlias({"circulation", "circulationNum"})
    private Long circulationNum;

    /**
     * 地板价
     */
    private BigDecimal floorPrice;


    /**
     * 成交量
     */
    @JsonAlias({"volume", "tradeVolume"})
    private Long tradeVolume;


    /**
     * 是否关注
     */
    private Integer focus;

    /**
     * 新市场接口返回的关注状态为 Boolean，入库时转换为 0/1。
     */
    private Boolean focused;

    /**
     * 状态
     */
    private Integer status;
}
