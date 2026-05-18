package com.ibox.utils.impl;

import com.ibox.utils.AnnouncementContentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Default announcement content parser.
 */
@Slf4j
@Component
public class DefaultAnnouncementContentParser implements AnnouncementContentParser {

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
        if (originalPlainText.indexOf('《') < 0 && originalPlainText.indexOf("通道") < 0 && originalPlainText.indexOf("合成材料") < 0 && !originalPlainText.contains("持有藏品")) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> garbageGoods = new LinkedHashSet<>(extractTailGarbageGoods(originalPlainText));
        List<String> result = parseMaterialSegmentsOnly(originalPlainText, garbageGoods);

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
     * Converts rich-text content into plain text for lightweight scanning.
     */
    private String toPlainText(String content) {
        String normalized = content
                .replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")
                .replace("<BR>", "\n")
                .replace("<BR/>", "\n")
                .replace("<BR />", "\n")
                .replace("</p>", "\n")
                .replace("</P>", "\n")
                .replace("</li>", "\n")
                .replace("</LI>", "\n")
                .replace("</ul>", "\n")
                .replace("</UL>", "\n")
                .replace("&nbsp;", " ")
                .replace("　", " ")
                .replace("\r", "");
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean inTag = false;
        boolean previousNewline = false;
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (current == '<') {
                inTag = true;
                continue;
            }
            if (current == '>') {
                inTag = false;
                continue;
            }
            if (inTag) {
                continue;
            }
            if (current == '\n') {
                if (!previousNewline) {
                    builder.append('\n');
                    previousNewline = true;
                }
                continue;
            }
            previousNewline = false;
            builder.append(current);
        }
        return builder.toString();
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
     * Parses only the material segments from synthesis announcements.
     */
    private List<String> parseMaterialSegmentsOnly(String plainText, LinkedHashSet<String> garbageGoods) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String truncatedText = truncateNoiseTail(plainText);
        int searchFrom = 0;
        while (searchFrom >= 0 && searchFrom < truncatedText.length()) {
            int channelStart = findNextChannelStart(truncatedText, searchFrom);
            if (channelStart < 0) {
                break;
            }
            int channelBodyStart = findChannelBodyStart(truncatedText, channelStart);
            int nextChannelStart = findNextChannelStart(truncatedText, channelBodyStart);
            String channelBlock = nextChannelStart < 0
                    ? truncatedText.substring(channelBodyStart)
                    : truncatedText.substring(channelBodyStart, nextChannelStart);
            String materialBlock = extractMaterialBlock(channelBlock);
            if (materialBlock.isBlank()) {
                searchFrom = nextChannelStart < 0 ? truncatedText.length() : nextChannelStart;
                continue;
            }
            for (String name : extractCollectionNames(materialBlock)) {
                if (garbageGoods != null && garbageGoods.contains(name)) {
                    continue;
                }
                result.add(name);
            }
            searchFrom = nextChannelStart < 0 ? truncatedText.length() : nextChannelStart;
        }
        return new ArrayList<>(result);
    }

    /**
     * Extracts and filters collection names from a text block.
     */
    private List<String> extractCollectionNames(String block) {
        if (block == null || block.isBlank()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>();
        int searchFrom = 0;
        while (searchFrom < block.length()) {
            int start = block.indexOf('《', searchFrom);
            if (start < 0) {
                break;
            }
            int end = block.indexOf('》', start + 1);
            if (end < 0) {
                break;
            }
            String name = block.substring(start + 1, end).trim();
            if (!name.isBlank() && !shouldExclude(name)) {
                names.add(name);
            }
            searchFrom = end + 1;
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
     * Extracts goods explicitly listed in the trailing future-activity / fengyu-shop section.
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
     * Finds the next channel marker.
     */
    private int findNextChannelStart(String text, int fromIndex) {
        int searchFrom = Math.max(fromIndex, 0);
        while (searchFrom < text.length()) {
            int candidate = text.indexOf("通道", searchFrom);
            if (candidate < 0) {
                return -1;
            }
            int suffixIndex = candidate + 2;
            if (suffixIndex < text.length() && isChannelSuffix(text.charAt(suffixIndex))) {
                return candidate;
            }
            searchFrom = candidate + 2;
        }
        return -1;
    }

    /**
     * Skips the short channel title line and points to the channel body.
     */
    private int findChannelBodyStart(String text, int channelStart) {
        int newlineIndex = text.indexOf('\n', channelStart);
        return newlineIndex < 0 ? channelStart : newlineIndex + 1;
    }

    /**
     * Checks whether the character after "通道" looks like a valid channel suffix.
     */
    private boolean isChannelSuffix(char value) {
        return value == '一' || value == '二' || value == '三' || value == '四'
                || value == '五' || value == '六' || value == '七' || value == '八'
                || value == '九' || value == '十'
                || (value >= '0' && value <= '9');
    }

}
