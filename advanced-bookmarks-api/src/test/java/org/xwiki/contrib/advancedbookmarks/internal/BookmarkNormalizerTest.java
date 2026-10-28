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
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.advancedbookmarks.BookmarkData;
import org.xwiki.contrib.advancedbookmarks.BookmarkEntry;
import org.xwiki.contrib.advancedbookmarks.BookmarkFolder;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * Tests the normalization of the bookmark data received from the browser.
 *
 * @version $Id$
 */
@ComponentTest
class BookmarkNormalizerTest
{
    private static final String WIKI = ReferenceStubs.WIKI;

    /**
     * A limit other than the default one, to check that the configured limit is applied.
     */
    private static final int PANEL_LIMIT = 3;

    @InjectMockComponents
    private BookmarkNormalizer normalizer;

    @MockComponent
    private BookmarkConfiguration configuration;

    @BeforeComponent
    void registerReferences(MockitoComponentManager componentManager) throws Exception
    {
        ReferenceStubs.register(componentManager);
    }

    @BeforeEach
    void setUp()
    {
        when(this.configuration.getPanelLimit()).thenReturn(BookmarkConfiguration.DEFAULT_PANEL_LIMIT);
    }

    @Test
    void keepsOnlyValidAndUniqueFolders() throws Exception
    {
        BookmarkData data = data(List.of(folder("ok", "Work", null), folder("bad id", "Bad", null),
            folder("ok", "Duplicate", null), folder(null, "No id", null), folder("x".repeat(41), "Long", null)),
            List.of());
        data.getFolders().add(null);

        this.normalizer.normalize(data, WIKI);

        assertEquals(List.of("ok"), data.getFolders().stream().map(BookmarkFolder::getId).toList());
        assertEquals("Work", data.getFolders().get(0).getName());
        assertEquals(BookmarkFolder.ROOT, data.getFolders().get(0).getParent());
    }

    @Test
    void boundsAndDefaultsFolderNames() throws Exception
    {
        BookmarkData data = data(List.of(folder("a", "  ", ""), folder("b", "n".repeat(150), "")), List.of());

        this.normalizer.normalize(data, WIKI);

        assertEquals("New folder", data.getFolders().get(0).getName());
        assertEquals(BookmarkNormalizer.MAX_FOLDER_NAME_LENGTH, data.getFolders().get(1).getName().length());
    }

    @Test
    void movesFoldersWithUnknownParentsOrCyclesToTheRoot() throws Exception
    {
        BookmarkData data = data(List.of(folder("orphan", "Orphan", "missing"), folder("a", "A", "b"),
            folder("b", "B", "a"), folder("self", "Self", "self"), folder("child", "Child", "orphan")), List.of());

        this.normalizer.normalize(data, WIKI);

        assertEquals(List.of("", "", "a", "", "orphan"),
            data.getFolders().stream().map(BookmarkFolder::getParent).toList());
    }

    @Test
    void keepsOnlyValidAndUniquePagesOfTheWiki() throws Exception
    {
        BookmarkData data = data(List.of(), List.of(bookmark("Space.Page", "Page"),
            bookmark("wiki:Space.Page", "Duplicate"), bookmark("other:Space.Page", "Other wiki"),
            bookmark(" ", "Blank"), bookmark(null, "None"), bookmark("Space." + "p".repeat(512), "Long"),
            bookmark("Space." + "p".repeat(506), "Too long once serialized")));
        data.getBookmarks().add(null);

        this.normalizer.normalize(data, WIKI);

        assertEquals(List.of("wiki:Space.Page"), data.getBookmarks().stream().map(BookmarkEntry::getPage).toList());
        assertEquals("Page", data.getBookmarks().get(0).getLabel());
    }

    @Test
    void boundsLabelsAndMovesBookmarksOfUnknownFoldersToTheRoot() throws Exception
    {
        BookmarkEntry inKnownFolder = bookmark("Space.A", null);
        inKnownFolder.setFolder("work");
        BookmarkEntry inUnknownFolder = bookmark("Space.B", "l".repeat(250));
        inUnknownFolder.setFolder("missing");
        BookmarkEntry withoutFolder = bookmark("Space.C", " C ");
        withoutFolder.setFolder(null);
        BookmarkData data = data(List.of(folder("work", "Work", "")),
            List.of(inKnownFolder, inUnknownFolder, withoutFolder));

        this.normalizer.normalize(data, WIKI);

        assertEquals(List.of("work", "", ""), data.getBookmarks().stream().map(BookmarkEntry::getFolder).toList());
        assertEquals("", inKnownFolder.getLabel());
        assertEquals(BookmarkNormalizer.MAX_LABEL_LENGTH, inUnknownFolder.getLabel().length());
        assertEquals("C", withoutFolder.getLabel());
    }

    @Test
    void limitsThePanelSelectionToTheConfiguredLimit() throws Exception
    {
        when(this.configuration.getPanelLimit()).thenReturn(PANEL_LIMIT);
        List<BookmarkEntry> bookmarks = new ArrayList<>();
        for (int i = 0; i < PANEL_LIMIT + 2; i++) {
            BookmarkEntry bookmark = bookmark("Space.Page" + i, "");
            bookmark.setPanel(true);
            bookmarks.add(bookmark);
        }
        BookmarkData data = data(List.of(), bookmarks);

        this.normalizer.normalize(data, WIKI);

        assertEquals(PANEL_LIMIT, data.getBookmarks().stream().filter(BookmarkEntry::isPanel).count());
        assertFalse(data.getBookmarks().get(PANEL_LIMIT).isPanel());
    }

    @Test
    void rejectsTooManyItems()
    {
        BookmarkData tooManyBookmarks =
            data(List.of(), Collections.nCopies(BookmarkJsonCodec.MAX_ITEMS + 1, new BookmarkEntry()));
        BookmarkData tooManyFolders =
            data(Collections.nCopies(BookmarkJsonCodec.MAX_ITEMS + 1, new BookmarkFolder()), List.of());

        assertThrows(BookmarkInvalidDataException.class, () -> this.normalizer.normalize(tooManyBookmarks, WIKI));
        assertThrows(BookmarkInvalidDataException.class, () -> this.normalizer.normalize(tooManyFolders, WIKI));
    }

    @Test
    void boundsTexts()
    {
        assertEquals("", BookmarkNormalizer.bound(null, 3));
        assertEquals("abc", BookmarkNormalizer.bound(" abcd ", 3));
    }

    private static BookmarkData data(List<BookmarkFolder> folders, List<BookmarkEntry> bookmarks)
    {
        BookmarkData data = new BookmarkData();
        data.setFolders(folders);
        data.setBookmarks(bookmarks);
        return data;
    }

    static BookmarkFolder folder(String id, String name, String parent)
    {
        BookmarkFolder folder = new BookmarkFolder();
        folder.setId(id);
        folder.setName(name);
        folder.setParent(parent);
        return folder;
    }

    static BookmarkEntry bookmark(String page, String label)
    {
        BookmarkEntry bookmark = new BookmarkEntry();
        bookmark.setPage(page);
        bookmark.setLabel(label);
        return bookmark;
    }
}
