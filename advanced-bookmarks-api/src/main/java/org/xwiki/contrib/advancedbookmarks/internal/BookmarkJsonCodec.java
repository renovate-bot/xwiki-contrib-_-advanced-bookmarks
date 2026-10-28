/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.advancedbookmarks.internal;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkData;
import org.xwiki.contrib.advancedbookmarks.BookmarkEntry;
import org.xwiki.contrib.advancedbookmarks.BookmarkFolder;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkTooLargeException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Converts bookmark data to and from the JSON stored in Solr, and enforces the size limits of that JSON.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = BookmarkJsonCodec.class)
@Singleton
public class BookmarkJsonCodec
{
    /**
     * Maximum size of the JSON of one user, in UTF-8 bytes. It is the largest value Lucene accepts in the
     * {@code docValues} of the {@code string} field type of Solr, which the stored JSON uses: do not raise it without
     * changing the Solr schema, which existing cores cannot change in place.
     */
    public static final int MAX_JSON_BYTES = 32_766;

    /**
     * Maximum number of folders, and separately of bookmarks, of one user.
     */
    public static final int MAX_ITEMS = 10_000;

    /**
     * Maximum length of a serialized page reference.
     */
    public static final int MAX_PAGE_REFERENCE_LENGTH = 512;

    private static final String FOLDERS = "folders";

    private static final String BOOKMARKS = "bookmarks";

    private static final String PAGE = "page";

    // Unknown properties are ignored so that data written by a later version can still be read.
    private static final ObjectMapper JSON =
        new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Parse bookmark JSON into typed data. The version of the result is {@code 0}.
     *
     * @param json the JSON, with the {@code folders} and {@code bookmarks} root properties
     * @return the parsed data, which may still need normalization
     * @throws BookmarkInvalidDataException if the JSON is malformed or exceeds the limits
     */
    public BookmarkData read(String json) throws BookmarkInvalidDataException
    {
        return read(json, Map.of());
    }

    /**
     * Parse bookmark JSON into typed data, after checking its header. The version of the result is {@code 0}.
     *
     * @param json the JSON, with the {@code folders} and {@code bookmarks} root properties
     * @param requiredHeader the root properties the JSON must have, with these exact values
     * @return the parsed data, which may still need normalization
     * @throws BookmarkInvalidDataException if the JSON is malformed, exceeds the limits or lacks the header
     */
    public BookmarkData read(String json, Map<String, Object> requiredHeader) throws BookmarkInvalidDataException
    {
        ObjectNode root = parse(json);
        for (Map.Entry<String, Object> property : requiredHeader.entrySet()) {
            if (!JSON.valueToTree(property.getValue()).equals(root.get(property.getKey()))) {
                throw new BookmarkInvalidDataException(null);
            }
        }
        return toData(root);
    }

    /**
     * Convert a JSON object, such as an element of a wiki export file, into typed data, with the same limits as the
     * JSON of one user. The version of the result is {@code 0}.
     *
     * @param node a JSON object with the {@code folders} and {@code bookmarks} properties; the other properties are
     *            ignored
     * @return the converted data, which may still need normalization
     * @throws BookmarkInvalidDataException if the node is not such an object or exceeds the limits
     */
    public BookmarkData read(JsonNode node) throws BookmarkInvalidDataException
    {
        if (node == null) {
            throw new BookmarkInvalidDataException(null);
        }
        checkSize(node.toString());
        return toData(validate(node));
    }

    /**
     * @param data the data to convert; the version is not converted
     * @return a JSON object holding the {@code folders} and {@code bookmarks} of the data
     */
    public ObjectNode toTree(BookmarkData data)
    {
        ObjectNode root = JSON.createObjectNode();
        root.set(FOLDERS, JSON.valueToTree(data.getFolders()));
        root.set(BOOKMARKS, JSON.valueToTree(data.getBookmarks()));
        return root;
    }

    private static BookmarkData toData(ObjectNode root) throws BookmarkInvalidDataException
    {
        try {
            BookmarkData data = new BookmarkData();
            data.setFolders(Arrays.asList(JSON.treeToValue(root.get(FOLDERS), BookmarkFolder[].class)));
            data.setBookmarks(Arrays.asList(JSON.treeToValue(root.get(BOOKMARKS), BookmarkEntry[].class)));
            return data;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new BookmarkInvalidDataException(e);
        }
    }

    /**
     * Serialize the folders and bookmarks of the given data. The version is stored separately and not serialized.
     *
     * @param data the data to serialize
     * @return the JSON
     * @throws BookmarkTooLargeException if the JSON exceeds {@link #MAX_JSON_BYTES}
     */
    public String write(BookmarkData data) throws BookmarkTooLargeException
    {
        return write(data, Map.of());
    }

    /**
     * Serialize the folders and bookmarks of the given data after a header. The version is not serialized.
     *
     * @param data the data to serialize
     * @param header root properties written before the folders and bookmarks
     * @return the JSON
     * @throws BookmarkTooLargeException if the JSON exceeds {@link #MAX_JSON_BYTES}
     */
    public String write(BookmarkData data, Map<String, Object> header) throws BookmarkTooLargeException
    {
        ObjectNode root = JSON.createObjectNode();
        header.forEach((name, value) -> root.set(name, JSON.valueToTree(value)));
        root.setAll(toTree(data));
        String json = serialize(root);
        // Checked on writing too, since JSON that reading would reject could never be read back.
        checkSize(json);
        return json;
    }

    /**
     * @param data the data to measure
     * @return the size of its JSON as {@link #write(BookmarkData)} produces it, in UTF-8 bytes, to be compared with
     *         {@link #MAX_JSON_BYTES}
     */
    public int getSize(BookmarkData data)
    {
        return getSize(serialize(toTree(data)));
    }

    /**
     * Extract the distinct page references of bookmark JSON, which are indexed to find the users of a page.
     *
     * @param json the JSON to inspect
     * @return the page references, in order of first appearance
     * @throws BookmarkInvalidDataException if the JSON is malformed, exceeds the limits or has an invalid page
     */
    public Set<String> getPageReferences(String json) throws BookmarkInvalidDataException
    {
        Set<String> pages = new LinkedHashSet<>();
        for (JsonNode bookmark : getBookmarkNodes(parse(json))) {
            JsonNode page = bookmark.get(PAGE);
            if (!isValidPage(page)) {
                throw new BookmarkInvalidDataException(null);
            }
            pages.add(page.textValue());
        }
        return pages;
    }

    /**
     * Replace a page reference in bookmark JSON, leaving every other property untouched.
     *
     * @param json the JSON to update
     * @param oldPage the page reference to replace
     * @param newPage the replacement page reference
     * @return the updated JSON
     * @throws BookmarkInvalidDataException if the JSON is invalid, or would exceed {@link #MAX_JSON_BYTES} with the
     *             longer replacement page reference
     */
    public String replacePage(String json, String oldPage, String newPage) throws BookmarkInvalidDataException
    {
        ObjectNode root = parse(json);
        for (JsonNode bookmark : getBookmarkNodes(root)) {
            JsonNode page = bookmark.get(PAGE);
            if (page != null && page.isTextual() && page.textValue().equals(oldPage)) {
                ((ObjectNode) bookmark).put(PAGE, newPage);
            }
        }
        String result = serialize(root);
        checkSize(result);
        return result;
    }

    private static ArrayNode getBookmarkNodes(ObjectNode root) throws BookmarkInvalidDataException
    {
        ArrayNode bookmarks = (ArrayNode) root.get(BOOKMARKS);
        for (JsonNode bookmark : bookmarks) {
            if (!bookmark.isObject()) {
                throw new BookmarkInvalidDataException(null);
            }
        }
        return bookmarks;
    }

    private static boolean isValidPage(JsonNode page)
    {
        // The text value of a node that is not a string is null, hence invalid.
        return page != null && BookmarkReferences.isValidReference(page.textValue());
    }

    private static ObjectNode parse(String json) throws BookmarkInvalidDataException
    {
        if (json == null || json.isBlank()) {
            throw new BookmarkInvalidDataException(null);
        }
        checkSize(json);
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JsonProcessingException e) {
            throw new BookmarkInvalidDataException(e);
        }
        return validate(root);
    }

    private static ObjectNode validate(JsonNode root) throws BookmarkInvalidDataException
    {
        if (root == null || !root.isObject() || !isBoundedArray(root.get(FOLDERS))
            || !isBoundedArray(root.get(BOOKMARKS))) {
            throw new BookmarkInvalidDataException(null);
        }
        return (ObjectNode) root;
    }

    private static void checkSize(String json) throws BookmarkTooLargeException
    {
        if (getSize(json) > MAX_JSON_BYTES) {
            throw new BookmarkTooLargeException();
        }
    }

    private static int getSize(String json)
    {
        return json.getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean isBoundedArray(JsonNode node)
    {
        return node != null && node.isArray() && node.size() <= MAX_ITEMS;
    }

    private static String serialize(ObjectNode root)
    {
        // Unlike ObjectMapper#writeValueAsString, JsonNode#toString cannot fail on a tree built in memory.
        return root.toString();
    }
}
