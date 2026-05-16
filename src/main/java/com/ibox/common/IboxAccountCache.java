package com.ibox.common;

import com.ibox.entity.IboxAccountEntity;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * iBox账号缓存
 */
@Component
public class IboxAccountCache {

    /**
     * 默认账号
     */
    private volatile IboxAccountEntity defaultAccount;

    /**
     * 账号缓存
     */
    private final Map<Long, IboxAccountEntity> accountMap = new ConcurrentHashMap<>();

    /**
     * 获取默认账号
     *
     * @return 默认账号
     */
    public IboxAccountEntity getDefaultAccount() {
        return defaultAccount;
    }

    /**
     * 设置默认账号
     *
     * @param defaultAccount 默认账号
     */
    public void setDefaultAccount(IboxAccountEntity defaultAccount) {
        this.defaultAccount = defaultAccount;
        put(defaultAccount);
    }

    /**
     * 根据ID获取账号
     *
     * @param accountId 账号ID
     * @return 账号信息
     */
    public IboxAccountEntity getById(Long accountId) {
        if (accountId == null) {
            return null;
        }
        return accountMap.get(accountId);
    }

    /**
     * 放入账号缓存
     *
     * @param account 账号信息
     */
    public void put(IboxAccountEntity account) {
        if (account == null || account.getId() == null) {
            return;
        }
        accountMap.put(account.getId(), account);
    }

    /**
     * 是否为空
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return defaultAccount == null;
    }

    /**
     * 清空缓存
     */
    public void clear() {
        this.defaultAccount = null;
        this.accountMap.clear();
    }
}
