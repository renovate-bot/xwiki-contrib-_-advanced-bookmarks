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
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.xwiki.contrib.advancedbookmarks.BookmarkAccessDeniedException;
import org.xwiki.contrib.advancedbookmarks.BookmarkData;
import org.xwiki.contrib.advancedbookmarks.BookmarkEntry;
import org.xwiki.contrib.advancedbookmarks.BookmarkException;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStore;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Tests the export of the bookmarks of a whole wiki and the reading of such a file.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(BookmarkJsonCodec.class)
class BookmarkWikiTransferTest
{
    private static final DocumentReference ALICE = ReferenceStubs.page("wiki:XWiki.alice");

    private static final DocumentReference GLOBAL = ReferenceStubs.page("xwiki:XWiki.global");

    private static final String HEADER = "{\"format\":\"advanced-bookmarks-wiki\",\"formatVersion\":1,";

    private static final String EMPTY = "\"folders\":[],\"bookmarks\":[]";

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private BookmarkWikiTransfer wikiTransfer;

    @MockComponent
    private BookmarkStore store;

    @MockComponent
    private BookmarkTransfer transfer;

    @MockComponent
    private BookmarkRights rights;

    @BeforeComponent
    void registerReferences(MockitoComponentManager componentManager) throws Exception
    {
        ReferenceStubs.register(componentManager);
    }

    @BeforeEach
    void setUp()
    {
        when(this.rights.getCurrentWiki()).thenReturn(ReferenceStubs.WIKI);
    }

    @Test
    void exportsTheUsersOfTheWikiWithReferencesRelativeToIt() throws Exception
    {
        when(this.store.listUsers(ReferenceStubs.WIKI)).thenReturn(
            List.of("wiki:XWiki.alice", "xwiki:XWiki.global", "wiki:XWiki.gone", "wiki:XWiki.broken"));
        when(this.transfer.exportData(ALICE)).thenReturn(data("Space.Page", ""));
        when(this.transfer.exportData(GLOBAL)).thenReturn(new BookmarkData());
        when(this.transfer.exportData(ReferenceStubs.page("wiki:XWiki.gone")))
            .thenThrow(new BookmarkAccessDeniedException());
        when(this.transfer.exportData(ReferenceStubs.page("wiki:XWiki.broken")))
            .thenThrow(new BookmarkStoreException("corrupted", null));

        BookmarkWikiTransfer.WikiExport export = this.wikiTransfer.exportWiki();

        assertTrue(export.content().startsWith(HEADER + "\"exportedAt\":\""), export.content());
        assertTrue(export.content().endsWith("\"users\":[{\"user\":\"XWiki.alice\",\"folders\":[],\"bookmarks\":["
            + "{\"page\":\"Space.Page\",\"label\":\"\",\"folder\":\"\",\"order\":0,\"panel\":false}]},"
            + "{\"user\":\"xwiki:XWiki.global\"," + EMPTY + "}]}"), export.content());
        assertEquals(2, export.exported());
        assertEquals(2, export.skipped());
        assertEquals("The bookmarks of [wiki:XWiki.gone] were not exported: it is not an existing user.",
            this.logCapture.getMessage(0));
        assertEquals("The bookmarks of [wiki:XWiki.broken] were not exported: BookmarkStoreException: corrupted",
            this.logCapture.getMessage(1));

        // The export can be imported again.
        List<DocumentReference> users = new ArrayList<>();
        assertEquals(2, this.wikiTransfer.readUsers(bytes(export.content()), (user, bookmarks) -> users.add(user)));
        assertEquals(List.of(ALICE, GLOBAL), users);
    }

    @Test
    void refusesAnExportTooLargeToBeImported() throws Exception
    {
        // Each user is within the limits of one user, but not the whole file.
        int userSize = BookmarkJsonCodec.MAX_JSON_BYTES - 200;
        int userCount = BookmarkWikiTransfer.MAX_FILE_BYTES / userSize + 1;
        List<String> users = new ArrayList<>();
        for (int i = 0; i < userCount; i++) {
            String user = "wiki:XWiki.user" + i;
            users.add(user);
            when(this.transfer.exportData(ReferenceStubs.page(user)))
                .thenReturn(data("Space.Page", "l".repeat(userSize)));
        }
        when(this.store.listUsers(ReferenceStubs.WIKI)).thenReturn(users);

        assertThrows(BookmarkInvalidDataException.class, () -> this.wikiTransfer.exportWiki());
    }

    @Test
    void validatesAFileAndCountsItsUsers() throws Exception
    {
        String file = HEADER + "\"exportedAt\":\"2026-10-10T00:00:00Z\",\"future\":{\"nested\":[1,2]},\"users\":["
            + "{\"user\":\"XWiki.alice\"," + EMPTY + "},{\"user\":\"xwiki:XWiki.global\"," + EMPTY + "}]}";

        assertEquals(2, this.wikiTransfer.validate(bytes(file)));
    }

    @Test
    void acceptsAHeaderAfterTheUsers() throws Exception
    {
        String file = "{\"users\":[{\"user\":\"XWiki.alice\"," + EMPTY + "}],\"format\":\"advanced-bookmarks-wiki\","
            + "\"formatVersion\":1}";

        assertEquals(1, this.wikiTransfer.validate(bytes(file)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "[]",
        "{",
        "{\"format\":\"advanced-bookmarks\",\"formatVersion\":1,\"users\":[]}",
        "{\"format\":\"advanced-bookmarks-wiki\",\"formatVersion\":2,\"users\":[]}",
        "{\"format\":\"advanced-bookmarks-wiki\",\"formatVersion\":\"1\",\"users\":[]}",
        "{\"format\":\"advanced-bookmarks-wiki\",\"formatVersion\":1}",
        "{\"format\":\"advanced-bookmarks-wiki\",\"formatVersion\":1,\"users\":{}}",
        "{\"format\":\"advanced-bookmarks-wiki\",\"formatVersion\":1,\"users\":[1]}",
        "{\"format\":\"advanced-bookmarks-wiki\",\"formatVersion\":1,\"users\":[{\"folders\":[],\"bookmarks\":[]}]}",
        "{\"format\":\"advanced-bookmarks-wiki\",\"formatVersion\":1,\"users\":[{\"user\":3,"
            + "\"folders\":[],\"bookmarks\":[]}]}",
        "{\"format\":\"advanced-bookmarks-wiki\",\"formatVersion\":1,\"users\":[{\"user\":\" \","
            + "\"folders\":[],\"bookmarks\":[]}]}",
        "{\"format\":\"advanced-bookmarks-wiki\",\"formatVersion\":1,\"users\":[{\"user\":\"XWiki.alice\"}]}",
        "{\"format\":\"advanced-bookmarks-wiki\",\"formatVersion\":1,\"users\":["
            + "{\"user\":\"XWiki.alice\",\"folders\":[],\"bookmarks\":[]},"
            + "{\"user\":\"wiki:XWiki.alice\",\"folders\":[],\"bookmarks\":[]}]}",
        "{\"format\":\"advanced-bookmarks-wiki\",\"formatVersion\":1,\"users\":[],\"users\":[]}",
        "{\"format\":\"advanced-bookmarks-wiki\",\"formatVersion\":1,\"users\":[]} {}"
    })
    void rejectsAnInvalidFile(String file)
    {
        assertThrows(BookmarkInvalidDataException.class, () -> this.wikiTransfer.validate(bytes(file)));
    }

    @Test
    void rejectsAMissingOrOversizedFileAndATooLongUser()
    {
        assertThrows(BookmarkInvalidDataException.class, () -> this.wikiTransfer.validate(null));
        assertThrows(BookmarkInvalidDataException.class,
            () -> this.wikiTransfer.validate(new byte[BookmarkWikiTransfer.MAX_FILE_BYTES + 1]));
        String user = "XWiki." + "u".repeat(BookmarkJsonCodec.MAX_PAGE_REFERENCE_LENGTH);
        assertThrows(BookmarkInvalidDataException.class, () -> this.wikiTransfer.validate(
            bytes(HEADER + "\"users\":[{\"user\":\"" + user + "\"," + EMPTY + "}]}")));
    }

    @Test
    void rejectsTooManyUsers()
    {
        List<String> users = new ArrayList<>();
        for (int i = 0; i <= BookmarkWikiTransfer.MAX_USERS; i++) {
            users.add("{\"user\":\"XWiki.user" + i + "\"," + EMPTY + "}");
        }
        String file = HEADER + "\"users\":[" + String.join(",", users) + "]}";

        assertThrows(BookmarkInvalidDataException.class, () -> this.wikiTransfer.validate(bytes(file)));
    }

    @Test
    void propagatesTheFailureOfTheVisitor()
    {
        BookmarkException failure = new BookmarkException("failure", null);

        assertSame(failure, assertThrows(BookmarkException.class, () -> this.wikiTransfer.readUsers(
            bytes(HEADER + "\"users\":[{\"user\":\"XWiki.alice\"," + EMPTY + "}]}"), (user, bookmarks) -> {
                throw failure;
            })));
    }

    private static BookmarkData data(String page, String label)
    {
        BookmarkEntry bookmark = new BookmarkEntry();
        bookmark.setPage(page);
        bookmark.setLabel(label);
        BookmarkData data = new BookmarkData();
        data.setBookmarks(new ArrayList<>(List.of(bookmark)));
        return data;
    }

    private static byte[] bytes(String content)
    {
        return content.getBytes(StandardCharsets.UTF_8);
    }
}
