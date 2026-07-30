package com.ibox.utils.impl;

import com.ibox.utils.AnnouncementContentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Default announcement content parser.
 */
@Slf4j
@Component
public class DefaultAnnouncementContentParser implements AnnouncementContentParser {

    /**
     * Keywords that should be excluded from parse results.
     */
    private static final List<String> EXCLUDED_KEYWORDS = List.of("一壶春（锦鲤版）");

    /**
     * Noise markers used to cut off the trailing non-target goods section.
     */
    private static final List<String> NOISE_TAIL_KEYWORDS = List.of("本次合成结束", "后续活动区", "凤羽商店");

    /**
     * Markers indicating named collections should be moved into future-area / phoenix-feather pools.
     */
    private static final List<String> FUTURE_ZONE_BLOCK_MARKERS = List.of("移入后续区", "移入后续活动区", "移入凤羽商店", "凤羽商店");

    /**
     * Chooses a parse strategy based on announcement category.
     */
    @Override
    public List<String> parseCollectionNames(String title, String category, String content) {
        if (title.contains("公示") || title.contains("关闭求购")||title.contains("假如")) {
            return new LinkedList<>();
        }
        if (category != null && category.contains("寄售公告")) {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            extractCollectionNamesToSet(title, names, null);
            extractCollectionNamesToSet(toPlainText(content), names, null);
            List<String> result = new ArrayList<>(names);
            log.info("【公告内容解析】寄售公告解析结果:{}", result);
            return result;
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
        TailParseResult tailParseResult = parseTailInfo(originalPlainText);
        List<String> result = parseMaterialSegmentsOnly(tailParseResult.mainText(), tailParseResult.garbageGoods());

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
    private List<String> parseMaterialSegmentsOnly(String plainText, Set<String> garbageGoods) {
        LinkedHashSet<String> holdingResult = new LinkedHashSet<>();
        LinkedHashSet<String> materialResult = new LinkedHashSet<>();
        LinkedHashSet<String> blockedNames = new LinkedHashSet<>();
        collectFutureZoneBlockedNames(plainText, garbageGoods, blockedNames);
        collectHoldingCollectionNames(plainText, holdingResult, garbageGoods, blockedNames);
        String truncatedText = plainText;
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
            collectMaterialCollectionNames(materialBlock, materialResult, garbageGoods, blockedNames);
            searchFrom = nextChannelStart < 0 ? truncatedText.length() : nextChannelStart;
        }
        if (materialResult.isEmpty()) {
            String materialBlock = extractMaterialBlock(truncatedText);
            if (!materialBlock.isBlank()) {
                collectMaterialCollectionNames(materialBlock, materialResult, garbageGoods, blockedNames);
            }
        }
        if (!blockedNames.isEmpty()) {
            holdingResult.removeAll(blockedNames);
            materialResult.removeAll(blockedNames);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        result.addAll(holdingResult);
        result.addAll(materialResult);
        return new ArrayList<>(result);
    }

    /**
     * Collects names explicitly marked to move into future-area / phoenix-feather sections.
     */
    private void collectFutureZoneBlockedNames(String text, Set<String> garbageGoods, Set<String> blockedNames) {
        if (text == null || text.isBlank() || blockedNames == null) {
            return;
        }
        for (String rawLine : text.split("\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank() || !containsFutureZoneBlockMarker(line)) {
                continue;
            }
            blockedNames.addAll(parseClauseNames(line, garbageGoods, Collections.emptySet()).names());
        }
    }

    private boolean containsFutureZoneBlockMarker(String line) {
        for (String marker : FUTURE_ZONE_BLOCK_MARKERS) {
            if (line.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Collects material-side collection names and skips large "任意" pools.
     */
    private void collectMaterialCollectionNames(String materialBlock,
                                                LinkedHashSet<String> result,
                                                Set<String> garbageGoods,
                                                Set<String> blockedNames) {
        if (materialBlock == null || materialBlock.isBlank()) {
            return;
        }
        for (String clause : splitMaterialClauses(materialBlock)) {
            ClauseParseResult clauseParseResult = parseClauseNames(clause, garbageGoods, blockedNames);
            if (shouldSkipAnyPoolClause(clause, clauseParseResult)) {
                blockedNames.addAll(clauseParseResult.names());
                log.info("【公告内容解析】过滤任意大材料池子句:{}", clause.replace('\n', ' ').trim());
                continue;
            }
            result.addAll(clauseParseResult.names());
        }
    }

    /**
     * Splits a material block into smaller clauses so each rule can be filtered independently.
     */
    private List<String> splitMaterialClauses(String materialBlock) {
        List<String> clauses = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String rawLine : materialBlock.split("\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank() || "合成材料".equals(line) || "+".equals(line)) {
                if (current.length() > 0) {
                    clauses.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
        }
        if (current.length() > 0) {
            clauses.add(current.toString());
        }
        return clauses;
    }

    /**
     * Skips "任意" material clauses when the candidate collection count is too large.
     */
    private boolean shouldSkipAnyPoolClause(String clause, ClauseParseResult clauseParseResult) {
        if (clause == null || clause.isBlank() || !clause.contains("任意")) {
            return false;
        }
        return clauseParseResult.count() > 2;
    }

    /**
     * Collects only the holding-collection side from snapshot qualification text.
     */
    private void collectHoldingCollectionNames(String text,
                                               LinkedHashSet<String> result,
                                               Set<String> garbageGoods,
                                               Set<String> blockedNames) {
        if (text == null || text.isBlank()) {
            return;
        }
        int searchFrom = 0;
        while (searchFrom < text.length()) {
            int holdingIndex = text.indexOf("持有藏品", searchFrom);
            if (holdingIndex < 0) {
                break;
            }
            int end = text.length();
            end = minPositive(end, text.indexOf("持有藏品", holdingIndex + 4));
            end = minPositive(end, text.indexOf("快照时间", holdingIndex));
            end = minPositive(end, text.indexOf("空投发放时间", holdingIndex));
            end = minPositive(end, text.indexOf("合成材料", holdingIndex));
            end = minPositive(end, text.indexOf("合成时间", holdingIndex));
            end = minPositive(end, text.indexOf("最大空投数量", holdingIndex));
            end = minPositive(end, text.indexOf("最大合成数量", holdingIndex));
            end = minPositive(end, text.indexOf("通道", holdingIndex + 4));
            end = minPositive(end, text.indexOf("活动", holdingIndex + 4));
            end = minPositive(end, text.indexOf("注：", holdingIndex));
            end = minPositive(end, text.indexOf("注:", holdingIndex));
            String segment = text.substring(holdingIndex, end);
            ClauseParseResult clauseParseResult = parseClauseNames(segment, garbageGoods, blockedNames);
            if (shouldSkipHoldingClause(segment, clauseParseResult)) {
                blockedNames.addAll(clauseParseResult.names());
                log.info("【公告内容解析】过滤快照持有大材料池子句:{}", segment.replace('\n', ' ').trim());
                searchFrom = holdingIndex + 4;
                continue;
            }
            result.addAll(clauseParseResult.names());
            searchFrom = holdingIndex + 4;
        }
    }

    /**
     * Skips snapshot holding clauses when the candidate collection count is too large.
     */
    private boolean shouldSkipHoldingClause(String clause, ClauseParseResult clauseParseResult) {
        if (clause == null || clause.isBlank()) {
            return false;
        }
        return clauseParseResult.count() > 2;
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
     * Extracts collection names directly into the target set to reduce intermediate allocations.
     */
    private void extractCollectionNamesToSet(String block, LinkedHashSet<String> target, Set<String> garbageGoods) {
        if (block == null || block.isBlank() || target == null) {
            return;
        }
        target.addAll(parseClauseNames(block, garbageGoods, Collections.emptySet()).names());
    }

    /**
     * Parses all collection names inside a clause in one pass.
     */
    private ClauseParseResult parseClauseNames(String block, Set<String> garbageGoods, Set<String> blockedNames) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if (block == null || block.isBlank()) {
            return new ClauseParseResult(names, 0);
        }
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
            if (!name.isBlank()
                && !shouldExclude(name)
                && (garbageGoods == null || !garbageGoods.contains(name))
                && (blockedNames == null || !blockedNames.contains(name))) {
                names.add(name);
            }
            searchFrom = end + 1;
        }
        return new ClauseParseResult(names, names.size());
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
    private TailParseResult parseTailInfo(String text) {
        if (text == null || text.isBlank()) {
            return new TailParseResult(text, Collections.emptySet());
        }
        int startIndex = -1;
        for (String marker : NOISE_TAIL_KEYWORDS) {
            int markerIndex = text.indexOf(marker);
            if (markerIndex >= 0 && (startIndex < 0 || markerIndex < startIndex)) {
                startIndex = findLineStart(text, markerIndex);
            }
        }
        if (startIndex < 0) {
            return new TailParseResult(text, Collections.emptySet());
        }
        LinkedHashSet<String> garbageGoods = new LinkedHashSet<>();
        extractCollectionNamesToSet(text.substring(startIndex), garbageGoods, null);
        return new TailParseResult(text.substring(0, startIndex), garbageGoods);
    }

    /**
     * Finds the start index of the current line so tail parsing can include names before the marker.
     */
    private int findLineStart(String text, int index) {
        if (index <= 0) {
            return 0;
        }
        int lineStart = text.lastIndexOf('\n', index - 1);
        return lineStart < 0 ? 0 : lineStart + 1;
    }

    /**
     * Finds the next block marker such as "通道一" or "活动二".
     */
    private int findNextChannelStart(String text, int fromIndex) {
        int searchFrom = Math.max(fromIndex, 0);
        while (searchFrom < text.length()) {
            int channelCandidate = text.indexOf("通道", searchFrom);
            int activityCandidate = text.indexOf("活动", searchFrom);
            int candidate = firstPositive(channelCandidate, activityCandidate);
            if (candidate < 0) {
                return -1;
            }
            int suffixIndex = candidate + 2;
            if (suffixIndex < text.length() && isBlockSuffix(text.charAt(suffixIndex))) {
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
     * Checks whether the character after "通道"/"活动" looks like a valid block suffix.
     */
    private boolean isBlockSuffix(char value) {
        return value == '一' || value == '二' || value == '三' || value == '四'
                || value == '五' || value == '六' || value == '七' || value == '八'
                || value == '九' || value == '十'
                || (value >= '0' && value <= '9');
    }

    /**
     * Returns the earliest non-negative position among two candidates.
     */
    private int firstPositive(int left, int right) {
        if (left < 0) {
            return right;
        }
        if (right < 0) {
            return left;
        }
        return Math.min(left, right);
    }

    /**
     * Tail parse result containing the truncated main text and tail garbage goods.
     */
    private record TailParseResult(String mainText, Set<String> garbageGoods) {
    }

    /**
     * Clause-level parse result used to avoid rescanning the same text.
     */
    private record ClauseParseResult(LinkedHashSet<String> names, int count) {
    }

}
