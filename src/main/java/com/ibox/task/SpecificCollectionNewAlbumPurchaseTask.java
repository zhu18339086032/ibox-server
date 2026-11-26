package com.ibox.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.model.ProxyInfo;
import com.ibox.model.req.ibox.BatchPurchaseConsignmentOrdersReq;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.ibox.MarketCollectionListRes;
import com.ibox.model.res.ibox.MarketCollectionRes;
import com.ibox.service.IboxAccountService;
import com.ibox.service.IboxProxyService;
import com.ibox.utils.FeishuMessageUtil;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 指定合集新品监听与多账号自动下单任务。
 *
 * <p>首次成功拉取只建立商品基线，不会购买合集内已有商品；后续发现新的商品 ID 时，
 * 使用所有已配置 Bearer Token 的账号并发调用批量购买接口。</p>
 */
@Slf4j
@Component
public class SpecificCollectionNewAlbumPurchaseTask {

    private static final int PAGE_SIZE = 100;
    private static final int PURCHASE_COUNT = 5;
    private static final int MAX_SINGLE_PRICE = 2000;
    private static final long FETCH_ERROR_LOG_INTERVAL_MILLIS = 5_000L;

    private final IboxClient iboxClient;
    private final IboxAccountService iboxAccountService;
    private final IboxProxyService iboxProxyService;
    private final FeishuMessageUtil feishuMessageUtil;

    /**
     * 需要监听的合集 ID。值小于等于 0 时任务保持关闭。
     */
    private long targetCollectionId = 19;

    /**
     * 调度线程只负责触发，实际市场请求放到独立单线程，避免阻塞项目里的其他定时任务。
     */
    private final ExecutorService monitorExecutor = Executors.newSingleThreadExecutor();

    /**
     * 不同账号的下单请求并发执行。
     */
    private final ExecutorService purchaseExecutor = Executors.newFixedThreadPool(16);

    private final Set<Long> seenAlbumIds = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean polling = new AtomicBoolean(false);
    private final AtomicBoolean missingCollectionIdLogged = new AtomicBoolean(false);
    private final AtomicLong lastFetchErrorLogAt = new AtomicLong(0L);
    private volatile boolean baselineInitialized = false;

    public SpecificCollectionNewAlbumPurchaseTask(IboxClient iboxClient, IboxAccountService iboxAccountService, IboxProxyService iboxProxyService, FeishuMessageUtil feishuMessageUtil) {
        this.iboxClient = iboxClient;
        this.iboxAccountService = iboxAccountService;
        this.iboxProxyService = iboxProxyService;
        this.feishuMessageUtil = feishuMessageUtil;
    }

    /**
     * 每 100 毫秒触发一次。若上一轮仍在请求中，本轮直接跳过，保证不会并发重复扫描。
     */
//    @Scheduled(fixedRate = 400L, initialDelay = 1_000L)
    public void scheduleMonitor() {
        if (targetCollectionId <= 0) {
            if (missingCollectionIdLogged.compareAndSet(false, true)) {
                log.info("【指定合集新品监听】未配置合集ID，任务未启用");
            }
            return;
        }
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        try {
            monitorAndPurchaseNewAlbums();
        } catch (Exception e) {
            logFetchFailure("监听执行异常", e);
        } finally {
            polling.set(false);
        }
    }

    private void monitorAndPurchaseNewAlbums() {
        List<MarketCollectionRes> currentAlbums = fetchAllAlbums();
        if (currentAlbums == null) {
            return;
        }

        if (!baselineInitialized) {
            for (MarketCollectionRes album : currentAlbums) {
                if (album != null && album.getId() != null) {
                    seenAlbumIds.add(album.getId());
                }
            }
            baselineInitialized = true;
            log.info("【指定合集新品监听】首次基线建立完成，collectionId:{}, albumCount:{}", targetCollectionId, seenAlbumIds.size());
            return;
        }

        List<MarketCollectionRes> newAlbums = new ArrayList<>();
        for (MarketCollectionRes album : currentAlbums) {
            if (album != null && album.getId() != null && seenAlbumIds.add(album.getId())) {
                newAlbums.add(album);
            }
        }
        if (newAlbums.isEmpty()) {
            log.info("暂未有新商品");
            return;
        }

        List<IboxAccountEntity> accounts = loadAvailableAccounts();
        if (accounts.isEmpty()) {
            log.warn("【指定合集新品监听】发现新增商品但没有可用账号，collectionId:{}, newAlbumCount:{}", targetCollectionId, newAlbums.size());
            return;
        }

        for (MarketCollectionRes album : newAlbums) {
            log.info("【指定合集新品监听】发现新增商品，准备多账号并发下单，collectionId:{}, albumId:{}, name:{}, accountCount:{}", targetCollectionId, album.getId(), album.getName(), accounts.size());
            for (IboxAccountEntity account : accounts) {
                purchaseExecutor.execute(() -> tryBatchPurchase(account, album));
            }
        }
    }

    /**
     * 拉取指定合集的全部商品。返回 null 表示本轮请求失败，不能据此更新基线。
     */
    private List<MarketCollectionRes> fetchAllAlbums() {
        try {
            ProxyInfo proxyInfo = iboxProxyService.getNextConfigProxy();
            Map<Long, MarketCollectionRes> albumMap = new LinkedHashMap<>();
            int pageNo = 1;
            while (true) {
                String url = String.format(UrlConstant.MARKET_COLLECTION_ALBUM_LIST_URL, targetCollectionId, 3, "UNFOCUSED", pageNo, PAGE_SIZE);
                IboxApiResponse<MarketCollectionListRes> response = iboxClient.get("", url, null, new TypeReference<>() {
                }, proxyInfo);
                if (!isValidMarketResponse(response)) {
                    logFetchFailure("合集商品接口响应无效，url:" + url, null);
                    return null;
                }

                List<MarketCollectionRes> pageAlbums = response.getData().getList();
                if (pageAlbums == null || pageAlbums.isEmpty()) {
                    break;
                }
                for (MarketCollectionRes album : pageAlbums) {
                    if (album != null && album.getId() != null && !"collection".equalsIgnoreCase(album.getItemType())) {
                        albumMap.put(album.getId(), album);
                    }
                }
                if (!Boolean.TRUE.equals(response.getData().getHasMore())) {
                    break;
                }
                pageNo++;
            }
            return new ArrayList<>(albumMap.values());
        } catch (Exception e) {
            logFetchFailure("拉取合集商品异常，collectionId:" + targetCollectionId, e);
            return null;
        }
    }

    private boolean isValidMarketResponse(IboxApiResponse<MarketCollectionListRes> response) {
        return response != null && Integer.valueOf(0).equals(response.getCode()) && response.getData() != null;
    }

    /**
     * 仅在发现新品时查库，确保可以使用账号最新保存的 Token。
     */
    private List<IboxAccountEntity> loadAvailableAccounts() {
        List<IboxAccountEntity> accounts = iboxAccountService.lambdaQuery().orderByAsc(IboxAccountEntity::getId).list();
        if (accounts == null || accounts.isEmpty()) {
            return List.of();
        }
        return accounts.stream().filter(account -> account != null && StringUtils.hasText(account.getBearerToken())).toList();
    }

    private void tryBatchPurchase(IboxAccountEntity account, MarketCollectionRes album) {
        try {
            BatchPurchaseConsignmentOrdersReq req = new BatchPurchaseConsignmentOrdersReq();
            req.setDigitalCollectionGroupId(album.getId());
            req.setMaxCount(PURCHASE_COUNT);
            req.setMaxSinglePrice(MAX_SINGLE_PRICE);
            req.setPaymentPlatformCode(account.getDefaultPaymentPlatformCode() == null ? 30 : account.getDefaultPaymentPlatformCode());
            req.setLevel(-1);

            IboxApiResponse<Map<String, Object>> response = iboxClient.post(account.getBearerToken(), UrlConstant.BATCH_PURCHASE_CONSIGNMENT_ORDER_URL, req, new TypeReference<>() {
            });
            log.info("【指定合集新品监听】下单完成，accountId:{}, collectionId:{}, albumId:{}, name:{}, response:{}", account.getId(), targetCollectionId, album.getId(), album.getName(), response);
            if (isPurchaseSuccess(response)) {
                String orderId = String.valueOf(response.getData().get("orderId"));
                String message = "iBox指定合集新品下单成功，账号ID：" + account.getId() + "，账号名称：" + safeText(account.getAccountName()) + "，合集ID：" + targetCollectionId + "，商品ID：" + album.getId() + "，商品名称：" + safeText(album.getName()) + "，订单ID：" + orderId;
                feishuMessageUtil.sendText(message, "specific-collection-buy-success:" + account.getId() + ":" + album.getId() + ":" + orderId, 3);
            }
        } catch (Exception e) {
            log.error("【指定合集新品监听】下单异常，accountId:{}, collectionId:{}, albumId:{}, name:{}", account == null ? null : account.getId(), targetCollectionId, album == null ? null : album.getId(), album == null ? null : album.getName(), e);
        }
    }

    private boolean isPurchaseSuccess(IboxApiResponse<Map<String, Object>> response) {
        return response != null && Integer.valueOf(0).equals(response.getCode()) && response.getData() != null && response.getData().get("orderId") != null;
    }

    /**
     * 100ms 轮询失败时最多每 5 秒记录一次，避免外部接口异常导致日志刷屏。
     */
    private void logFetchFailure(String message, Throwable throwable) {
        long now = System.currentTimeMillis();
        long previous = lastFetchErrorLogAt.get();
        if (now - previous < FETCH_ERROR_LOG_INTERVAL_MILLIS || !lastFetchErrorLogAt.compareAndSet(previous, now)) {
            return;
        }
        if (throwable == null) {
            log.warn("【指定合集新品监听】{}", message);
        } else {
            log.warn("【指定合集新品监听】{}", message, throwable);
        }
    }

    private String safeText(String text) {
        return text == null || text.isBlank() ? "-" : text;
    }

    @PreDestroy
    public void shutdown() {
        monitorExecutor.shutdownNow();
        purchaseExecutor.shutdownNow();
    }
}
