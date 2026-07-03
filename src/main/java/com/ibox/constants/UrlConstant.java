package com.ibox.constants;

/**
 * iBox 接口地址常量。
 */
public class UrlConstant {

    /**
     * iBox sail-api 基础地址。
     */
    public static final String SAIL_API_BASE_URL = "https://sail-api.ibox.art";

    /**
     * 飞书机器人基础地址。
     */
    public static final String FEISHU_BOT_BASE_URL = "https://open.feishu.cn";

    /**
     * 公告列表接口
     * GET https://sail-api.ibox.art/public-service/announcements?categoryId={categoryId}&pageNo={pageNo}&pageSize={pageSize}
     */
    public static final String ANNOUNCEMENT_LIST_URL = SAIL_API_BASE_URL + "/public-service/announcements?categoryId=%s&pageNo=%s&pageSize=%s";

    /**
     * 公告详情接口
     * GET https://sail-api.ibox.art/public-service/announcements/uuid/{uuid}
     */
    public static final String ANNOUNCEMENT_DETAIL_URL = SAIL_API_BASE_URL + "/public-service/announcements/uuid/%s";

    /**
     * 推荐位列表接口
     * GET https://sail-api.ibox.art/public-service/home/activity-areas?areaType={areaType}
     */
    public static final String HOME_ACTIVITY_AREAS_URL = SAIL_API_BASE_URL + "/public-service/home/activity-areas?areaType=%s";

    /**
     * 市场藏品列表接口
     * GET https://sail-api.ibox.art/public-service/markets?sortType=0&pageNo={pageNo}&segmentId=-1&sortField=2&pageSize={pageSize}&timeRange=0
     */
    public static final String MARKET_COLLECTION_LIST_URL = SAIL_API_BASE_URL + "/public-service/markets?sortType=0&pageNo=%s&segmentId=%s&sortField=2&pageSize=%s&timeRange=0";

    /**
     * 寄售订单列表接口
     * GET /public-market-service/digital-collection-groups/{groupId}/consignment-orders?pageNo={pageNo}&pageSize={pageSize}&sortType={sortType}&sortField={sortField}&uid={uid}
     */
    public static final String CONSIGNMENT_ORDER_LIST_URL = SAIL_API_BASE_URL + "/public-market-service/digital-collection-groups/%s/consignment-orders?pageNo=%s&pageSize=%s&sortType=%s&sortField=%s&uid=%s";

    public static final String CONSIGNMENT_ORDER_LIST_WITH_SORT_VALUES_URL = SAIL_API_BASE_URL + "/public-market-service/digital-collection-groups/%s/consignment-orders?pageNo=%s&pageSize=%s&sortField=%s&sortType=%s&sortValues=%s";

    /**
     * 求购单列表接口
     * GET /public-market-service/digital-collection-groups/{groupId}/purchase-orders?pageNo={pageNo}&pageSize={pageSize}&uid={uid}
     */
    public static final String PURCHASE_ORDER_LIST_URL = SAIL_API_BASE_URL + "/public-market-service/digital-collection-groups/%s/purchase-orders?pageNo=%s&pageSize=%s&uid=%s";

    /**
     * 出售求购成交接口
     * POST /order-create-service/advance-orders/{advanceOrderId}/relation/{orderRelationId}/deal?uid={uid}
     */
    public static final String DEAL_PURCHASE_ORDER_URL = SAIL_API_BASE_URL + "/order-create-service/advance-orders/%s/relation/%s/deal?uid=%s";

    /**
     * 创建单个寄售订单接口
     * POST https://sail-api.ibox.art/order-create-service/purchase-consignment-orders
     */
    public static final String PURCHASE_CONSIGNMENT_ORDER_URL = SAIL_API_BASE_URL + "/order-create-service/purchase-consignment-orders";

    /**
     * 创建批量寄售订单接口
     * POST https://sail-api.ibox.art/order-create-service/batch-purchase-consignment-orders?uid={uid}
     */
    public static final String BATCH_PURCHASE_CONSIGNMENT_ORDER_URL = SAIL_API_BASE_URL + "/order-create-service/batch-purchase-consignment-orders?uid=%s";

    /**
     * 订单详情接口
     * GET https://sail-api.ibox.art/order-service/orders/{orderUuid}
     */
    public static final String ORDER_DETAIL_URL = SAIL_API_BASE_URL + "/order-service/orders/%s";

    /**
     * 获取订单支付信息接口
     * GET https://sail-api.ibox.art/payment-service/cashiers/gain?orderUUId={orderUuid}&paymentInitiatorType={paymentInitiatorType}
     */
    public static final String ORDER_PAYMENT_INFO_URL = SAIL_API_BASE_URL + "/payment-service/cashiers/gain?orderUUId=%s&paymentInitiatorType=%s";

    /**
     * 待支付订单列表接口
     * GET https://sail-api.ibox.art/order-service/orders?pageNo={pageNo}&pageSize={pageSize}&orderStatus=0&productType=0&orderType=0
     */
    public static final String ORDERS_URL = SAIL_API_BASE_URL + "/order-service/orders?pageNo=%s&pageSize=%s&orderStatus=0&productType=0&orderType=0";

    /**
     * 订单详情
     * GET /order-service/orders/${orderId}
     */
    public static final String ORDERS_DETAIL_URL = SAIL_API_BASE_URL + "/order-service/orders/%s";

    /**
     * 平台用户仓库商品列表接口
     * GET /personal-center-service/users/{userId}/digital-collection-groups?pageNo={pageNo}&pageSize={pageSize}&groupType={groupType}
     */
    public static final String PLATFORM_USER_COLLECTION_GROUPS_URL =
            SAIL_API_BASE_URL + "/personal-center-service/users/%s/digital-collection-groups?pageNo=%s&pageSize=%s&groupType=%s";

    /**
     * 活动合成列表接口
     * GET https://sail-api.ibox.art/synthesis-service/synthetic/activity/list?pageNo={pageNo}&pageSize={pageSize}
     */
    public static final String SYNTHETIC_ACTIVITY_LIST_URL = SAIL_API_BASE_URL + "/synthesis-service/synthetic/activity/list?pageNo=%s&pageSize=%s";

    /**
     * 活动详情接口
     * GET https://sail-api.ibox.art/synthesis-service/synthetic/activity/detail?id={activityId}
     */
    public static final String SYNTHETIC_ACTIVITY_DETAIL_URL = SAIL_API_BASE_URL + "/synthesis-service/synthetic/activity/detail?id=%s";

    /**
     * 合成中心详情接口
     * GET https://sail-api.ibox.art/synthesis-service/synthetic/center/%s
     */
    public static final String SYNTHETIC_CENTER_DETAIL_URL = SAIL_API_BASE_URL + "/synthesis-service/synthetic/center/%s";

    /**
     * 合成确认接口
     * POST https://sail-api.ibox.art/synthesis-service/synthetic/center/confirm?uid={uid}
     */
    public static final String SYNTHETIC_CONFIRM_URL = SAIL_API_BASE_URL + "/synthesis-service/synthetic/center/confirm?uid=%s";

    private UrlConstant() {
    }
}
