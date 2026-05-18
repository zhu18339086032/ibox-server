package com.ibox.utils.impl;

import com.ibox.utils.AnnouncementContentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default announcement content parser.
 */
@Slf4j
@Component
public class DefaultAnnouncementContentParser implements AnnouncementContentParser {

    /**
     * Matches collection names wrapped by Chinese title marks.
     */
    private static final Pattern COLLECTION_NAME_PATTERN = Pattern.compile("《(.*?)》");

    /**
     * Splits synthesis content into channel-specific blocks.
     */
    private static final Pattern CHANNEL_BLOCK_PATTERN = Pattern.compile(
            "(通道[一二三四五六七八九十0-9]+(?:（[^）]*）)?)(.*?)(?=通道[一二三四五六七八九十0-9]+(?:（[^）]*）)?|【iBox链盒风险提示】|【iBox链盒郑重提醒广大用户】|$)",
            Pattern.DOTALL
    );

    /**
     * Matches exact holding collection names in announcement text.
     */
    private static final Pattern HOLDING_COLLECTION_PATTERN = Pattern.compile("持有藏品《(.*?)》");

    /**
     * Matches the holding collection description segment.
     */
    private static final Pattern HOLDING_SEGMENT_PATTERN = Pattern.compile("持有藏品.*?(?:合成资格|快照时间|合成材料|合成时间|$)", Pattern.DOTALL);

    /**
     * Keywords that should be excluded from parse results.
     */
    private static final List<String> EXCLUDED_KEYWORDS = List.of("万能材料", "新春限定", "扶光踏梦", "秀山", "大镖客", "一路牛", "蕃红李李·巴蛇", "领头狼");

    /**
     * Noise markers used to cut off the trailing non-target goods section.
     */
    private static final List<String> NOISE_TAIL_KEYWORDS = List.of("本次合成结束", "后续活动区", "凤羽商店");

    /**
     * Chooses a parse strategy based on announcement category.
     */
    @Override
    public List<String> parseCollectionNames(String title, String category, String content) {
        if (category != null && category.contains("寄售公告")) {
            List<String> result = new ArrayList<>(new LinkedHashSet<>(extractCollectionNames(title)));
            log.info("【公告内容解析】寄售公告标题解析结果:{}", result);
            return result;
        }
        if (category != null && category.contains("合成公告") && isAdjustmentTitle(title)) {
            log.info("【公告内容解析】合成公告标题命中调整类关键词，跳过解析: {}", title);
            return Collections.emptyList();
        }
        return parseSynthesisAnnouncement(content);
    }

    /**
     * Parses candidate collection names from synthesis content.
     */
    @Override
    public List<String> parseSynthesisAnnouncement(String content) {
        if (content == null || content.isBlank()) {
            return Collections.emptyList();
        }

        String originalPlainText = toPlainText(content);
        LinkedHashSet<String> garbageGoods = new LinkedHashSet<>(extractTailGarbageGoods(originalPlainText));
        String plainText = truncateNoiseTail(originalPlainText);
        if (plainText.indexOf('《') < 0 && !plainText.contains("持有藏品")) {
            return Collections.emptyList();
        }
        Matcher channelMatcher = CHANNEL_BLOCK_PATTERN.matcher(plainText);
        LinkedHashSet<String> fallbackResult = new LinkedHashSet<>();
        boolean matchedChannel = false;

        while (channelMatcher.find()) {
            matchedChannel = true;
            String channelTitle = channelMatcher.group(1);
            String channelBlock = truncateNoiseTail(channelMatcher.group(2));

            LinkedHashSet<String> currentNames = new LinkedHashSet<>();
            currentNames.addAll(filterGarbageGoods(extractHoldingCollectionNames(channelBlock), garbageGoods));
            currentNames.addAll(filterGarbageGoods(extractCollectionNames(extractMaterialBlock(channelBlock)), garbageGoods));
            if (currentNames.isEmpty()) {
                continue;
            }

            if (isPriorityChannel(channelTitle, channelBlock)) {
                List<String> priorityResult = new ArrayList<>(currentNames);
                log.info("【公告内容解析】命中优先通道，优先返回结果:{}", priorityResult);
                return priorityResult;
            }

            fallbackResult.addAll(currentNames);
        }

        List<String> result;
        if (matchedChannel) {
            result = new ArrayList<>(fallbackResult);
        } else {
            result = new ArrayList<>(new LinkedHashSet<>(filterGarbageGoods(extractCollectionNames(truncateNoiseTail(plainText)), garbageGoods)));
        }

        log.info("【公告内容解析】合成公告解析结果:{}", result);
        return result;
    }

    /**
     * Checks whether a synthesis title is likely to be a pure adjustment notice.
     */
    private boolean isAdjustmentTitle(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        return title.contains("时间调整")
                || title.contains("活动时间调整")
                || title.contains("公告调整")
                || title.contains("修正");
    }

    /**
     * Converts rich-text content into plain text for regex processing.
     */
    private String toPlainText(String content) {
        return content
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("(?i)</ul>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("　", " ")
                .replace("\r", "")
                .replaceAll("\\n{2,}", "\n");
    }

    /**
     * Extracts the material section from one channel block.
     */
    private String extractMaterialBlock(String channelBlock) {
        int materialStart = channelBlock.indexOf("合成材料");
        if (materialStart < 0) {
            return "";
        }

        String materialSection = channelBlock.substring(materialStart);
        int endIndex = materialSection.length();
        endIndex = minPositive(endIndex, materialSection.indexOf("合成时间"));
        endIndex = minPositive(endIndex, materialSection.indexOf("最大合成数量"));
        endIndex = minPositive(endIndex, materialSection.indexOf("提示"));
        endIndex = minPositive(endIndex, materialSection.indexOf("注："));
        endIndex = minPositive(endIndex, materialSection.indexOf("注:"));
        endIndex = minPositive(endIndex, materialSection.indexOf("本次合成结束"));
        endIndex = minPositive(endIndex, materialSection.indexOf("后续活动区"));
        endIndex = minPositive(endIndex, materialSection.indexOf("凤羽商店"));
        return materialSection.substring(0, endIndex);
    }

    /**
     * Detects whether the channel should be prioritized.
     */
    private boolean isPriorityChannel(String channelTitle, String channelBlock) {
        return channelTitle.contains("抢合")
                || channelBlock.contains("抢合")
                || channelBlock.contains("限量");
    }

    /**
     * Extracts and filters collection names from a text block.
     */
    private List<String> extractCollectionNames(String block) {
        if (block == null || block.isBlank()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        Matcher matcher = COLLECTION_NAME_PATTERN.matcher(block);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (name != null && !name.isBlank() && !shouldExclude(name.trim())) {
                names.add(name.trim());
            }
        }
        return names;
    }

    /**
     * Extracts collection names from holding-related content.
     */
    private List<String> extractHoldingCollectionNames(String block) {
        if (block == null || block.isBlank()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        Matcher exactMatcher = HOLDING_COLLECTION_PATTERN.matcher(block);
        while (exactMatcher.find()) {
            String name = exactMatcher.group(1);
            if (name != null && !name.isBlank() && !shouldExclude(name.trim())) {
                names.add(name.trim());
            }
        }

        Matcher segmentMatcher = HOLDING_SEGMENT_PATTERN.matcher(block);
        while (segmentMatcher.find()) {
            String segment = segmentMatcher.group();
            for (String name : extractCollectionNames(segment)) {
                if (!names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    /**
     * Checks whether a parsed name should be excluded.
     */
    private boolean shouldExclude(String name) {
        for (String keyword : EXCLUDED_KEYWORDS) {
            if (name.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the smaller non-negative index.
     */
    private int minPositive(int current, int candidate) {
        if (candidate >= 0 && candidate < current) {
            return candidate;
        }
        return current;
    }

    /**
     * Cuts off the tail section that usually contains converted-shop / future-activity goods.
     */
    private String truncateNoiseTail(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        int endIndex = text.length();
        for (String marker : NOISE_TAIL_KEYWORDS) {
            int markerIndex = text.indexOf(marker);
            if (markerIndex >= 0 && markerIndex < endIndex) {
                endIndex = markerIndex;
            }
        }
        return endIndex < text.length() ? text.substring(0, endIndex) : text;
    }

    /**
     * Extracts goods listed in the trailing future-activity / fengyu-shop section.
     */
    private List<String> extractTailGarbageGoods(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        int startIndex = -1;
        for (String marker : NOISE_TAIL_KEYWORDS) {
            int markerIndex = text.indexOf(marker);
            if (markerIndex >= 0 && (startIndex < 0 || markerIndex < startIndex)) {
                startIndex = markerIndex;
            }
        }
        if (startIndex < 0) {
            return Collections.emptyList();
        }
        return extractCollectionNames(text.substring(startIndex));
    }

    /**
     * Removes goods that are explicitly marked as future-activity / fengyu-shop leftovers.
     */
    private List<String> filterGarbageGoods(List<String> names, LinkedHashSet<String> garbageGoods) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        if (garbageGoods == null || garbageGoods.isEmpty()) {
            return names;
        }
        List<String> filtered = new ArrayList<>();
        for (String name : names) {
            if (name == null || name.isBlank() || garbageGoods.contains(name)) {
                continue;
            }
            filtered.add(name);
        }
        return filtered;
    }
}
