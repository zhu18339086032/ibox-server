package com.ibox.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ibox.entity.MarketCollectionEntity;

import java.util.List;

/**
 * 市场藏品服务接口
 */
public interface MarketCollectionService extends IService<MarketCollectionEntity> {

    /**
     * 获取市场列表
     *
     * @return 市场列表
     */
    List<MarketCollectionEntity> listMarketCollection();

    /**
     * 批量保存或更新市场列表
     *
     * @param list 市场数据
     * @return 是否成功
     */
    boolean saveOrUpdateMarketCollections(List<MarketCollectionEntity> list);
}
