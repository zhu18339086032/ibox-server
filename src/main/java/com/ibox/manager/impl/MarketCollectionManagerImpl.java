package com.ibox.manager.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ibox.client.IboxClient;
import com.ibox.common.IboxAccountCache;
import com.ibox.common.MarketCollectionCache;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.entity.MarketCollectionEntity;
import com.ibox.manager.MarketCollectionManager;
import com.ibox.model.req.web.MarketCollectionPageReq;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.ibox.MarketCollectionRes;
import com.ibox.model.res.web.MarketCollectionListRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.service.MarketCollectionService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 市场藏品服务实现类
 */
@Service
public class MarketCollectionManagerImpl implements MarketCollectionManager {

    @Autowired
    private MarketCollectionService marketCollectionService;

    @Autowired
    private IboxClient iboxClient;

    @Autowired
    private MarketCollectionCache marketCollectionCache;

    @Autowired
    private IboxAccountCache iboxAccountCache;

    @Override
    public List<MarketCollectionListRes> list() {
        List<MarketCollectionEntity> list = marketCollectionService.listMarketCollection();
        return list.stream().map(this::buildListRes).toList();
    }

    @Override
    public PageResponse<MarketCollectionListRes> page(MarketCollectionPageReq req) {
        LambdaQueryWrapper<MarketCollectionEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.like(StringUtils.hasText(req.getName()), MarketCollectionEntity::getName, req.getName());
        queryWrapper.orderByDesc(MarketCollectionEntity::getId);
        IPage<MarketCollectionEntity> page = marketCollectionService.page(new Page<>(req.getPageNum(), req.getPageSize()), queryWrapper);
        return PageResponse.of(
                page.getCurrent(),
                page.getSize(),
                page.getTotal(),
                page.getRecords().stream().map(this::buildListRes).toList()
        );
    }

    @Override
    public Integer refresh() {
        IboxAccountEntity account = getDefaultAccount();
        if (account == null || account.getBearerToken() == null || account.getBearerToken().isBlank()) {
            throw new IllegalStateException("账户列表中不存在可用token");
        }

        // 以商品 ID 去重：不同分类或不同合集展开结果可能包含同一商品。
        Map<Long, MarketCollectionRes> itemMap = new LinkedHashMap<>();
        Set<Long> expandedCollectionIds = new LinkedHashSet<>();
        // https://sail-api.ibox.art/public-service/markets/segments?timeRange=0&pageNo=1&pageSize=-1
        List<Integer> segmentIdList = Arrays.asList(96, 86, 89, 83, 54, 103);
        for (Integer segmentId : segmentIdList) {
            int pageNo = 1;
            int pageSize = 50;
            while (true) {
                String url = String.format(UrlConstant.MARKET_COLLECTION_LIST_URL, pageNo, pageSize, segmentId);
                IboxApiResponse<com.ibox.model.res.ibox.MarketCollectionListRes> response =
                        iboxClient.get(account.getBearerToken(), url, null, com.ibox.model.res.ibox.MarketCollectionListRes.class);
                validateResponse(response, url);
                if (response.getData().getList() == null || response.getData().getList().isEmpty()) {
                    break;
                }

                for (MarketCollectionRes item : response.getData().getList()) {
                    if (item == null || item.getId() == null) {
                        continue;
                    }
                    if ("collection".equalsIgnoreCase(item.getItemType())) {
                        if (expandedCollectionIds.add(item.getId())) {
                            collectCollectionAlbums(account.getBearerToken(), item.getId(), itemMap);
                        }
                    } else {
                        // 兼容旧接口没有 itemType 的情况，默认按商品处理。
                        itemMap.put(item.getId(), item);
                    }
                }

                if (!Boolean.TRUE.equals(response.getData().getHasMore())) {
                    break;
                }
                pageNo++;
                sleepBetweenRequests();
            }
        }

        List<MarketCollectionEntity> totalList = itemMap.values().stream().map(this::buildEntity).toList();
        if (totalList.isEmpty()) {
            marketCollectionCache.refresh(totalList);
            return 0;
        }

        Set<Long> currentIds = new LinkedHashSet<>();
        for (MarketCollectionEntity entity : totalList) {
            if (entity != null && entity.getId() != null) {
                currentIds.add(entity.getId());
            }
        }

        marketCollectionService.saveOrUpdateMarketCollections(totalList);
        marketCollectionService.markMissingAsDeleted(currentIds);
        marketCollectionCache.refresh(totalList);
        return totalList.size();
    }

    /**
     * 拉取一个合集下的全部商品，并加入本次刷新快照。
     */
    private void collectCollectionAlbums(String token, Long collectionId, Map<Long, MarketCollectionRes> itemMap) {
        int pageNo = 1;
        int pageSize = 20;
        while (true) {
            String url = String.format(UrlConstant.MARKET_COLLECTION_ALBUM_LIST_URL,
                    collectionId, 3, "UNFOCUSED", pageNo, pageSize);
            IboxApiResponse<com.ibox.model.res.ibox.MarketCollectionListRes> response =
                    iboxClient.get(token, url, null, com.ibox.model.res.ibox.MarketCollectionListRes.class);
            validateResponse(response, url);
            if (response.getData().getList() == null || response.getData().getList().isEmpty()) {
                return;
            }
            for (MarketCollectionRes album : response.getData().getList()) {
                if (album == null || album.getId() == null) {
                    continue;
                }
                // 合集接口当前应只返回 album；即使服务端未来混入 collection，也不把容器当商品入库。
                if (!"collection".equalsIgnoreCase(album.getItemType())) {
                    itemMap.put(album.getId(), album);
                }
            }
            if (!Boolean.TRUE.equals(response.getData().getHasMore())) {
                return;
            }
            pageNo++;
            sleepBetweenRequests();
        }
    }

    private void validateResponse(IboxApiResponse<?> response, String url) {
        if (response == null || !Integer.valueOf(0).equals(response.getCode()) || response.getData() == null) {
            throw new IllegalStateException("刷新市场数据失败，接口响应无效: " + url);
        }
    }

    private void sleepBetweenRequests() {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("刷新市场数据被中断", e);
        }
    }

    private MarketCollectionListRes buildListRes(MarketCollectionEntity entity) {
        MarketCollectionListRes res = new MarketCollectionListRes();
        BeanUtils.copyProperties(entity, res);
        return res;
    }

    private MarketCollectionEntity buildEntity(MarketCollectionRes res) {
        MarketCollectionEntity entity = new MarketCollectionEntity();
        BeanUtils.copyProperties(res, entity);
        if (res.getFocused() != null) {
            entity.setFocus(res.getFocused() ? 1 : 0);
        }
        // 新接口没有 status，市场快照中的商品均视为有效。
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        return entity;
    }

    private IboxAccountEntity getDefaultAccount() {
        IboxAccountEntity cached = iboxAccountCache.getDefaultAccount();
        if (cached != null) {
            return cached;
        }
        throw new IllegalStateException("默认账号缓存不存在，请检查启动预热任务是否执行成功");
    }
}
