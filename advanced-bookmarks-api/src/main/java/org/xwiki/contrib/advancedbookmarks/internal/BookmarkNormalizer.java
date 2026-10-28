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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkData;
import org.xwiki.contrib.advancedbookmarks.BookmarkEntry;
import org.xwiki.contrib.advancedbookmarks.BookmarkFolder;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

/**
 * Makes bookmark data consistent before it is stored, whatever the browser sent: valid and unique identifiers,
 * bounded texts, known parent folders, no folder cycle, unique pages of the expected wiki and a bounded panel.
 * Invalid items are dropped rather than rejected, so that one bad item does not lose the user's other changes.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = BookmarkNormalizer.class)
@Singleton
public class BookmarkNormalizer
{
    /**
     * Maximum length of a folder name.
     */
    public static final int MAX_FOLDER_NAME_LENGTH = 100;

    /**
     * Maximum length of a bookmark label.
     */
    public static final int MAX_LABEL_LENGTH = 200;

    private static final Pattern FOLDER_ID = Pattern.compile("^[A-Za-z0-9_-]{1,40}$");

    private static final String DEFAULT_FOLDER_NAME = "New folder";

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> pageResolver;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private BookmarkConfiguration configuration;

    /**
     * Normalize bookmark data in place.
     *
     * @param data the data to normalize
     * @param wiki the wiki the bookmarks belong to; pages of other wikis are dropped
     * @throws BookmarkInvalidDataException if the data has more folders or bookmarks than supported
     */
    public void normalize(BookmarkData data, String wiki) throws BookmarkInvalidDataException
    {
        if (data.getFolders().size() > BookmarkJsonCodec.MAX_ITEMS
            || data.getBookmarks().size() > BookmarkJsonCodec.MAX_ITEMS) {
            throw new BookmarkInvalidDataException(null);
        }
        Map<String, BookmarkFolder> folders = normalizeFolders(data);
        normalizeBookmarks(data, folders.keySet(), wiki);
    }

    /**
     * @param reference a page or user reference
     * @return the full serialized reference, including the wiki, under which it is stored
     */
    public String serialize(DocumentReference reference)
    {
        return this.serializer.serialize(reference);
    }

    /**
     * @param text a text, possibly {@code null}
     * @param maxLength the maximum length
     * @return the trimmed text, cut to the maximum length, never {@code null}
     */
    public static String bound(String text, int maxLength)
    {
        String result = text == null ? "" : text.trim();
        return result.length() > maxLength ? result.substring(0, maxLength) : result;
    }

    private Map<String, BookmarkFolder> normalizeFolders(BookmarkData data)
    {
        Map<String, BookmarkFolder> folders = new HashMap<>();
        List<BookmarkFolder> validFolders = new ArrayList<>();
        for (BookmarkFolder folder : data.getFolders()) {
            if (isValidNewFolder(folder, folders)) {
                String name = bound(folder.getName(), MAX_FOLDER_NAME_LENGTH);
                folder.setName(name.isEmpty() ? DEFAULT_FOLDER_NAME : name);
                folders.put(folder.getId(), folder);
                validFolders.add(folder);
            }
        }
        for (BookmarkFolder folder : validFolders) {
            if (folder.getParent() == null || !folders.containsKey(folder.getParent())) {
                folder.setParent(BookmarkFolder.ROOT);
            }
        }
        for (BookmarkFolder folder : validFolders) {
            if (isInCycle(folder, folders)) {
                // Moving the folder to the root breaks the cycle while keeping its content.
                folder.setParent(BookmarkFolder.ROOT);
            }
        }
        data.setFolders(validFolders);
        return folders;
    }

    private static boolean isValidNewFolder(BookmarkFolder folder, Map<String, BookmarkFolder> folders)
    {
        return folder != null && folder.getId() != null && FOLDER_ID.matcher(folder.getId()).matches()
            && !folders.containsKey(folder.getId());
    }

    private static boolean isInCycle(BookmarkFolder folder, Map<String, BookmarkFolder> folders)
    {
        Set<String> visited = new HashSet<>();
        String current = folder.getParent();
        while (!BookmarkFolder.ROOT.equals(current)) {
            if (current.equals(folder.getId()) || !visited.add(current)) {
                return true;
            }
            current = folders.get(current).getParent();
        }
        return false;
    }

    private void normalizeBookmarks(BookmarkData data, Set<String> folderIds, String wiki)
    {
        Set<String> pages = new HashSet<>();
        List<BookmarkEntry> validBookmarks = new ArrayList<>();
        int panelCount = 0;
        // Also applied on every read: lowering the limit shortens the panel at once, and the next save makes it final.
        int panelLimit = this.configuration.getPanelLimit();
        for (BookmarkEntry bookmark : data.getBookmarks()) {
            String page = normalizePage(bookmark, wiki);
            if (page != null && pages.add(page)) {
                bookmark.setPage(page);
                bookmark.setLabel(bound(bookmark.getLabel(), MAX_LABEL_LENGTH));
                if (bookmark.getFolder() == null || !folderIds.contains(bookmark.getFolder())) {
                    bookmark.setFolder(BookmarkFolder.ROOT);
                }
                bookmark.setPanel(bookmark.isPanel() && panelCount < panelLimit);
                if (bookmark.isPanel()) {
                    panelCount++;
                }
                validBookmarks.add(bookmark);
            }
        }
        data.setBookmarks(validBookmarks);
    }

    /**
     * @return the canonical page reference, or {@code null} if the bookmark has no valid page of the wiki
     */
    private String normalizePage(BookmarkEntry bookmark, String wiki)
    {
        if (bookmark == null || !BookmarkReferences.isValidReference(bookmark.getPage())) {
            return null;
        }
        DocumentReference reference = this.pageResolver.resolve(bookmark.getPage());
        // Bookmarks are isolated per wiki: a page of another wiki would escape rename tracking.
        if (!wiki.equals(reference.getWikiReference().getName())) {
            return null;
        }
        String page = serialize(reference);
        // The canonical reference, with its wiki, may be longer than the reference received.
        return BookmarkReferences.isValidReference(page) ? page : null;
    }
}
