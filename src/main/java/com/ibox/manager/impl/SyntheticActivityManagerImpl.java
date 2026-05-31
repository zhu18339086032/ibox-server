package com.ibox.manager.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ibox.client.IboxClient;
import com.ibox.constants.TokenConstant;
import com.ibox.constants.UrlConstant;
import com.ibox.entity.IboxAccountEntity;
import com.ibox.manager.SyntheticActivityManager;
import com.ibox.model.req.web.SyntheticActivityPageReq;
import com.ibox.model.res.ibox.IboxApiResponse;
import com.ibox.model.res.ibox.IboxPageDataRes;
import com.ibox.model.res.ibox.SyntheticActivityChannelRes;
import com.ibox.model.res.ibox.SyntheticActivityDetailRes;
import com.ibox.model.res.ibox.SyntheticActivityListItemRes;
import com.ibox.model.res.ibox.SyntheticAlbumRes;
import com.ibox.model.res.ibox.SyntheticBurnAlbumGroupRes;
import com.ibox.model.res.ibox.SyntheticCenterRes;
import com.ibox.model.res.ibox.SyntheticGroupNameRes;
import com.ibox.model.res.ibox.SyntheticTargetAlbumRes;
import com.ibox.model.res.web.PageResponse;
import com.ibox.model.res.web.SyntheticActivityDetailWebRes;
import com.ibox.model.res.web.SyntheticActivityListItemWebRes;
import com.ibox.model.res.web.SyntheticAlbumWebRes;
import com.ibox.model.res.web.SyntheticBurnAlbumGroupWebRes;
import com.ibox.model.res.web.SyntheticChannelWebRes;
import com.ibox.model.res.web.SyntheticGroupNameWebRes;
import com.ibox.model.res.web.SyntheticTargetAlbumWebRes;
import com.ibox.service.IboxAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 活动查询管理器实现。
 */
@Slf4j
@Service
public class SyntheticActivityManagerImpl implements SyntheticActivityManager {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String ACTIVITY_STAGE_UPCOMING = "UPCOMING";
    private static final String ACTIVITY_STAGE_ONGOING = "ONGOING";

    @Autowired
    private IboxClient iboxClient;

    @Autowired
    private IboxAccountService iboxAccountService;

    @Override
    public PageResponse<SyntheticActivityListItemWebRes> list(SyntheticActivityPageReq req) {
        long pageNum = req.getPageNum() == null || req.getPageNum() <= 0 ? 1L : req.getPageNum();
        long pageSize = req.getPageSize() == null || req.getPageSize() <= 0 ? 10L : req.getPageSize();

        IboxApiResponse<IboxPageDataRes<SyntheticActivityListItemRes>> response = iboxClient.get(
            TokenConstant.TOKEN_3,
            String.format(UrlConstant.SYNTHETIC_ACTIVITY_LIST_URL, pageNum, pageSize),
            null,
            new TypeReference<>() {
            }
        );
        if (!isSuccess(response) || response.getData() == null || CollectionUtils.isEmpty(response.getData().getList())) {
            return PageResponse.empty(pageNum, pageSize);
        }

        List<SyntheticActivityListItemWebRes> records = new ArrayList<>();
        for (SyntheticActivityListItemRes item : response.getData().getList()) {
            if (!matchKeyword(item, req.getKeyword())) {
                continue;
            }
            if (!matchOnlyAvailable(item, req.getOnlyAvailable())) {
                continue;
            }
            if (!matchSupportAssistant(item, req.getSupportAssistant())) {
                continue;
            }

            SyntheticActivityListItemWebRes res = new SyntheticActivityListItemWebRes();
            res.setActivityId(item.getId());
            res.setActivityName(item.getName());
            res.setCoverPic(item.getCoverPic());
            res.setStartTime(item.getStartTime());
            res.setEndTime(item.getEndTime());
            res.setSupportAssistant(Boolean.TRUE.equals(item.getSupportAssistant()));
            res.setSyntheticStatus(item.getSyntheticStatus());
            res.setChannelCount(0);
            res.setActivityStage(resolveActivityStage(item));
            records.add(res);
        }
        return PageResponse.of(pageNum, pageSize, response.getData().getTotal(), records);
    }

    @Override
    public SyntheticActivityDetailWebRes detail(Long activityId, Long accountId) {
        if (activityId == null) {
            return null;
        }
        IboxAccountEntity account = iboxAccountService.getById(accountId);
        if (account == null || !StringUtils.hasText(account.getBearerToken())) {
            throw new IllegalArgumentException("活动详情查询账号不存在或 token 为空");
        }

        IboxApiResponse<SyntheticActivityDetailRes> response = iboxClient.get(
            account.getBearerToken(),
            String.format(UrlConstant.SYNTHETIC_ACTIVITY_DETAIL_URL, activityId),
            null,
            SyntheticActivityDetailRes.class
        );
        if (!isSuccess(response) || response.getData() == null) {
            return null;
        }

        SyntheticActivityDetailRes activity = response.getData();
        SyntheticActivityDetailWebRes res = new SyntheticActivityDetailWebRes();
        res.setActivityId(activity.getId());
        res.setActivityName(activity.getName());
        res.setCoverPic(activity.getCoverPic());

        List<SyntheticChannelWebRes> channels = new ArrayList<>();
        if (!CollectionUtils.isEmpty(activity.getChannels())) {
            for (SyntheticActivityChannelRes channel : activity.getChannels()) {
                SyntheticChannelWebRes channelRes = buildChannel(channel, account);
                channels.add(channelRes);
                if (res.getStartTime() == null) {
                    res.setStartTime(channelRes.getStartTime());
                }
                if (res.getEndTime() == null) {
                    res.setEndTime(channelRes.getEndTime());
                }
                if (res.getSyntheticStatus() == null) {
                    res.setSyntheticStatus(channelRes.getSyntheticStatus());
                }
                if (res.getSupportAssistant() == null) {
                    res.setSupportAssistant(Boolean.TRUE.equals(channelRes.getSupportAssistant()));
                }
            }
        }
        res.setChannels(channels);
        return res;
    }

    /**
     * 聚合单个通道详情。
     *
     * <p>center detail 也必须使用同一个账号 token，
     * 这样返回的材料、资格、通道状态才与最终执行账号一致。</p>
     */
    private SyntheticChannelWebRes buildChannel(SyntheticActivityChannelRes channel, IboxAccountEntity account) {
        SyntheticChannelWebRes res = new SyntheticChannelWebRes();
        res.setSyntheticId(channel.getSyntheticActivityId());
        res.setChannelName(channel.getName());
        res.setStartTime(channel.getStartTime());
        res.setEndTime(channel.getEndTime());
        res.setSyntheticStatus(channel.getSyntheticStatus());
        res.setSupportAssistant(Boolean.TRUE.equals(channel.getSupportAssistant()));
        res.setSupportBatchSynthetic(false);
        res.setNeedSlider(false);
        res.setBurnAlbums(Collections.emptyList());
        res.setTargetAlbums(Collections.emptyList());
        res.setSyntheticGroupNameList(Collections.emptyList());

        if (channel.getSyntheticActivityId() == null) {
            return res;
        }

        IboxApiResponse<SyntheticCenterRes> centerResponse = iboxClient.get(
            account.getBearerToken(),
            String.format(UrlConstant.SYNTHETIC_CENTER_DETAIL_URL, channel.getSyntheticActivityId()),
            null,
            SyntheticCenterRes.class
        );
        if (!isSuccess(centerResponse) || centerResponse.getData() == null) {
            return res;
        }

        SyntheticCenterRes center = centerResponse.getData();
        res.setSyntheticStatus(center.getSyntheticStatus());
        res.setSupportAssistant(center.getSupportAssistant() != null && center.getSupportAssistant() == 1);
        res.setSupportBatchSynthetic(center.getSupportBatchSynthetic() != null && center.getSupportBatchSynthetic() == 1);
        res.setNeedSlider(center.getNeedSlider() != null && center.getNeedSlider() == 1);
        res.setSurplusNum(center.getSurplusNum());
        if (center.getStartTime() != null) {
            res.setStartTime(formatMillis(center.getStartTime()));
        }
        if (center.getEndTime() != null) {
            res.setEndTime(formatMillis(center.getEndTime()));
        }
        res.setBurnAlbums(buildBurnAlbums(center.getBurnAlbums()));
        res.setTargetAlbums(buildTargetAlbums(center.getTargetAlbums()));
        res.setSyntheticGroupNameList(buildGroupNames(center.getSyntheticGroupNameList()));
        return res;
    }

    private List<SyntheticBurnAlbumGroupWebRes> buildBurnAlbums(List<SyntheticBurnAlbumGroupRes> groups) {
        if (CollectionUtils.isEmpty(groups)) {
            return Collections.emptyList();
        }

        List<SyntheticBurnAlbumGroupWebRes> results = new ArrayList<>();
        for (SyntheticBurnAlbumGroupRes group : groups) {
            SyntheticBurnAlbumGroupWebRes res = new SyntheticBurnAlbumGroupWebRes();
            res.setGroupId(group.getGroupId());
            res.setQuantity(group.getQuantity());
            res.setConsumeNum(group.getConsumeNum());

            List<SyntheticAlbumWebRes> albums = new ArrayList<>();
            if (!CollectionUtils.isEmpty(group.getAlbums())) {
                for (SyntheticAlbumRes album : group.getAlbums()) {
                    SyntheticAlbumWebRes albumRes = new SyntheticAlbumWebRes();
                    albumRes.setId(album.getId());
                    albumRes.setName(album.getName());
                    albumRes.setCoverPicUrl(album.getCoverPicUrl());
                    albumRes.setFileUrl(album.getFileUrl());
                    albumRes.setIssueNum(album.getIssueNum());
                    albumRes.setUsableNum(album.getUsableNum());
                    albumRes.setNeedToBuy(album.getNeedToBuy());
                    albums.add(albumRes);
                }
            }
            res.setAlbums(albums);
            results.add(res);
        }
        return results;
    }

    private List<SyntheticTargetAlbumWebRes> buildTargetAlbums(List<SyntheticTargetAlbumRes> albums) {
        if (CollectionUtils.isEmpty(albums)) {
            return Collections.emptyList();
        }

        List<SyntheticTargetAlbumWebRes> results = new ArrayList<>();
        for (SyntheticTargetAlbumRes album : albums) {
            SyntheticTargetAlbumWebRes res = new SyntheticTargetAlbumWebRes();
            res.setId(album.getId());
            res.setName(album.getName());
            res.setCoverPicUrl(album.getCoverPicUrl());
            res.setFileUrl(album.getFileUrl());
            res.setIssueNum(album.getIssueNum());
            res.setCirculationNum(album.getCirculationNum());
            res.setQuantity(album.getQuantity());
            res.setSyntheticGroupId(album.getSyntheticGroupId());
            results.add(res);
        }
        return results;
    }

    private List<SyntheticGroupNameWebRes> buildGroupNames(List<SyntheticGroupNameRes> groups) {
        if (CollectionUtils.isEmpty(groups)) {
            return Collections.emptyList();
        }

        List<SyntheticGroupNameWebRes> results = new ArrayList<>();
        for (SyntheticGroupNameRes group : groups) {
            SyntheticGroupNameWebRes res = new SyntheticGroupNameWebRes();
            res.setSyntheticGroupId(group.getSyntheticGroupId());
            res.setSyntheticCustomDcName(group.getSyntheticCustomDcName());
            res.setSyntheticCustomUrl(group.getSyntheticCustomUrl());
            results.add(res);
        }
        return results;
    }

    private boolean matchKeyword(SyntheticActivityListItemRes item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        return StringUtils.hasText(item.getName()) && item.getName().contains(keyword.trim());
    }

    private boolean matchOnlyAvailable(SyntheticActivityListItemRes item, Boolean onlyAvailable) {
        if (!Boolean.TRUE.equals(onlyAvailable)) {
            return true;
        }
        return !isEnded(item);
    }

    private boolean matchSupportAssistant(SyntheticActivityListItemRes item, Boolean supportAssistant) {
        if (supportAssistant == null) {
            return true;
        }
        return supportAssistant.equals(Boolean.TRUE.equals(item.getSupportAssistant()));
    }

    private boolean isEnded(SyntheticActivityListItemRes item) {
        if (!StringUtils.hasText(item.getEndTime())) {
            return false;
        }
        try {
            return !LocalDateTime.parse(item.getEndTime(), DATETIME_FORMATTER).isAfter(LocalDateTime.now());
        } catch (Exception ex) {
            log.warn("【活动合成-活动列表】解析结束时间失败，activityId:{}, endTime:{}", item.getId(), item.getEndTime(), ex);
            return false;
        }
    }

    private String resolveActivityStage(SyntheticActivityListItemRes item) {
        if (!StringUtils.hasText(item.getStartTime())) {
            return ACTIVITY_STAGE_ONGOING;
        }
        try {
            LocalDateTime startTime = LocalDateTime.parse(item.getStartTime(), DATETIME_FORMATTER);
            return startTime.isAfter(LocalDateTime.now()) ? ACTIVITY_STAGE_UPCOMING : ACTIVITY_STAGE_ONGOING;
        } catch (Exception ex) {
            log.warn("【活动合成-活动列表】解析开始时间失败，activityId:{}, startTime:{}", item.getId(), item.getStartTime(), ex);
            return ACTIVITY_STAGE_ONGOING;
        }
    }

    private String formatMillis(Long millis) {
        return millis == null
            ? null
            : LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(DATETIME_FORMATTER);
    }

    private boolean isSuccess(IboxApiResponse<?> response) {
        return response != null && response.getCode() != null && response.getCode() == 0;
    }
}
