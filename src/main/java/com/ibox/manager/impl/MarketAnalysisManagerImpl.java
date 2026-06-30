package com.ibox.manager.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.common.IboxAccountCache;
import com.ibox.common.MarketAnalysisTaskManager;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.entity.MarketCollectionEntity;
import com.ibox.manager.MarketAnalysisManager;
import com.ibox.model.req.web.ConsignmentOrderAnalysisReq;
import com.ibox.model.res.ibox.AnalysisConsignmentOrderListRes;
import com.ibox.model.res.ibox.AnalysisConsignmentOrderRes;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.web.ConsignmentOrderAnalysisRes;
import com.ibox.model.res.web.ConsignmentOrderAnalysisTaskRes;
import com.ibox.service.IboxAccountService;
import com.ibox.service.MarketCollectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 市场分析实现
 */
@Service
@Slf4j
public class MarketAnalysisManagerImpl implements MarketAnalysisManager {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int DEFAULT_MAX_PAGES = 10;

    private static final int MAX_ALLOWED_PAGES = 50;

    private static final long DEFAULT_PAGE_INTERVAL_MILLIS = 200L;

    private static final int CONSIGNMENT_SORT_FIELD = 1;

    private static final int CONSIGNMENT_SORT_TYPE = 1;

    @Autowired
    private IboxClient iboxClient;

    @Autowired
    private IboxAccountCache iboxAccountCache;

    @Autowired
    private IboxAccountService iboxAccountService;

    @Autowired
    private MarketCollectionService marketCollectionService;

    @Autowired
    private MarketAnalysisTaskManager marketAnalysisTaskManager;

    private void validateReq(ConsignmentOrderAnalysisReq req) {
        if (req == null || req.getDigitalCollectionGroupId() == null) {
            throw new IllegalArgumentException("商品ID不能为空");
        }
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, 100);
    }

    private int normalizeMaxPages(Integer maxPages) {
        if (maxPages == null || maxPages <= 0) {
            return DEFAULT_MAX_PAGES;
        }
        return Math.min(maxPages, MAX_ALLOWED_PAGES);
    }

    private long normalizePageInterval(Long pageIntervalMillis) {
        return pageIntervalMillis == null || pageIntervalMillis < 0 ? DEFAULT_PAGE_INTERVAL_MILLIS : pageIntervalMillis;
    }

    @Override
    public ConsignmentOrderAnalysisRes analyzeConsignmentOrders(ConsignmentOrderAnalysisReq req) {
        validateReq(req);

        IboxAccountEntity account = getDefaultAccount();
        if (account == null || !StringUtils.hasText(account.getBearerToken()) || account.getIboxUid() == null) {
            throw new IllegalStateException("默认账号不存在或 token/uid 不可用");
        }

        int pageSize = normalizePageSize(req.getPageSize());
        int maxPages = normalizeMaxPages(req.getMaxPages());
        long pageIntervalMillis = normalizePageInterval(req.getPageIntervalMillis());
        log.info("【在售分析】开始同步分析，groupId:{}, pageSize:{}, maxPages:{}, pageIntervalMillis:{}",
                req.getDigitalCollectionGroupId(), pageSize, maxPages, pageIntervalMillis);

        List<AnalysisConsignmentOrderRes> rawOrders = new ArrayList<>();
        int fetchedPageCount = 0;
        String nextSortValues = null;
        for (int pageNo = 1; pageNo <= maxPages; pageNo++) {
            IboxApiResponse<AnalysisConsignmentOrderListRes> response = fetchConsignmentOrderPageDirect(
                    req.getDigitalCollectionGroupId(),
                    pageNo,
                    pageSize,
                    account,
                    nextSortValues
            );
            if (response == null || response.getCode() == null || response.getCode() != 0 || response.getData() == null || CollectionUtils.isEmpty(response.getData().getList())) {
                log.info("【在售分析】分页抓取提前结束，groupId:{}, pageNo:{}, reason:{}, responseCode:{}, hasData:{}",
                        req.getDigitalCollectionGroupId(),
                        pageNo,
                        resolveStopReason(response),
                        response == null ? null : response.getCode(),
                        response != null && response.getData() != null);
                break;
            }
            List<AnalysisConsignmentOrderRes> currentPageList = response.getData().getList();
            int beforeSize = deduplicateOrders(rawOrders).size();
            rawOrders.addAll(currentPageList);
            int afterSize = deduplicateOrders(rawOrders).size();
            log.info("【在售分析】分页抓取结果，groupId:{}, pageNo:{}, pageListSize:{}, hasMore:{}, deduplicatedBefore:{}, deduplicatedAfter:{}",
                    req.getDigitalCollectionGroupId(),
                    pageNo,
                    currentPageList.size(),
                    response.getData().isHasMore(),
                    beforeSize,
                    afterSize);
            log.info("【在售分析】分页抓取样本，groupId:{}, pageNo:{}, sampleOrders:{}, responseSortValues:{}",
                    req.getDigitalCollectionGroupId(),
                    pageNo,
                    extractSampleOrderKeys(currentPageList),
                    extractNextSortValues(response.getData()));
            if (afterSize == beforeSize) {
                log.info("【在售分析】检测到重复页，提前结束，groupId:{}, pageNo:{}, deduplicatedSize:{}",
                        req.getDigitalCollectionGroupId(), pageNo, afterSize);
                break;
            }
            fetchedPageCount++;
            nextSortValues = extractNextSortValues(response.getData());
            if (!response.getData().isHasMore()) {
                log.info("【在售分析】接口返回无更多数据，结束抓取，groupId:{}, pageNo:{}, fetchedPageCount:{}",
                        req.getDigitalCollectionGroupId(), pageNo, fetchedPageCount);
                break;
            }
            if (!StringUtils.hasText(nextSortValues)) {
                log.info("【在售分析】接口未返回下一页 sortValues，结束抓取，groupId:{}, pageNo:{}, fetchedPageCount:{}",
                        req.getDigitalCollectionGroupId(), pageNo, fetchedPageCount);
                break;
            }
            sleep(pageIntervalMillis);
        }

        log.info("【在售分析】同步分析抓取完成，groupId:{}, fetchedPageCount:{}, rawOrderCount:{}",
                req.getDigitalCollectionGroupId(), fetchedPageCount, rawOrders.size());
        return buildAnalysisResult(req.getDigitalCollectionGroupId(), rawOrders, fetchedPageCount);
    }

    @Override
    public ConsignmentOrderAnalysisTaskRes startAnalyzeConsignmentOrders(ConsignmentOrderAnalysisReq req) {
        validateReq(req);
        MarketAnalysisTaskManager.AnalysisTaskSnapshot snapshot = marketAnalysisTaskManager.createTask();
        String taskId = snapshot.getTaskId();
        marketAnalysisTaskManager.submit(taskId, () -> {
            try {
                marketAnalysisTaskManager.markRunning(taskId, 5, "开始分析");
                ConsignmentOrderAnalysisRes result = analyzeConsignmentOrdersWithProgress(req, taskId);
                marketAnalysisTaskManager.markSuccess(taskId, result);
            } catch (Exception e) {
                marketAnalysisTaskManager.markFail(taskId, "分析失败:" + safeText(e.getMessage()));
            }
        });
        return buildTaskRes(snapshot);
    }

    @Override
    public ConsignmentOrderAnalysisTaskRes getAnalyzeTask(String taskId) {
        MarketAnalysisTaskManager.AnalysisTaskSnapshot snapshot = marketAnalysisTaskManager.getTask(taskId);
        if (snapshot == null) {
            throw new IllegalArgumentException("分析任务不存在");
        }
        return buildTaskRes(snapshot);
    }

    private ConsignmentOrderAnalysisRes analyzeConsignmentOrdersWithProgress(ConsignmentOrderAnalysisReq req, String taskId) {
        validateReq(req);

        IboxAccountEntity account = getDefaultAccount();
        if (account == null || !StringUtils.hasText(account.getBearerToken()) || account.getIboxUid() == null) {
            throw new IllegalStateException("默认账号不存在或 token/uid 不可用");
        }

        int pageSize = normalizePageSize(req.getPageSize());
        int maxPages = normalizeMaxPages(req.getMaxPages());
        long pageIntervalMillis = normalizePageInterval(req.getPageIntervalMillis());
        log.info("【在售分析】开始异步分析，taskId:{}, groupId:{}, pageSize:{}, maxPages:{}, pageIntervalMillis:{}",
                taskId, req.getDigitalCollectionGroupId(), pageSize, maxPages, pageIntervalMillis);

        List<AnalysisConsignmentOrderRes> rawOrders = new ArrayList<>();
        int fetchedPageCount = 0;
        String nextSortValues = null;
        for (int pageNo = 1; pageNo <= maxPages; pageNo++) {
            int progress = Math.min(10 + (pageNo - 1) * 70 / Math.max(maxPages, 1), 80);
            marketAnalysisTaskManager.markRunning(taskId, progress, "正在抓取第" + pageNo + "页");
            IboxApiResponse<AnalysisConsignmentOrderListRes> response = fetchConsignmentOrderPageDirect(
                    req.getDigitalCollectionGroupId(),
                    pageNo,
                    pageSize,
                    account,
                    nextSortValues
            );
            if (response == null || response.getCode() == null || response.getCode() != 0 || response.getData() == null || CollectionUtils.isEmpty(response.getData().getList())) {
                log.info("【在售分析】异步分页抓取提前结束，taskId:{}, groupId:{}, pageNo:{}, reason:{}, responseCode:{}, hasData:{}",
                        taskId,
                        req.getDigitalCollectionGroupId(),
                        pageNo,
                        resolveStopReason(response),
                        response == null ? null : response.getCode(),
                        response != null && response.getData() != null);
                break;
            }
            List<AnalysisConsignmentOrderRes> currentPageList = response.getData().getList();
            int beforeSize = deduplicateOrders(rawOrders).size();
            rawOrders.addAll(currentPageList);
            int afterSize = deduplicateOrders(rawOrders).size();
            log.info("【在售分析】异步分页抓取结果，taskId:{}, groupId:{}, pageNo:{}, pageListSize:{}, hasMore:{}, deduplicatedBefore:{}, deduplicatedAfter:{}",
                    taskId,
                    req.getDigitalCollectionGroupId(),
                    pageNo,
                    currentPageList.size(),
                    response.getData().isHasMore(),
                    beforeSize,
                    afterSize);
            log.info("【在售分析】异步分页抓取样本，taskId:{}, groupId:{}, pageNo:{}, sampleOrders:{}, responseSortValues:{}",
                    taskId,
                    req.getDigitalCollectionGroupId(),
                    pageNo,
                    extractSampleOrderKeys(currentPageList),
                    extractNextSortValues(response.getData()));
            if (afterSize == beforeSize) {
                marketAnalysisTaskManager.markRunning(taskId, progress, "检测到重复页，提前结束抓取");
                log.info("【在售分析】异步检测到重复页，提前结束，taskId:{}, groupId:{}, pageNo:{}, deduplicatedSize:{}",
                        taskId, req.getDigitalCollectionGroupId(), pageNo, afterSize);
                break;
            }
            fetchedPageCount++;
            nextSortValues = extractNextSortValues(response.getData());
            if (!response.getData().isHasMore()) {
                log.info("【在售分析】异步接口返回无更多数据，结束抓取，taskId:{}, groupId:{}, pageNo:{}, fetchedPageCount:{}",
                        taskId, req.getDigitalCollectionGroupId(), pageNo, fetchedPageCount);
                break;
            }
            if (!StringUtils.hasText(nextSortValues)) {
                log.info("【在售分析】异步接口未返回下一页 sortValues，结束抓取，taskId:{}, groupId:{}, pageNo:{}, fetchedPageCount:{}",
                        taskId, req.getDigitalCollectionGroupId(), pageNo, fetchedPageCount);
                break;
            }
            sleep(pageIntervalMillis);
        }

        marketAnalysisTaskManager.markRunning(taskId, 85, "正在聚合分析结果");
        ConsignmentOrderAnalysisRes res = buildAnalysisResult(req.getDigitalCollectionGroupId(), rawOrders, fetchedPageCount);
        marketAnalysisTaskManager.markRunning(taskId, 95, "正在生成买入建议");
        log.info("【在售分析】异步分析抓取完成，taskId:{}, groupId:{}, fetchedPageCount:{}, rawOrderCount:{}, deduplicatedOrderCount:{}",
                taskId,
                req.getDigitalCollectionGroupId(),
                fetchedPageCount,
                rawOrders.size(),
                res.getDeduplicatedOrderCount());
        return res;
    }

    private IboxApiResponse<AnalysisConsignmentOrderListRes> fetchConsignmentOrderPageDirect(Long groupId,
                                                                                              int pageNo,
                                                                                              int pageSize,
                                                                                              IboxAccountEntity account,
                                                                                              String sortValues) {
        String encodedSortValues = StringUtils.hasText(sortValues)
                ? URLEncoder.encode(sortValues, StandardCharsets.UTF_8)
                : "";
        String url = String.format(
                UrlConstant.CONSIGNMENT_ORDER_LIST_WITH_SORT_VALUES_URL,
                groupId,
                pageNo,
                pageSize,
                CONSIGNMENT_SORT_FIELD,
                CONSIGNMENT_SORT_TYPE,
                encodedSortValues
        );
        log.info("【在售分析】请求在售分页，groupId:{}, pageNo:{}, pageSize:{}, sortValues:{}, url:{}",
                groupId, pageNo, pageSize, sortValues, url);
        return iboxClient.get(account.getBearerToken(), url, null, new TypeReference<AnalysisConsignmentOrderListRes>() {
        });
    }

    private List<AnalysisConsignmentOrderRes> deduplicateOrders(List<AnalysisConsignmentOrderRes> rawOrders) {
        if (CollectionUtils.isEmpty(rawOrders)) {
            return List.of();
        }
        Map<String, AnalysisConsignmentOrderRes> map = new LinkedHashMap<>();
        for (AnalysisConsignmentOrderRes order : rawOrders) {
            if (order == null) {
                continue;
            }
            String key = StringUtils.hasText(order.getOrderUuid())
                    ? order.getOrderUuid()
                    : safeText(order.getId()) + "_" + safeText(order.getDigitalCollectionId()) + "_" + safeText(order.getPrice());
            map.putIfAbsent(key, order);
        }
        return new ArrayList<>(map.values());
    }

    private ConsignmentOrderAnalysisRes buildAnalysisResult(Long digitalCollectionGroupId, List<AnalysisConsignmentOrderRes> rawOrders, int fetchedPageCount) {
        List<AnalysisConsignmentOrderRes> deduplicatedOrders = deduplicateOrders(rawOrders).stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getPrice() != null)
                .sorted(Comparator.comparing(AnalysisConsignmentOrderRes::getPrice).thenComparing(AnalysisConsignmentOrderRes::getId, Comparator.nullsLast(Long::compareTo)))
                .toList();

        ConsignmentOrderAnalysisRes res = new ConsignmentOrderAnalysisRes();
        res.setDigitalCollectionGroupId(digitalCollectionGroupId);
        res.setCollectionName(resolveCollectionName(digitalCollectionGroupId, deduplicatedOrders));
        res.setFetchedPageCount(fetchedPageCount);
        res.setRawOrderCount(rawOrders.size());
        res.setDeduplicatedOrderCount(deduplicatedOrders.size());

        if (deduplicatedOrders.isEmpty()) {
            res.setPriceBuckets(List.of());
            res.setPressureRanges(List.of());
            res.setBuySuggestion(buildEmptySuggestion());
            return res;
        }

        res.setFloorPrice(deduplicatedOrders.get(0).getPrice());
        res.setAveragePrice(calculateAveragePrice(deduplicatedOrders));
        res.setMedianPrice(calculateMedianPrice(deduplicatedOrders));
        res.setTop20AveragePrice(calculateTopAveragePrice(deduplicatedOrders, 20));
        res.setTop50AveragePrice(calculateTopAveragePrice(deduplicatedOrders, 50));
        res.setTop100AveragePrice(calculateTopAveragePrice(deduplicatedOrders, 100));
        res.setTop200AveragePrice(calculateTopAveragePrice(deduplicatedOrders, 200));
        res.setTop500AveragePrice(calculateTopAveragePrice(deduplicatedOrders, 500));
        res.setPriceBuckets(buildPriceBuckets(deduplicatedOrders));
        res.setPressureRanges(buildPressureRanges(deduplicatedOrders));
        res.setBuySuggestion(buildBuySuggestion(res));
        return res;
    }

    private ConsignmentOrderAnalysisTaskRes buildTaskRes(MarketAnalysisTaskManager.AnalysisTaskSnapshot snapshot) {
        ConsignmentOrderAnalysisTaskRes res = new ConsignmentOrderAnalysisTaskRes();
        res.setTaskId(snapshot.getTaskId());
        res.setStatus(snapshot.getStatus());
        res.setProgress(snapshot.getProgress());
        res.setMessage(snapshot.getMessage());
        res.setResult(snapshot.getResult());
        res.setCreatedAt(snapshot.getCreatedAt());
        res.setUpdatedAt(snapshot.getUpdatedAt());
        return res;
    }

    private String resolveCollectionName(Long groupId, List<AnalysisConsignmentOrderRes> orders) {
        if (!CollectionUtils.isEmpty(orders)) {
            AnalysisConsignmentOrderRes first = orders.get(0);
            if (first.getDigitalCollection() != null && StringUtils.hasText(first.getDigitalCollection().getName())) {
                return first.getDigitalCollection().getName();
            }
        }
        MarketCollectionEntity entity = marketCollectionService.getOne(
                new LambdaQueryWrapper<MarketCollectionEntity>()
                        .eq(MarketCollectionEntity::getId, groupId)
                        .last("limit 1")
        );
        return entity == null ? String.valueOf(groupId) : entity.getName();
    }

    private BigDecimal calculateAveragePrice(List<AnalysisConsignmentOrderRes> orders) {
        BigDecimal total = BigDecimal.ZERO;
        for (AnalysisConsignmentOrderRes order : orders) {
            total = total.add(order.getPrice());
        }
        return divide(total, orders.size());
    }

    private BigDecimal calculateMedianPrice(List<AnalysisConsignmentOrderRes> orders) {
        int size = orders.size();
        if (size == 0) {
            return BigDecimal.ZERO;
        }
        if (size % 2 == 1) {
            return orders.get(size / 2).getPrice();
        }
        return orders.get(size / 2 - 1).getPrice()
                .add(orders.get(size / 2).getPrice())
                .divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTopAveragePrice(List<AnalysisConsignmentOrderRes> orders, int topN) {
        List<AnalysisConsignmentOrderRes> topList = orders.stream().limit(topN).toList();
        if (topList.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (AnalysisConsignmentOrderRes order : topList) {
            total = total.add(order.getPrice());
        }
        return divide(total, topList.size());
    }

    private List<ConsignmentOrderAnalysisRes.PriceBucketRes> buildPriceBuckets(List<AnalysisConsignmentOrderRes> orders) {
        Map<BigDecimal, List<AnalysisConsignmentOrderRes>> bucketMap = new LinkedHashMap<>();
        for (AnalysisConsignmentOrderRes order : orders) {
            bucketMap.computeIfAbsent(order.getPrice(), key -> new ArrayList<>()).add(order);
        }
        List<ConsignmentOrderAnalysisRes.PriceBucketRes> result = new ArrayList<>();
        int cumulativeOrderCount = 0;
        for (Map.Entry<BigDecimal, List<AnalysisConsignmentOrderRes>> entry : bucketMap.entrySet()) {
            List<AnalysisConsignmentOrderRes> bucketOrders = entry.getValue();
            cumulativeOrderCount += bucketOrders.size();

            ConsignmentOrderAnalysisRes.PriceBucketRes bucketRes = new ConsignmentOrderAnalysisRes.PriceBucketRes();
            bucketRes.setPrice(entry.getKey());
            bucketRes.setOrderCount(bucketOrders.size());
            bucketRes.setCumulativeOrderCount(cumulativeOrderCount);
            result.add(bucketRes);
        }
        return result;
    }

    private List<ConsignmentOrderAnalysisRes.PressureRangeRes> buildPressureRanges(List<AnalysisConsignmentOrderRes> orders) {
        BigDecimal floorPrice = orders.get(0).getPrice();
        List<BigDecimal> percentList = List.of(
                new BigDecimal("0.01"),
                new BigDecimal("0.03"),
                new BigDecimal("0.05"),
                new BigDecimal("0.10"),
                new BigDecimal("0.15")
        );
        List<ConsignmentOrderAnalysisRes.PressureRangeRes> result = new ArrayList<>();
        for (BigDecimal percent : percentList) {
            BigDecimal upperPrice = floorPrice.multiply(BigDecimal.ONE.add(percent)).setScale(2, RoundingMode.HALF_UP);
            List<AnalysisConsignmentOrderRes> rangeOrders = orders.stream()
                    .filter(item -> item.getPrice().compareTo(upperPrice) <= 0)
                    .toList();

            ConsignmentOrderAnalysisRes.PressureRangeRes rangeRes = new ConsignmentOrderAnalysisRes.PressureRangeRes();
            rangeRes.setRangeLabel("0%~" + percent.movePointRight(2).stripTrailingZeros().toPlainString() + "%");
            rangeRes.setUpperPrice(upperPrice);
            rangeRes.setOrderCount(rangeOrders.size());
            result.add(rangeRes);
        }
        return result;
    }

    private ConsignmentOrderAnalysisRes.BuySuggestionRes buildBuySuggestion(ConsignmentOrderAnalysisRes analysisRes) {
        ConsignmentOrderAnalysisRes.BuySuggestionRes suggestionRes = new ConsignmentOrderAnalysisRes.BuySuggestionRes();
        List<String> reasons = new ArrayList<>();
        int score = 100;

        if (!CollectionUtils.isEmpty(analysisRes.getPressureRanges())) {
            ConsignmentOrderAnalysisRes.PressureRangeRes fivePercentRange = analysisRes.getPressureRanges().stream()
                    .filter(item -> "0%~5%".equals(item.getRangeLabel()))
                    .findFirst()
                    .orElse(null);
            if (fivePercentRange != null && fivePercentRange.getOrderCount() != null && fivePercentRange.getOrderCount() > 120) {
                score -= 20;
                reasons.add("最低价上方5%区间卖压偏重");
            }
        }

        if (score >= 80) {
            suggestionRes.setLevel("RECOMMENDED");
            reasons.add("前排卖压相对可控，可考虑积极买入");
        } else if (score >= 60) {
            suggestionRes.setLevel("CAUTIOUS");
            reasons.add("可谨慎买入，建议贴近前排价格成交");
        } else {
            suggestionRes.setLevel("NOT_RECOMMENDED");
            reasons.add("卖压结构偏重，不建议激进买入");
        }

        BigDecimal floorPrice = analysisRes.getFloorPrice() == null ? BigDecimal.ZERO : analysisRes.getFloorPrice();
        suggestionRes.setScore(Math.max(score, 0));
        suggestionRes.setStrongSafeMinPrice(floorPrice);
        suggestionRes.setStrongSafeMaxPrice(floorPrice.multiply(new BigDecimal("1.02")).setScale(2, RoundingMode.HALF_UP));
        suggestionRes.setAcceptableMinPrice(floorPrice);
        suggestionRes.setAcceptableMaxPrice(floorPrice.multiply(new BigDecimal("1.05")).setScale(2, RoundingMode.HALF_UP));
        suggestionRes.setReasons(reasons);
        return suggestionRes;
    }

    private ConsignmentOrderAnalysisRes.BuySuggestionRes buildEmptySuggestion() {
        ConsignmentOrderAnalysisRes.BuySuggestionRes suggestionRes = new ConsignmentOrderAnalysisRes.BuySuggestionRes();
        suggestionRes.setScore(0);
        suggestionRes.setLevel("NO_DATA");
        suggestionRes.setStrongSafeMinPrice(BigDecimal.ZERO);
        suggestionRes.setStrongSafeMaxPrice(BigDecimal.ZERO);
        suggestionRes.setAcceptableMinPrice(BigDecimal.ZERO);
        suggestionRes.setAcceptableMaxPrice(BigDecimal.ZERO);
        suggestionRes.setReasons(List.of("当前未抓取到有效在售挂单"));
        return suggestionRes;
    }

    private String resolveStopReason(IboxApiResponse<AnalysisConsignmentOrderListRes> response) {
        if (response == null) {
            return "response_null";
        }
        if (response.getCode() == null) {
            return "response_code_null";
        }
        if (response.getCode() != 0) {
            return "response_code_" + response.getCode();
        }
        if (response.getData() == null) {
            return "response_data_null";
        }
        if (CollectionUtils.isEmpty(response.getData().getList())) {
            return "response_list_empty";
        }
        return "unknown";
    }

    private List<Long> extractSampleOrderKeys(List<AnalysisConsignmentOrderRes> orders) {
        if (CollectionUtils.isEmpty(orders)) {
            return List.of();
        }
        return orders.stream()
                .filter(Objects::nonNull)
                .limit(10)
                .map(AnalysisConsignmentOrderRes::getId)
                .toList();
    }

    private String extractNextSortValues(AnalysisConsignmentOrderListRes data) {
        if (data == null || data.getSortValues() == null || CollectionUtils.isEmpty(data.getSortValues().getValues())) {
            return null;
        }
        List<String> values = data.getSortValues().getValues().stream()
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
        if (CollectionUtils.isEmpty(values)) {
            return null;
        }
        return String.join(",", values);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private BigDecimal divide(BigDecimal dividend, int divisor) {
        if (divisor <= 0) {
            return BigDecimal.ZERO;
        }
        return dividend.divide(BigDecimal.valueOf(divisor), 4, RoundingMode.HALF_UP);
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(millis, 0L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String safeText(Object value) {
        if (value == null) {
            return "-";
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text : "-";
    }

    private IboxAccountEntity getDefaultAccount() {
        IboxAccountEntity cached = iboxAccountCache.getById(3L);
        if (cached != null) {
            return cached;
        }
        IboxAccountEntity account = iboxAccountService.getById(3L);
        if (account != null) {
            iboxAccountCache.put(account);
        }
        return account;
    }
}
