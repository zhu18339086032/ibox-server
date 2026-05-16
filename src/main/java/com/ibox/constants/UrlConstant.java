package com.ibox.constants;

/**
 * iBox閹恒儱褰涢崷鏉挎絻鐢悂鍣? */
public class UrlConstant {

    /**
     * iBox sail-api閺堝秴濮熼崺铏诡攨閸︽澘娼?     */
    public static final String SAIL_API_BASE_URL = "https://sail-api.ibox.art";

    /**
     * iBox 妞嬬偘鍔熼張鍝勬珤娴滃搫鐔€绾偓閸︽澘娼?     */
    public static final String FEISHU_BOT_BASE_URL = "https://open.feishu.cn";

    /**
     * 閸忣剙鎲￠崚妤勩€冮幒銉ュ經
     * GET https://sail-api.ibox.art/public-service/announcements?categoryId={categoryId}&pageNo={pageNo}&pageSize={pageSize}
     */
    public static final String ANNOUNCEMENT_LIST_URL = SAIL_API_BASE_URL + "/public-service/announcements?categoryId=%s&pageNo=%s&pageSize=%s";

    /**
     * 閸忣剙鎲＄拠锔藉剰閹恒儱褰?     * GET https://sail-api.ibox.art/public-service/announcements/uuid/{uuid}
     */
    public static final String ANNOUNCEMENT_DETAIL_URL = SAIL_API_BASE_URL + "/public-service/announcements/uuid/%s";

    /**
     * 推荐位列表接口
     * GET https://sail-api.ibox.art/public-service/home/activity-areas?areaType={areaType}
     */
    public static final String HOME_ACTIVITY_AREAS_URL = SAIL_API_BASE_URL + "/public-service/home/activity-areas?areaType=%s";

    /**
     * 鐢倸婧€閸掓銆冮幒銉ュ經
     * GET https://sail-api.ibox.art/public-service/markets?sortType=0&pageNo={pageNo}&segmentId=-1&sortField=2&pageSize={pageSize}&timeRange=0
     */
    public static final String MARKET_COLLECTION_LIST_URL = SAIL_API_BASE_URL + "/public-service/markets?sortType=0&pageNo=%s&segmentId=%s&sortField=2&pageSize=%s&timeRange=0";

    /**
     * 閸熷棗鎼ч崷銊ユ暛閸掓銆冮幒銉ュ經
     * GET /public-market-service/digital-collection-groups/{groupId}/consignment-orders?pageNo={pageNo}&pageSize={pageSize}&sortType={sortType}&sortField={sortField}&uid={uid}
     */
    public static final String CONSIGNMENT_ORDER_LIST_URL = "/public-market-service/digital-collection-groups/%s/consignment-orders?pageNo=%s&pageSize=%s&sortType=%s&sortField=%s&uid=%s";

    /**
     * 閸掓稑缂撻崡鏇氶嚋鐎靛嫬鏁拋銏犲礋閹恒儱褰?     * POST https://sail-api.ibox.art/order-create-service/purchase-consignment-orders
     */
    public static final String PURCHASE_CONSIGNMENT_ORDER_URL = SAIL_API_BASE_URL + "/order-create-service/purchase-consignment-orders";

    /**
     * 閹靛綊鍣洪崚娑樼紦鐎靛嫬鏁拋銏犲礋閹恒儱褰?     * POST https://sail-api.ibox.art/order-create-service/batch-purchase-consignment-orders?uid={uid}
     */
    public static final String BATCH_PURCHASE_CONSIGNMENT_ORDER_URL = SAIL_API_BASE_URL + "/order-create-service/batch-purchase-consignment-orders?uid=%s";

    /**
     * 璁㈠崟璇︽儏鎺ュ彛
     * GET https://sail-api.ibox.art/order-service/orders/{orderUuid}
     */
    public static final String ORDER_DETAIL_URL = SAIL_API_BASE_URL + "/order-service/orders/%s";

    /**
     * 妞嬬偘鍔熼張鍝勬珤娴滅ebhook閸︽澘娼?     * POST https://open.feishu.cn/open-apis/bot/v2/hook/{hookToken}
     */
    public static final String FEISHU_BOT_WEBHOOK_URL = FEISHU_BOT_BASE_URL + "/open-apis/bot/v2/hook/%s";

    private UrlConstant() {
    }
}
