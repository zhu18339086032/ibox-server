package com.ibox.common;

import com.ibox.manager.MarketCollectionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically refreshes market collection cache and persisted market data.
 */
@Slf4j
@Component
public class MarketCollectionRefreshTask {

    @Autowired
    private MarketCollectionManager marketCollectionManager;

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
}
