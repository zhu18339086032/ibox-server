package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单详情响应
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderDetailRes {

    private String createdAt;

    private Integer id;

    private Integer lockCount;

    private Integer orderStatus;

    private Integer orderType;

    private String orderUuid;

    private BigDecimal price;

    private ProductPreview productPreview;

    private List<ProductItem> products;

    private Integer quantity;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductPreview {
        private String coverPicUrl;
        private Integer level;
        private String name;
        private String tokenId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductItem {
        private String coverPicUrl;
        private Long digitalCollectionId;
        private Integer level;
        private String name;
        private Integer onChainStatus;
        private BigDecimal price;
        private Integer subId;
        private Long tokenId;
        private Integer transactionStatus;
        private String txHash;
    }
}
