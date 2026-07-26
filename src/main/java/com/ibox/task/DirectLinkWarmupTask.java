package com.ibox.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.common.IboxAccountCache;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.model.res.ibox.AnnouncementRes;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.ibox.IboxPageDataRes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 直连链路保热任务。
 * <p>
 * 公告列表监控当前走代理，而公告详情和下单走默认直连链路。
 * 这里定时用直连方式请求一次公告列表和最新公告详情，把默认直连连接池持续跑热，
 * 降低新公告出现时公告详情接口和下单接口命中冷连接的概率。
 * </p>
 */
@Slf4j
@Component
public class DirectLinkWarmupTask {

    private final IboxClient iboxClient;
    private final IboxAccountCache iboxAccountCache;

    /**
     * 是否启用直连保热。
     */
    @Value("${ibox.direct-warmup.enabled:true}")
    private boolean directWarmupEnabled;

    public DirectLinkWarmupTask(IboxClient iboxClient, IboxAccountCache iboxAccountCache) {
        this.iboxClient = iboxClient;
        this.iboxAccountCache = iboxAccountCache;
    }

    /**
     * 定时保热公告详情直连链路。
     * <p>
     * 先直连请求公告列表拿到最新公告 UUID，再直连请求一次对应详情。
     * 这两步都不走代理，从而让默认直连 RestTemplate 的连接池保持热状态。
     * </p>
     */
    @Scheduled(fixedDelay = 10000L, initialDelay = 10_000L)
    public void warmupDirectAnnouncementChain() {
        if (!directWarmupEnabled) {
            return;
        }

        IboxAccountEntity account = iboxAccountCache.getDefaultAccount();
        if (account == null || account.getBearerToken() == null || account.getBearerToken().isBlank()) {
            return;
        }

        try {
            IboxApiResponse<IboxPageDataRes<AnnouncementRes>> listResponse = iboxClient.get(
                    "",
                    String.format(UrlConstant.ANNOUNCEMENT_LIST_URL, -1, 1, 1),
                    null,
                    new TypeReference<>() {
                    }
            );
            if (listResponse == null
                    || listResponse.getData() == null
                    || listResponse.getData().getList() == null
                    || listResponse.getData().getList().isEmpty()
                    || listResponse.getData().getList().getFirst() == null
                    || listResponse.getData().getList().getFirst().getUuid() == null
                    || listResponse.getData().getList().getFirst().getUuid().isBlank()) {
                return;
            }

            String latestUuid = listResponse.getData().getList().getFirst().getUuid();
            iboxClient.get(
                    account.getBearerToken(),
                    String.format(UrlConstant.ANNOUNCEMENT_DETAIL_URL, latestUuid),
                    null,
                    AnnouncementRes.class
            );
//            log.info("【直连保热】................................");
        } catch (Exception e) {
            log.debug("【直连保热】公告直连链路保热失败", e);
        }
    }
}
