package com.ibox.utils;

import java.util.List;

/**
 * Parses collection names from announcement content.
 */
public interface AnnouncementContentParser {

    /**
     * Parses collection names from a single announcement.
     *
     * @param title announcement title
     * @param category announcement category
     * @param content announcement detail content
     * @return parsed collection names
     */
    List<String> parseCollectionNames(String title, String category, String content);

    /**
     * Parses collection names from a synthesis announcement.
     *
     * @param content announcement content
     * @return parsed collection names
     */
    List<String> parseSynthesisAnnouncement(String content);
}
