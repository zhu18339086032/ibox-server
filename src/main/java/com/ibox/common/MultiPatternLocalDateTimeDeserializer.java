package com.ibox.common;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 兼容多种时间字符串格式的 LocalDateTime 反序列化器。
 *
 * <p>活动合成任务页面在不同操作路径下，前端可能会提交两种时间格式：</p>
 * <p>1. yyyy-MM-dd HH:mm:ss</p>
 * <p>2. yyyy-MM-dd'T'HH:mm:ss</p>
 *
 * <p>为了避免编辑回填后再次提交出现反序列化失败，这里统一做兼容处理。</p>
 */
public class MultiPatternLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    /**
     * 按优先级依次尝试的时间格式列表。
     *
     * <p>先尝试项目表单期望的空格格式，再兼容 ISO LocalDateTime 的 T 格式。</p>
     */
    private static final List<DateTimeFormatter> FORMATTERS = List.of(
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );

    @Override
    public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getValueAsString();
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String normalizedValue = value.trim();
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(normalizedValue, formatter);
            } catch (DateTimeParseException ignored) {
                // 继续尝试下一种格式，直到全部失败为止。
            }
        }

        throw new IOException("无法解析时间字段，支持格式: yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd'T'HH:mm:ss，实际值: " + value);
    }
}
