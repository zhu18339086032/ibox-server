package com.ibox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ibox.entity.MarketCollectionEntity;
import com.ibox.mapper.MarketCollectionMapper;
import com.ibox.service.MarketCollectionService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 市场藏品Service业务层处理
 */
@Service
public class MarketCollectionServiceImpl extends ServiceImpl<MarketCollectionMapper, MarketCollectionEntity> implements MarketCollectionService {

    /**
     * 获取市场列表
     *
     * @return 市场列表
     */
    @Override
    public List<MarketCollectionEntity> listMarketCollection() {
        LambdaQueryWrapper<MarketCollectionEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(MarketCollectionEntity::getId);
        return this.list(queryWrapper);
    }

    /**
     * 批量保存或更新市场列表
     *
     * @param list 市场数据
     * @return 是否成功
     */
    @Override
    public boolean saveOrUpdateMarketCollections(List<MarketCollectionEntity> list) {
        return this.saveOrUpdateBatch(list);
    }
}
