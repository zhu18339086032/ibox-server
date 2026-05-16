package com.ibox.common;

import com.ibox.entity.IboxAccountEntity;
import com.ibox.entity.MarketCollectionEntity;
import com.ibox.service.IboxAccountService;
import com.ibox.service.MarketCollectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * iBox启动预热任务
 */
@Slf4j
@Component
public class IboxStartupTask implements ApplicationRunner {

    /**
     * iBox账号服务
     */
    @Autowired
    private IboxAccountService iboxAccountService;

    /**
     * 市场藏品服务
     */
    @Autowired
    private MarketCollectionService marketCollectionService;

    /**
     * iBox账号缓存
     */
    @Autowired
    private IboxAccountCache iboxAccountCache;

    /**
     * 市场藏品缓存
     */
    @Autowired
    private MarketCollectionCache marketCollectionCache;

    /**
     * 启动时预热缓存
     *
     * @param args 启动参数
     */
    @Override
    public void run(ApplicationArguments args) {
        loadDefaultAccount();
        loadMarketCollectionCache();
    }

    /**
     * 加载默认账号到缓存
     */
    private void loadDefaultAccount() {
        IboxAccountEntity account = iboxAccountService.lambdaQuery()
                .orderByAsc(IboxAccountEntity::getId)
                .last("limit 1")
                .one();
        if (account == null) {
            log.warn("【启动预热】未查询到默认账号，账号缓存未加载");
            return;
        }
        iboxAccountCache.setDefaultAccount(account);
        log.info("【启动预热】默认账号缓存加载完成，账号ID:{}, iBox用户:{}", account.getId(), account.getAccountName());
    }

    /**
     * 加载市场藏品到缓存
     */
    private void loadMarketCollectionCache() {
        List<MarketCollectionEntity> list = marketCollectionService.listMarketCollection();
        marketCollectionCache.refresh(list);
        log.info("【启动预热】市场藏品缓存加载完成，数量:{}", list.size());
    }
}
