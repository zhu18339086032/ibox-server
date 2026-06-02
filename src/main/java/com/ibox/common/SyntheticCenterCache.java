package com.ibox.common;

import com.ibox.model.res.ibox.SyntheticCenterRes;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 活动合成 center detail 缓存。
 *
 * <p>按 账号ID + 通道ID 维度缓存，避免不同账号的材料和资格状态串用。</p>
 */
@Component
public class SyntheticCenterCache {

    /**
     * center detail 缓存有效期。
     * 这里拉长一些，尽量覆盖“查看详情 -> 创建任务 -> 临近开始 -> 开始执行”这段链路。
     */
    private static final long CACHE_TTL_MILLIS = 10000 * 60 * 1000L;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 读取指定账号和通道的 center detail 缓存。
     *
     * @param accountId 账号ID
     * @param syntheticId 通道ID
     * @return 缓存中的 center detail；未命中或已过期时返回 null
     */
    public SyntheticCenterRes get(Long accountId, Long syntheticId) {
        CacheEntry entry = getEntry(accountId, syntheticId);
        return entry == null ? null : entry.center();
    }

    /**
     * 读取完整缓存条目，并在访问时顺便做过期清理。
     *
     * @param accountId 账号ID
     * @param syntheticId 通道ID
     * @return 缓存条目；未命中或已过期时返回 null
     */
    public CacheEntry getEntry(Long accountId, Long syntheticId) {
        if (accountId == null || syntheticId == null) {
            return null;
        }
        CacheEntry entry = cache.get(buildKey(accountId, syntheticId));
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.cachedAtMillis() > CACHE_TTL_MILLIS) {
            cache.remove(buildKey(accountId, syntheticId), entry);
            return null;
        }
        return entry;
    }

    /**
     * 写入 center detail 缓存。
     * 只缓存成功拿到的有效数据，失败响应不应覆盖已有好缓存。
     *
     * @param accountId 账号ID
     * @param syntheticId 通道ID
     * @param center center detail 数据
     */
    public void put(Long accountId, Long syntheticId, SyntheticCenterRes center) {
        if (accountId == null || syntheticId == null || center == null) {
            return;
        }
        cache.put(buildKey(accountId, syntheticId), new CacheEntry(center, System.currentTimeMillis()));
    }

    /**
     * 删除指定账号和通道的缓存。
     *
     * @param accountId 账号ID
     * @param syntheticId 通道ID
     */
    public void remove(Long accountId, Long syntheticId) {
        if (accountId == null || syntheticId == null) {
            return;
        }
        cache.remove(buildKey(accountId, syntheticId));
    }

    private String buildKey(Long accountId, Long syntheticId) {
        return accountId + ":" + syntheticId;
    }

    /**
     * 缓存条目。
     *
     * @param center center detail 数据
     * @param cachedAtMillis 写入缓存的时间戳
     */
    public record CacheEntry(SyntheticCenterRes center, long cachedAtMillis) {
    }
}
