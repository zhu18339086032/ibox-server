package com.ibox.model.res.ibox;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 完整订单详情响应模型。
 * 对应订单详情接口返回的完整 data 报文结构。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderDetailFullRes {

    /**
     * 垫资条目列表。
     */
    private List<Object> advanceItems;

    /**
     * 创建时间。
     */
    private String createdAt;

    /**
     * 销毁藏品列表。
     */
    private List<Object> destroyDcs;

    /**
     * 过期时间。
     */
    private String expiredAt;

    /**
     * 订单 ID。
     */
    private Long id;

    /**
     * 发起方类型。
     */
    private Integer initiatorType;

    /**
     * 是否批量购买。
     */
    private Integer isBatchBuy;

    /**
     * 多目标列表标记。
     */
    private List<Object> isMultiTargetList;

    /**
     * 锁定数量。
     */
    private Integer lockCount;

    /**
     * 市场类型。
     */
    private Integer marketType;

    /**
     * 订单事件列表。
     */
    private List<OrderEventItem> orderEvents;

    /**
     * 订单状态。
     */
    private Integer orderStatus;

    /**
     * 订单类型。
     */
    private Integer orderType;

    /**
     * 订单唯一标识。
     */
    private String orderUuid;

    /**
     * 部分退款信息。
     */
    private Object partialRefund;

    /**
     * 支付信息。
     */
    private PaymentInfoVo paymentInfoVo;

    /**
     * 订单总价。
     */
    private BigDecimal price;

    /**
     * 商品预览信息。
     */
    private ProductPreview productPreview;

    /**
     * 商品类型。
     */
    private Integer productType;

    /**
     * 商品明细列表。
     */
    private List<ProductItem> products;

    /**
     * 商品数量。
     */
    private Integer quantity;

    /**
     * 实收金额。
     */
    private BigDecimal receivedPrice;

    /**
     * 退款垫资条目列表。
     */
    private List<Object> refundAdvanceItems;

    /**
     * 已退款金额。
     */
    private BigDecimal refundedPrice;

    /**
     * 单价。
     */
    private BigDecimal singlePrice;

    /**
     * 支持的支付方式列表。
     */
    private List<Object> supportPayments;

    /**
     * 交易流水号。
     */
    private String transactionNumber;

    /**
     * 即将锁定时间。
     */
    private Integer willLockTime;

    /**
     * 订单事件。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderEventItem {
        /**
         * 事件创建时间。
         */
        private String createdAt;

        /**
         * 事件对应的订单状态。
         */
        private Integer orderStatus;

        /**
         * 事件说明。
         */
        private String remark;
    }

    /**
     * 支付信息。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentInfoVo {
        /**
         * 支付方式。
         */
        private Integer paymentMethod;

        /**
         * 支付 UUID。
         */
        private String uuid;

        /**
         * 钱包名称。
         */
        private String walletAddressName;
    }

    /**
     * 商品预览信息。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductPreview {
        /**
         * 封面图地址。
         */
        private String coverPicUrl;

        /**
         * 等级。
         */
        private Integer level;

        /**
         * 商品名称。
         */
        private String name;

        /**
         * Token ID。
         */
        private String tokenId;
    }

    /**
     * 商品明细。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProductItem {
        /**
         * 封面图地址。
         */
        private String coverPicUrl;

        /**
         * 数字藏品 ID。
         */
        private Long digitalCollectionId;

        /**
         * 等级。
         */
        private Integer level;

        /**
         * 商品名称。
         */
        private String name;

        /**
         * 上链状态。
         */
        private Integer onChainStatus;

        /**
         * 商品价格。
         */
        private BigDecimal price;

        /**
         * 子 ID。
         */
        private Integer subId;

        /**
         * Token ID。
         */
        private Long tokenId;

        /**
         * 交易状态。
         */
        private Integer transactionStatus;

        /**
         * 交易哈希。
         */
        private String txHash;
    }
}
