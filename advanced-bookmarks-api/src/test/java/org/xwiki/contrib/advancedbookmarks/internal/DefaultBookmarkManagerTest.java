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
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.xwiki.contrib.advancedbookmarks.BookmarkAccessDeniedException;
import org.xwiki.contrib.advancedbookmarks.BookmarkData;
import org.xwiki.contrib.advancedbookmarks.BookmarkEntry;
import org.xwiki.contrib.advancedbookmarks.BookmarkFolder;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkRecord;
import org.xwiki.contrib.advancedbookmarks.BookmarkStore;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.contrib.advancedbookmarks.BookmarkTooLargeException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.xwiki.contrib.advancedbookmarks.internal.BookmarkNormalizerTest.bookmark;
import static org.xwiki.contrib.advancedbookmarks.internal.BookmarkNormalizerTest.folder;

/**
 * Tests the bookmark business logic, with a mocked store and mocked rights.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList({BookmarkJsonCodec.class, BookmarkNormalizer.class})
class DefaultBookmarkManagerTest
{
    private static final String WIKI = ReferenceStubs.WIKI;

    private static final DocumentReference USER = new DocumentReference("xwiki", "XWiki", "alice");

    private static final String USER_KEY = "xwiki:XWiki.alice";

    private static final DocumentReference PAGE = new DocumentReference(WIKI, "Space", "Page");

    private static final String PAGE_KEY = "wiki:Space.Page";

    /**
     * A limit other than the default one, to check that the configured limit is applied.
     */
    private static final int PANEL_LIMIT = 4;

    private static final String EMPTY_JSON = "{\"folders\":[],\"bookmarks\":[]}";

    private static final String PAGE_JSON =
        "{\"folders\":[],\"bookmarks\":[{\"page\":\"wiki:Space.Page\",\"label\":\"Page\"}]}";

    @InjectMockComponents
    private DefaultBookmarkManager manager;

    @MockComponent
    private BookmarkStore store;

    @MockComponent
    private BookmarkRights rights;

    @MockComponent
    private BookmarkPages pages;

    @MockComponent
    private BookmarkConfiguration configuration;

    private final BookmarkJsonCodec codec = new BookmarkJsonCodec();

    @BeforeComponent
    void registerReferences(MockitoComponentManager componentManager) throws Exception
    {
        ReferenceStubs.register(componentManager);
    }

    @BeforeEach
    void setUp()
    {
        when(this.rights.getCurrentWiki()).thenReturn(WIKI);
        when(this.rights.canManage(USER)).thenReturn(true);
        when(this.rights.canView(eq(USER), any())).thenReturn(true);
        when(this.pages.exists(any())).thenReturn(true);
        when(this.pages.getPlainTitle(PAGE)).thenReturn("Page title");
        when(this.configuration.getPanelLimit()).thenReturn(BookmarkConfiguration.DEFAULT_PANEL_LIMIT);
    }

    @Test
    void delegatesTheAccessCheckToTheRights()
    {
        assertTrue(this.manager.canManage(USER));
        assertFalse(this.manager.canManage(PAGE));
    }

    @Test
    void returnsEmptyBookmarksWhenNoneAreStored() throws Exception
    {
        when(this.store.get(WIKI, USER_KEY)).thenReturn(Optional.empty());

        BookmarkData data = this.manager.getBookmarks(USER);

        assertEquals(0L, data.getVersion());
        assertTrue(data.getFolders().isEmpty());
        assertTrue(data.getBookmarks().isEmpty());
    }

    @Test
    void readsNormalizedBookmarksWithTheirVersion() throws Exception
    {
        stored("{\"folders\":[],\"bookmarks\":[{\"page\":\"Space.Page\"},{\"page\":\"other:Space.Page\"}]}", 12L);

        BookmarkData data = this.manager.getBookmarks(USER);

        assertEquals(12L, data.getVersion());
        assertEquals(List.of(PAGE_KEY), data.getBookmarks().stream().map(BookmarkEntry::getPage).toList());
    }

    @Test
    void refusesToReadTheBookmarksOfAnotherUser()
    {
        assertThrows(BookmarkAccessDeniedException.class, () -> this.manager.getBookmarks(PAGE));

        verifyNoInteractions(this.store);
    }

    @Test
    void reportsCorruptedStoredDataAsAStorageFailure() throws Exception
    {
        stored("{\"folders\":[],\"bookmarks\":[{\"order\":\"first\"}]}", 3L);

        BookmarkStoreException exception =
            assertThrows(BookmarkStoreException.class, () -> this.manager.getBookmarks(USER));
        assertFalse(exception instanceof BookmarkInvalidDataException);
    }

    @Test
    void tellsWhetherAPageIsBookmarked() throws Exception
    {
        when(this.store.containsPage(WIKI, USER_KEY, PAGE_KEY)).thenReturn(true);

        assertTrue(this.manager.isBookmarked(USER, PAGE));
        assertFalse(this.manager.isBookmarked(USER, new DocumentReference(WIKI, "Space", "Other")));
        assertThrows(BookmarkAccessDeniedException.class, () -> this.manager.isBookmarked(PAGE, PAGE));
        // The bookmarks themselves are not read.
        verify(this.store, never()).get(anyString(), anyString());
    }

    @Test
    void addsAnUncategorizedBookmarkLabelledWithThePageTitle() throws Exception
    {
        stored("{\"folders\":[{\"id\":\"f\",\"name\":\"F\"}],\"bookmarks\":[{\"page\":\"Space.A\"},"
            + "{\"page\":\"Space.B\",\"folder\":\"f\"}]}", 3L);
        when(this.store.save(eq(WIKI), eq(USER_KEY), anyString(), eq(3L))).thenReturn(4L);

        assertTrue(this.manager.toggle(USER, PAGE));

        BookmarkEntry added = savedData().getBookmarks().get(2);
        assertEquals(PAGE_KEY, added.getPage());
        assertEquals("Page title", added.getLabel());
        assertEquals(BookmarkFolder.ROOT, added.getFolder());
        assertEquals(1, added.getOrder());
    }

    @Test
    void addsABookmarkWithoutLabelForAMissingPage() throws Exception
    {
        when(this.store.get(WIKI, USER_KEY)).thenReturn(Optional.empty());
        when(this.pages.exists(PAGE)).thenReturn(false);

        assertTrue(this.manager.toggle(USER, PAGE));

        assertEquals("", savedData().getBookmarks().get(0).getLabel());
        verify(this.store).save(eq(WIKI), eq(USER_KEY), anyString(), eq(0L));
        verify(this.pages, never()).getPlainTitle(any());
    }

    @Test
    void removesAnExistingBookmark() throws Exception
    {
        stored(PAGE_JSON, 3L);

        assertFalse(this.manager.toggle(USER, PAGE));

        assertTrue(savedData().getBookmarks().isEmpty());
    }

    @Test
    void refusesToBookmarkAnExcludedPage() throws Exception
    {
        stored(EMPTY_JSON, 3L);
        when(this.configuration.isExcluded(PAGE)).thenReturn(true);

        assertThrows(BookmarkAccessDeniedException.class, () -> this.manager.toggle(USER, PAGE));

        verify(this.store, never()).save(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void stillRemovesTheBookmarkOfAnExcludedPage() throws Exception
    {
        stored(PAGE_JSON, 3L);
        when(this.configuration.isExcluded(PAGE)).thenReturn(true);

        assertFalse(this.manager.toggle(USER, PAGE));

        assertTrue(savedData().getBookmarks().isEmpty());
    }

    @Test
    void retriesAToggleAfterAConflict() throws Exception
    {
        stored(EMPTY_JSON, 3L);
        when(this.store.save(eq(WIKI), eq(USER_KEY), anyString(), anyLong()))
            .thenThrow(new BookmarkStoreConflictException(null)).thenReturn(5L);

        assertTrue(this.manager.toggle(USER, PAGE));

        verify(this.store, times(2)).save(eq(WIKI), eq(USER_KEY), anyString(), anyLong());
    }

    @Test
    void givesUpAToggleAfterThreeConflicts() throws Exception
    {
        stored(EMPTY_JSON, 3L);
        when(this.store.save(eq(WIKI), eq(USER_KEY), anyString(), anyLong()))
            .thenThrow(new BookmarkStoreConflictException(null));

        assertThrows(BookmarkStoreConflictException.class, () -> this.manager.toggle(USER, PAGE));

        verify(this.store, times(3)).save(eq(WIKI), eq(USER_KEY), anyString(), anyLong());
    }

    @Test
    void refusesToBookmarkAPageTheUserCannotView() throws Exception
    {
        stored(EMPTY_JSON, 3L);
        when(this.rights.canView(USER, PAGE)).thenReturn(false);

        assertThrows(BookmarkAccessDeniedException.class, () -> this.manager.toggle(USER, PAGE));

        verify(this.store, never()).save(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void refusesToBookmarkAPageOfAnotherWikiOrForAnotherUser()
    {
        DocumentReference otherWikiPage = new DocumentReference("other", "Space", "Page");

        assertThrows(BookmarkInvalidDataException.class, () -> this.manager.toggle(USER, otherWikiPage));
        assertThrows(BookmarkAccessDeniedException.class, () -> this.manager.toggle(PAGE, PAGE));

        verifyNoInteractions(this.store);
    }

    @Test
    void refusesToExceedTheMaximumSize() throws Exception
    {
        // Bookmarks filling the allowed space, with less room left than the bookmark of the page takes.
        BookmarkData data = new BookmarkData();
        // The list of the data itself, since setBookmarks copies the given list.
        List<BookmarkEntry> bookmarks = data.getBookmarks();
        for (int i = 0;; i++) {
            BookmarkEntry bookmark = new BookmarkEntry();
            // Already normalized, so that reading them does not make any room.
            bookmark.setPage("wiki:S.P" + i);
            bookmark.setLabel("");
            bookmark.setFolder(BookmarkFolder.ROOT);
            bookmark.setOrder(i);
            bookmarks.add(bookmark);
            if (this.codec.getSize(data) > BookmarkJsonCodec.MAX_JSON_BYTES) {
                bookmarks.remove(bookmark);
                break;
            }
        }
        stored(this.codec.write(data), 3L);

        assertThrows(BookmarkTooLargeException.class, () -> this.manager.toggle(USER, PAGE));
        verify(this.store, never()).save(any(), any(), any(), anyLong());
    }

    @Test
    void savesNormalizedBookmarksInDisplayOrder() throws Exception
    {
        BookmarkEntry inFolder = bookmark("Space.InFolder", "In folder");
        inFolder.setFolder("f");
        BookmarkData data = new BookmarkData();
        data.setFolders(List.of(folder("f", "F", "")));
        data.setBookmarks(List.of(inFolder, bookmark("Space.Root", "Root"), bookmark("other:Space.X", "X")));
        data.setVersion(7L);
        when(this.store.save(eq(WIKI), eq(USER_KEY), anyString(), eq(7L))).thenReturn(8L);

        assertEquals(8L, this.manager.save(USER, data));

        assertEquals(List.of("wiki:Space.Root", "wiki:Space.InFolder"),
            savedData().getBookmarks().stream().map(BookmarkEntry::getPage).toList());
    }

    @Test
    void addsOnlyPagesThatAreNotExcludedAndThatTheOwnerMayView()
    {
        DocumentReference hidden = new DocumentReference(WIKI, "Space", "Hidden");
        DocumentReference excluded = new DocumentReference(WIKI, "Space", "Excluded");
        when(this.rights.canView(USER, hidden)).thenReturn(false);
        when(this.configuration.isExcluded(excluded)).thenReturn(true);

        assertTrue(this.manager.canAdd(USER, PAGE));
        assertFalse(this.manager.canAdd(USER, hidden));
        assertFalse(this.manager.canAdd(USER, excluded));
    }

    @Test
    void refusesToSaveANewPageThatTheOwnerCannotView() throws Exception
    {
        stored(EMPTY_JSON, 3L);
        when(this.rights.canView(USER, PAGE)).thenReturn(false);
        BookmarkData data = new BookmarkData();
        data.setBookmarks(List.of(bookmark("Space.Page", "Secret")));
        data.setVersion(3L);

        assertThrows(BookmarkAccessDeniedException.class, () -> this.manager.save(USER, data));

        verify(this.store, never()).save(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void refusesToSaveANewExcludedPage() throws Exception
    {
        stored(EMPTY_JSON, 3L);
        when(this.configuration.isExcluded(PAGE)).thenReturn(true);
        BookmarkData data = new BookmarkData();
        data.setBookmarks(List.of(bookmark("Space.Page", "Excluded")));
        data.setVersion(3L);

        assertThrows(BookmarkAccessDeniedException.class, () -> this.manager.save(USER, data));

        verify(this.store, never()).save(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void keepsAStoredPageThatCanNoLongerBeAdded() throws Exception
    {
        stored(PAGE_JSON, 3L);
        when(this.rights.canView(USER, PAGE)).thenReturn(false);
        when(this.configuration.isExcluded(PAGE)).thenReturn(true);
        BookmarkData data = new BookmarkData();
        data.setBookmarks(List.of(bookmark("Space.Page", "Renamed")));
        data.setVersion(3L);
        when(this.store.save(eq(WIKI), eq(USER_KEY), anyString(), eq(3L))).thenReturn(4L);

        assertEquals(4L, this.manager.save(USER, data));

        assertEquals("Renamed", savedData().getBookmarks().get(0).getLabel());
    }

    @Test
    void refusesToSaveTheBookmarksOfAnotherUser()
    {
        assertThrows(BookmarkAccessDeniedException.class, () -> this.manager.save(PAGE, new BookmarkData()));

        verifyNoInteractions(this.store);
    }

    @Test
    void ordersUncategorizedBookmarksFirstThenFoldersDepthFirst()
    {
        BookmarkData data = new BookmarkData();
        data.setFolders(List.of(withOrder(folder("b", "B", ""), 2), withOrder(folder("a", "A", ""), 1),
            withOrder(folder("a2", "A2", "a"), 2), withOrder(folder("a1", "A1", "a"), 1)));
        data.setBookmarks(List.of(in("b", 0), in("a1", 0), in("a", 1), in("a", 0), in("", 1), in("", 0),
            in("a2", 0)));

        List<String> order = this.manager.getDisplayOrder(data).stream().map(BookmarkEntry::getLabel).toList();

        assertEquals(List.of("/0", "/1", "a1/0", "a2/0", "a/0", "a/1", "b/0"), order);
    }

    @Test
    void showsTheSelectedPanelBookmarksOrTheFirstOnes()
    {
        when(this.configuration.getPanelLimit()).thenReturn(PANEL_LIMIT);
        List<BookmarkEntry> bookmarks = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            bookmarks.add(in("", i));
        }
        BookmarkData data = new BookmarkData();
        data.setBookmarks(bookmarks);

        assertEquals(PANEL_LIMIT, this.manager.getPanel(data).size());
        assertEquals("/0", this.manager.getPanel(data).get(0).getLabel());

        data.getBookmarks().get(12).setPanel(true);
        data.getBookmarks().get(3).setPanel(true);

        assertEquals(List.of("/3", "/12"),
            this.manager.getPanel(data).stream().map(BookmarkEntry::getLabel).toList());
    }

    private void stored(String json, long version) throws BookmarkStoreException
    {
        when(this.store.get(WIKI, USER_KEY)).thenReturn(Optional.of(new BookmarkRecord(json, version)));
    }

    private BookmarkData savedData() throws BookmarkStoreException
    {
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(this.store).save(eq(WIKI), eq(USER_KEY), json.capture(), anyLong());
        return this.codec.read(json.getValue());
    }

    private static BookmarkFolder withOrder(BookmarkFolder folder, int order)
    {
        folder.setOrder(order);
        return folder;
    }

    /**
     * @return a bookmark labelled with its folder and order, to check the display order
     */
    private static BookmarkEntry in(String folder, int order)
    {
        BookmarkEntry bookmark = bookmark("Space." + folder + order, folder + "/" + order);
        bookmark.setFolder(folder);
        bookmark.setOrder(order);
        return bookmark;
    }
}
