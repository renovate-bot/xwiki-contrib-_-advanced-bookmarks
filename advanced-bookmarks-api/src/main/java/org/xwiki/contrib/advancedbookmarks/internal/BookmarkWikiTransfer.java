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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkAccessDeniedException;
import org.xwiki.contrib.advancedbookmarks.BookmarkException;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStore;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Exports the bookmarks of all the users of the current wiki to a single JSON file, and reads such a file. Each user
 * is written like the export file of {@link BookmarkTransfer}, with a {@code user} property:
 * {@code {"format":"advanced-bookmarks-wiki","formatVersion":1,"exportedAt":…,"users":[{"user":…,"folders":[…],
 * "bookmarks":[…]}]}}.
 * <p>
 * User references are written relative to the current wiki: local users without their wiki, so that the file can be
 * imported into another wiki, and global users with it, so that they stay global.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = BookmarkWikiTransfer.class)
@Singleton
public class BookmarkWikiTransfer
{
    /**
     * Value of the {@code format} property, which identifies a wiki export file.
     */
    public static final String FORMAT = "advanced-bookmarks-wiki";

    /**
     * Maximum size of a wiki export file, in bytes. Each user is also limited like the export of one user.
     */
    public static final int MAX_FILE_BYTES = 33_554_432;

    /**
     * Maximum number of users in a wiki export file.
     */
    public static final int MAX_USERS = 10_000;

    private static final String USERS = "users";

    private static final String USER = "user";

    private static final Map<String, Object> HEADER =
        Map.of(BookmarkTransfer.FORMAT_KEY, FORMAT, BookmarkTransfer.FORMAT_VERSION_KEY,
            BookmarkTransfer.FORMAT_VERSION);

    private static final ObjectMapper JSON = new ObjectMapper();

    @Inject
    private BookmarkStore store;

    @Inject
    private BookmarkTransfer transfer;

    @Inject
    private BookmarkJsonCodec jsonCodec;

    @Inject
    private BookmarkRights rights;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @Inject
    @Named("compactwiki")
    private EntityReferenceSerializer<String> compactSerializer;

    @Inject
    private Logger logger;

    /**
     * The outcome of a wiki export.
     *
     * @param content the export file, as compact JSON
     * @param exported the number of users whose bookmarks were exported
     * @param skipped the number of users whose bookmarks could not be read, the reason being logged
     */
    public record WikiExport(String content, int exported, int skipped)
    {
    }

    /**
     * Receives the users of a wiki export file, one at a time.
     */
    @FunctionalInterface
    public interface UserVisitor
    {
        /**
         * @param user the user, resolved in the current wiki
         * @param bookmarks the JSON object of the user, with the {@code folders} and {@code bookmarks} properties
         * @throws BookmarkException if the user cannot be processed
         */
        void visit(DocumentReference user, JsonNode bookmarks) throws BookmarkException;
    }

    /**
     * Export the bookmarks of all the users of the current wiki. Rights are those of
     * {@link BookmarkTransfer#exportData}: only an administrator of the wiki can read the bookmarks of every user.
     *
     * @return the export file and the number of users exported and skipped
     * @throws BookmarkInvalidDataException if the file would exceed {@link #MAX_FILE_BYTES}, and could therefore not
     *             be imported
     * @throws BookmarkStoreException if the users cannot be listed
     */
    public WikiExport exportWiki() throws BookmarkException
    {
        ObjectNode root = JSON.createObjectNode();
        BookmarkTransfer.createHeader(FORMAT).forEach((name, value) -> root.set(name, JSON.valueToTree(value)));
        ArrayNode users = root.putArray(USERS);
        int skipped = 0;
        for (String storedUser : this.store.listUsers(this.rights.getCurrentWiki())) {
            DocumentReference user = this.resolver.resolve(storedUser);
            try {
                // Read before the node is added, so that a failure leaves no partial entry.
                ObjectNode bookmarks = this.jsonCodec.toTree(this.transfer.exportData(user));
                ObjectNode entry = users.addObject();
                entry.put(USER, this.compactSerializer.serialize(user));
                entry.setAll(bookmarks);
            } catch (BookmarkAccessDeniedException e) {
                // The bookmarks of deleted users are normally deleted with them: this is a leftover.
                skipped++;
                this.logger.warn("The bookmarks of [{}] were not exported: it is not an existing user.", storedUser);
            } catch (BookmarkStoreException e) {
                // One corrupted document must not prevent the export of all the others.
                skipped++;
                this.logger.warn("The bookmarks of [{}] were not exported: {}",
                    storedUser, ExceptionUtils.getRootCauseMessage(e));
                this.logger.debug("Full error:", e);
            }
        }
        String content = root.toString();
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
            throw new BookmarkInvalidDataException(null);
        }
        return new WikiExport(content, users.size(), skipped);
    }

    /**
     * Check a whole wiki export file without modifying anything: format, user references and limits.
     *
     * @param content the content of the file
     * @return the number of users in the file
     * @throws BookmarkInvalidDataException if the file is invalid
     */
    public int validate(byte[] content) throws BookmarkException
    {
        return readUsers(content, (user, bookmarks) -> this.jsonCodec.read(bookmarks));
    }

    /**
     * Read a wiki export file progressively, without loading the whole file in memory as a tree. The visitor may
     * receive users before an invalid part of the file is found: call {@link #validate} first so that a file is
     * processed entirely or not at all.
     *
     * @param content the content of the file
     * @param visitor receives each user of the file, in order
     * @return the number of users in the file
     * @throws BookmarkInvalidDataException if the file is invalid
     * @throws BookmarkException if the visitor fails
     */
    public int readUsers(byte[] content, UserVisitor visitor) throws BookmarkException
    {
        if (content == null || content.length > MAX_FILE_BYTES) {
            throw new BookmarkInvalidDataException(null);
        }
        try (JsonParser parser = JSON.createParser(content)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new BookmarkInvalidDataException(null);
            }
            Map<String, JsonNode> header = new HashMap<>();
            int userCount = readProperties(parser, visitor, header);
            // Nothing may follow the root object.
            if (parser.nextToken() != null || !isValidHeader(header)) {
                throw new BookmarkInvalidDataException(null);
            }
            return userCount;
        } catch (IOException e) {
            // Malformed JSON, or JSON exceeding the parser constraints.
            throw new BookmarkInvalidDataException(e);
        }
    }

    /**
     * Read the properties of the root object, up to its end. The header can be anywhere among them, so it is
     * collected and checked by the caller once the whole object has been read.
     *
     * @return the number of users
     */
    private int readProperties(JsonParser parser, UserVisitor visitor, Map<String, JsonNode> header)
        throws IOException, BookmarkException
    {
        int userCount = -1;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String name = parser.currentName();
            parser.nextToken();
            if (USERS.equals(name)) {
                // A second list would be imported after the first one: it is refused like a duplicate user.
                if (userCount >= 0) {
                    throw new BookmarkInvalidDataException(null);
                }
                userCount = readUserArray(parser, visitor);
            } else if (HEADER.containsKey(name)) {
                header.put(name, parser.readValueAsTree());
            } else {
                // Unknown properties are ignored, as in the export of one user.
                parser.skipChildren();
            }
        }
        if (parser.currentToken() != JsonToken.END_OBJECT || userCount < 0) {
            throw new BookmarkInvalidDataException(null);
        }
        return userCount;
    }

    private static boolean isValidHeader(Map<String, JsonNode> header)
    {
        return HEADER.entrySet().stream()
            .allMatch(property -> JSON.valueToTree(property.getValue()).equals(header.get(property.getKey())));
    }

    private int readUserArray(JsonParser parser, UserVisitor visitor) throws IOException, BookmarkException
    {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw new BookmarkInvalidDataException(null);
        }
        Set<DocumentReference> users = new HashSet<>();
        JsonToken token;
        while ((token = parser.nextToken()) == JsonToken.START_OBJECT) {
            if (users.size() >= MAX_USERS) {
                throw new BookmarkInvalidDataException(null);
            }
            JsonNode entry = parser.readValueAsTree();
            DocumentReference user = resolveUser(entry.get(USER));
            // A user listed twice would be imported twice, the second import merging with or replacing the first.
            if (!users.add(user)) {
                throw new BookmarkInvalidDataException(null);
            }
            visitor.visit(user, entry);
        }
        if (token != JsonToken.END_ARRAY) {
            throw new BookmarkInvalidDataException(null);
        }
        return users.size();
    }

    private DocumentReference resolveUser(JsonNode user) throws BookmarkInvalidDataException
    {
        // The text value of a node that is not a string is null, hence invalid.
        if (user == null || !BookmarkReferences.isValidReference(user.textValue())) {
            throw new BookmarkInvalidDataException(null);
        }
        return this.resolver.resolve(user.textValue());
    }
}
