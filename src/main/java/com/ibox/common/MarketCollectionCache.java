package com.ibox.common;

import com.ibox.entity.MarketCollectionEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 市场藏品缓存
 */
@Component
public class MarketCollectionCache {

    private final Map<String, MarketCollectionEntity> nameMap = new ConcurrentHashMap<>();
    private final Map<Long, MarketCollectionEntity> idMap = new ConcurrentHashMap<>();

    /**
     * 全量刷新缓存
     *
     * @param list 市场列表
     */
    public synchronized void refresh(List<MarketCollectionEntity> list) {
        nameMap.clear();
        idMap.clear();
        if (list == null || list.isEmpty()) {
            return;
        }
        for (MarketCollectionEntity entity : list) {
            if (entity == null) {
                continue;
            }
            if (entity.getId() != null) {
                idMap.put(entity.getId(), entity);
            }
            if (entity.getName() != null && !entity.getName().isBlank()) {
                nameMap.put(entity.getName(), entity);
            }
        }
    }

    /**
     * 根据名称获取市场藏品
     *
     * @param name 藏品名称
     * @return 市场藏品
     */
    public MarketCollectionEntity getByName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        MarketCollectionEntity entity = nameMap.get(name);
        if (entity != null) {
            return entity;
        }
        return nameMap.get(normalizeName(name));
    }

    /**
     * 根据ID获取市场藏品
     *
     * @param id 商品ID
     * @return 市场藏品
     */
    public MarketCollectionEntity getById(Long id) {
        if (id == null) {
            return null;
        }
        return idMap.get(id);
    }

    /**
     * 缓存是否为空
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return nameMap.isEmpty() || idMap.isEmpty();
    }

    /**
     * 获取名称缓存
     *
     * @return 名称缓存
     */
    public Map<String, MarketCollectionEntity> getNameMap() {
        return Collections.unmodifiableMap(nameMap);
    }

    /**
     * 获取ID缓存
     *
     * @return ID缓存
     */
    public Map<Long, MarketCollectionEntity> getIdMap() {
        return Collections.unmodifiableMap(idMap);
    }

    /**
     * 获取全部缓存数据
     *
     * @return 全部缓存数据
     */
    public List<MarketCollectionEntity> values() {
        return new ArrayList<>(idMap.values());
    }

    private String normalizeName(String name) {
        return name.replace("《", "").replace("》", "").trim();
    }
}
