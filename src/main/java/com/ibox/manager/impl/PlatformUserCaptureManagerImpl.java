package com.ibox.manager.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.entity.PlatformUserEntity;
import com.ibox.manager.PlatformUserCaptureManager;
import com.ibox.model.ProxyInfo;
import com.ibox.model.req.web.PlatformUserCaptureReq;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.ibox.PlatformUserCollectionGroupPageRes;
import com.ibox.model.res.web.PlatformUserCaptureRes;
import com.ibox.service.IboxAccountService;
import com.ibox.service.IboxProxyService;
import com.ibox.service.PlatformUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 平台用户抓取管理实现
 */
@Service
@Slf4j
public class PlatformUserCaptureManagerImpl implements PlatformUserCaptureManager {

    private static final int PAGE_NO = 1;

    private static final int PAGE_SIZE = 20;

    private static final int GROUP_TYPE = 0;

    private static final int MAX_PROXY_RETRY = 5;

    private static final int WAREHOUSE_VISIBLE = 1;

    private static final int WAREHOUSE_INVISIBLE = 0;

    @Autowired
    private IboxClient iboxClient;

    @Autowired
    private IboxAccountService iboxAccountService;

    @Autowired
    private IboxProxyService iboxProxyService;

    @Autowired
    private PlatformUserService platformUserService;

    @Override
    public PlatformUserCaptureRes capture(PlatformUserCaptureReq req) {
        validateReq(req);
        IboxAccountEntity account = iboxAccountService.getById(req.getAccountId());
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            throw new IllegalArgumentException("账号不存在或 bearerToken 为空");
        }

        long startUserId = req.getStartUserId();
        long endUserId = req.getEndUserId();

        PlatformUserCaptureRes res = new PlatformUserCaptureRes();
        res.setStartUserId(startUserId);
        res.setEndUserId(endUserId);
        res.setAccountId(req.getAccountId());

        int scannedCount = 0;
        int validStoredCount = 0;
        int invisibleStoredCount = 0;
        int emptySkippedCount = 0;
        int errorCount = 0;

        for (long userId = startUserId; userId <= endUserId; userId++) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            scannedCount++;
            log.info("【平台用户抓取】开始抓取用户，userId:{}, scannedCount:{}, accountId:{}",
                    userId, scannedCount, req.getAccountId());
            FetchAttemptResult fetchResult = fetchWithProxyRetry(account, userId);
            if (fetchResult.error) {
                errorCount++;
                log.warn("【平台用户抓取】抓取失败，userId:{}, result:PROXY_RETRY_EXHAUSTED, errorCount:{}",
                        userId, errorCount);
                continue;
            }

            IboxApiResponse<PlatformUserCollectionGroupPageRes> response = fetchResult.response;
            if (response == null) {
                errorCount++;
                log.warn("【平台用户抓取】抓取失败，userId:{}, result:NULL_RESPONSE, errorCount:{}",
                        userId, errorCount);
                continue;
            }

            if (response.getCode() == null || response.getCode() != 0) {
                saveWarehouseStatus(userId, WAREHOUSE_INVISIBLE);
                invisibleStoredCount++;
                log.info("【平台用户抓取】抓取完成，userId:{}, result:INVISIBLE, responseCode:{}, responseMessage:{}, invisibleStoredCount:{}",
                        userId, response.getCode(), response.getMessage(), invisibleStoredCount);
                continue;
            }

            PlatformUserCollectionGroupPageRes data = response.getData();
            if (data == null || CollectionUtils.isEmpty(data.getList())) {
                emptySkippedCount++;
                log.info("【平台用户抓取】抓取完成，userId:{}, result:EMPTY_SKIP, emptySkippedCount:{}",
                        userId, emptySkippedCount);
                continue;
            }

            saveWarehouseStatus(userId, WAREHOUSE_VISIBLE);
            validStoredCount++;
            log.info("【平台用户抓取】抓取完成，userId:{}, result:VALID, collectionSize:{}, validStoredCount:{}",
                    userId, data.getList().size(), validStoredCount);
        }

        res.setScannedCount(scannedCount);
        res.setValidStoredCount(validStoredCount);
        res.setInvisibleStoredCount(invisibleStoredCount);
        res.setEmptySkippedCount(emptySkippedCount);
        res.setErrorCount(errorCount);
        res.setMessage("抓取完成");
        return res;
    }

    private FetchAttemptResult fetchWithProxyRetry(IboxAccountEntity account, Long userId) {
        for (int attempt = 1; attempt <= MAX_PROXY_RETRY; attempt++) {
//            ProxyInfo proxyInfo = iboxProxyService.getNextConfigProxy();
            String url = String.format(
                    UrlConstant.PLATFORM_USER_COLLECTION_GROUPS_URL,
                    userId,
                    PAGE_NO,
                    PAGE_SIZE,
                    GROUP_TYPE
            );
            IboxApiResponse<PlatformUserCollectionGroupPageRes> response = iboxClient.get(
                    account.getBearerToken(),
                    url,
                    null,
                    new TypeReference<PlatformUserCollectionGroupPageRes>() {
                    }
            );
            if (response != null) {
                return FetchAttemptResult.success(response);
            }
        }
        return FetchAttemptResult.error();
    }

    private void saveWarehouseStatus(Long userId, Integer warehouseVisible) {
        PlatformUserEntity entity = platformUserService.getOne(
                new LambdaQueryWrapper<PlatformUserEntity>()
                        .eq(PlatformUserEntity::getUserId, userId)
                        .last("limit 1")
        );
        if (entity == null) {
            entity = new PlatformUserEntity();
            entity.setUserId(userId);
        }
        entity.setWarehouseVisible(warehouseVisible);
        platformUserService.saveOrUpdate(entity);
    }

    private void validateReq(PlatformUserCaptureReq req) {
        if (req == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        if (req.getStartUserId() == null || req.getStartUserId() <= 0) {
            throw new IllegalArgumentException("开始用户ID必须大于0");
        }
        if (req.getEndUserId() == null || req.getEndUserId() <= 0) {
            throw new IllegalArgumentException("结束用户ID必须大于0");
        }
        if (req.getEndUserId() < req.getStartUserId()) {
            throw new IllegalArgumentException("结束用户ID不能小于开始用户ID");
        }
        if (req.getAccountId() == null) {
            throw new IllegalArgumentException("账户ID不能为空");
        }
    }

    private static class FetchAttemptResult {
        private final boolean error;
        private final IboxApiResponse<PlatformUserCollectionGroupPageRes> response;

        private FetchAttemptResult(boolean error, IboxApiResponse<PlatformUserCollectionGroupPageRes> response) {
            this.error = error;
            this.response = response;
        }

        public static FetchAttemptResult success(IboxApiResponse<PlatformUserCollectionGroupPageRes> response) {
            return new FetchAttemptResult(false, response);
        }

        public static FetchAttemptResult error() {
            return new FetchAttemptResult(true, null);
        }
    }
}
