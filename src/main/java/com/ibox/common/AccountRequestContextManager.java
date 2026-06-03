package com.ibox.common;

import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 账号请求上下文管理器。
 *
 * <p>把账号维度的固定请求信息提前整理成内存上下文，
 * 减少热路径中重复读取账号字段和重复拼接固定 URL 的成本。</p>
 */
@Component
public class AccountRequestContextManager {

    private final Map<Long, AccountRequestContext> contextMap = new ConcurrentHashMap<>();

    /**
     * 读取账号请求上下文。
     * 如果上下文不存在，则基于当前账号信息即时构建一份。
     *
     * @param account 账号
     * @return 账号请求上下文；账号无效时返回 null
     */
    public AccountRequestContext getOrCreate(IboxAccountEntity account) {
        if (account == null || account.getId() == null) {
            return null;
        }
        return contextMap.compute(account.getId(), (accountId, existing) -> buildContext(account, existing));
    }

    /**
     * 主动刷新指定账号上下文。
     *
     * @param account 账号
     * @return 最新上下文；账号无效时返回 null
     */
    public AccountRequestContext refresh(IboxAccountEntity account) {
        if (account == null || account.getId() == null) {
            return null;
        }
        AccountRequestContext context = buildContext(account, null);
        if (context == null) {
            contextMap.remove(account.getId());
            return null;
        }
        contextMap.put(account.getId(), context);
        return context;
    }

    /**
     * 移除指定账号上下文。
     *
     * @param accountId 账号ID
     */
    public void remove(Long accountId) {
        if (accountId == null) {
            return;
        }
        contextMap.remove(accountId);
    }

    private AccountRequestContext buildContext(IboxAccountEntity account, AccountRequestContext existing) {
        if (account == null || account.getId() == null || !StringUtils.hasText(account.getBearerToken()) || account.getIboxUid() == null) {
            return existing;
        }
        return new AccountRequestContext(
            account.getId(),
            account.getAccountName(),
            account.getBearerToken(),
            account.getIboxUid(),
            account.getDefaultPaymentPlatformCode(),
            String.format(UrlConstant.BATCH_PURCHASE_CONSIGNMENT_ORDER_URL, account.getIboxUid())
        );
    }

    /**
     * 账号请求上下文。
     *
     * @param accountId 账号ID
     * @param accountName 账号名称
     * @param bearerToken bearer token
     * @param iboxUid iBox用户ID
     * @param defaultPaymentPlatformCode 默认支付平台编码
     * @param batchPurchaseUrl 批量下单 URL
     */
    public record AccountRequestContext(
        Long accountId,
        String accountName,
        String bearerToken,
        Long iboxUid,
        Integer defaultPaymentPlatformCode,
        String batchPurchaseUrl
    ) {
    }
}
