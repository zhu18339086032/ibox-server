package com.ibox.task;

import com.ibox.common.MarketCollectionCache;
import com.ibox.entity.MarketCollectionEntity;
import com.ibox.manager.MarketCollectionManager;
import com.ibox.service.MarketCollectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Periodically refreshes market collection cache and persisted market data.
 */
@Slf4j
@Component
public class MarketCollectionRefreshTask {

    @Autowired
    private MarketCollectionManager marketCollectionManager;

    /**
     * 市场藏品服务
     */
    @Autowired
    private MarketCollectionService marketCollectionService;

    /**
     * Refreshes market collection data every 10 minutes.
     */
    @Scheduled(fixedDelay = 10 * 60 * 1000L, initialDelay = 10 * 1000L)
    public void refreshMarketCollections() {
        try {
            Integer count = marketCollectionManager.refresh();
            log.info("【市场藏品定时刷新】刷新完成, count:{}", count);
        } catch (Exception e) {
            log.warn("【市场藏品定时刷新】刷新失败", e);
        }
    }


    @Scheduled(fixedDelay = 10000)
    public void loadMarketCollectionCache() {
        List<MarketCollectionEntity> list = marketCollectionService.listMarketCollection();
        marketCollectionCache.refresh(list);
        log.info("【启动预热】市场藏品缓存加载完成，数量:{}", list.size());
    }

    /**
     * 市场藏品缓存
     */
    @Autowired
    private MarketCollectionCache marketCollectionCache;
}
