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
import com.ibox.service.IboxAccountService;
import com.ibox.service.IboxProxyService;
import com.ibox.service.MarketCollectionService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 市场藏品服务实现类
 */
@Service
public class MarketCollectionManagerImpl implements MarketCollectionManager {

    @Autowired
    private MarketCollectionService marketCollectionService;

    @Autowired
    private IboxAccountService iboxAccountService;

    @Autowired
    private IboxClient iboxClient;

    @Autowired
    private MarketCollectionCache marketCollectionCache;

    @Autowired
    private IboxAccountCache iboxAccountCache;

    @Autowired
    private IboxProxyService iboxProxyService;

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

        List<MarketCollectionEntity> totalList = new ArrayList<>();
        // https://sail-api.ibox.art/public-service/markets/segments?timeRange=0&pageNo=1&pageSize=-1
        List<Integer> segmentIdList = Arrays.asList(97, 96, 89, 94, 92, 87, 83, 86, 84, 54, 39, 93);
        for (Integer segmentId : segmentIdList) {
            int pageNo = 1;
            int pageSize = 50;
            while (true) {
                String url = String.format(UrlConstant.MARKET_COLLECTION_LIST_URL, pageNo, segmentId, pageSize);
                IboxApiResponse<com.ibox.model.res.ibox.MarketCollectionListRes> response =
                        iboxClient.get(account.getBearerToken(), url, null, com.ibox.model.res.ibox.MarketCollectionListRes.class);
                try {
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                if (response == null || response.getData() == null || response.getData().getList() == null || response.getData().getList().isEmpty()) {
                    break;
                }

                List<MarketCollectionEntity> currentList = response.getData().getList().stream().map(this::buildEntity).toList();
                totalList.addAll(currentList);

                if (!Boolean.TRUE.equals(response.getData().getHasMore())) {
                    break;
                }
                pageNo++;
            }
        }

        if (totalList.isEmpty()) {
            return 0;
        }

        marketCollectionCache.refresh(totalList);
        marketCollectionService.saveOrUpdateMarketCollections(totalList);
        return totalList.size();
    }

    private MarketCollectionListRes buildListRes(MarketCollectionEntity entity) {
        MarketCollectionListRes res = new MarketCollectionListRes();
        BeanUtils.copyProperties(entity, res);
        return res;
    }

    private MarketCollectionEntity buildEntity(MarketCollectionRes res) {
        MarketCollectionEntity entity = new MarketCollectionEntity();
        BeanUtils.copyProperties(res, entity);
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
