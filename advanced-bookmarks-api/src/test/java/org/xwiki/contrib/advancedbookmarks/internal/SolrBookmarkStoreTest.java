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
import java.util.List;
import java.util.Optional;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException.ErrorCode;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkRecord;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.search.solr.Solr;
import org.xwiki.search.solr.SolrException;
import org.xwiki.search.solr.XWikiSolrCore;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests the Solr storage component with a mocked Solr client.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(BookmarkJsonCodec.class)
class SolrBookmarkStoreTest
{
    private static final String WIKI = "wiki";

    private static final String USER = "xwiki:XWiki.User";

    private static final String VERSION = "_version_";

    private static final String OLD_PAGE = "wiki:Old.Page";

    private static final String NEW_PAGE = "wiki:New.Page";

    private static final String BOOKMARK_JSON =
        "{\"folders\":[],\"bookmarks\":[{\"page\":\"wiki:Space.Page\",\"label\":\"Page\"}]}";

    private static final String OLD_JSON = "{\"folders\":[],\"bookmarks\":[{\"page\":\"wiki:Old.Page\"}]}";

    private static final String NEW_JSON = "{\"folders\":[],\"bookmarks\":[{\"page\":\"wiki:New.Page\"}]}";

    @InjectMockComponents
    private SolrBookmarkStore store;

    @MockComponent
    private Solr solr;

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    private SolrClient client;

    private XWikiSolrCore core;

    @BeforeEach
    void setUp() throws Exception
    {
        this.client = mock(SolrClient.class);
        this.core = mock(XWikiSolrCore.class);
        when(this.solr.getCore(SolrBookmarkStore.CORE_NAME)).thenReturn(this.core);
        when(this.core.getClient()).thenReturn(this.client);
    }

    @Test
    void readsNothingWhenNoDocumentIsStored() throws Exception
    {
        QueryResponse empty = responseWith();
        when(this.client.query(any(SolrQuery.class))).thenReturn(empty);

        assertEquals(Optional.empty(), this.store.get(WIKI, USER));
    }

    @Test
    void readsTheStoredJsonAndVersion() throws Exception
    {
        QueryResponse response = responseWith(bookmarkDocument(BOOKMARK_JSON, 7L, "wiki:Space.Page"));
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        assertEquals(Optional.of(new BookmarkRecord(BOOKMARK_JSON, 7L)), this.store.get(WIKI, USER));
    }

    @Test
    void readsADocumentWithoutBookmarks() throws Exception
    {
        String json = "{\"folders\":[],\"bookmarks\":[]}";
        QueryResponse response = responseWith(bookmarkDocument(json, 2L));
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        assertEquals(Optional.of(new BookmarkRecord(json, 2L)), this.store.get(WIKI, USER));
    }

    @Test
    void rejectsADocumentWithInvalidFields() throws Exception
    {
        SolrDocument withoutData = new SolrDocument();
        withoutData.setField(VERSION, 2L);
        SolrDocument withoutVersion = bookmarkDocument(BOOKMARK_JSON, 0L, "wiki:Space.Page");
        withoutVersion.setField(VERSION, "2");
        QueryResponse first = responseWith(withoutData);
        QueryResponse second = responseWith(withoutVersion);
        when(this.client.query(any(SolrQuery.class))).thenReturn(first, second);

        assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, USER));
        assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, USER));
    }

    @Test
    void readsTheStoredJsonWithoutParsingIt() throws Exception
    {
        // Validating the data is left to the caller: reading runs on every page view.
        QueryResponse response = responseWith(bookmarkDocument("{", 7L));
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        assertEquals(Optional.of(new BookmarkRecord("{", 7L)), this.store.get(WIKI, USER));

        ArgumentCaptor<SolrQuery> query = ArgumentCaptor.forClass(SolrQuery.class);
        verify(this.client).query(query.capture());
        assertEquals("data,_version_", query.getValue().getFields());
    }

    @Test
    void findsAPageAmongTheIndexedPagesOfAUser() throws Exception
    {
        QueryResponse found = responseWith();
        found.getResults().setNumFound(1);
        QueryResponse missing = responseWith();
        when(this.client.query(any(SolrQuery.class))).thenReturn(found, missing);

        assertTrue(this.store.containsPage(WIKI, USER, OLD_PAGE));
        assertFalse(this.store.containsPage(WIKI, USER, NEW_PAGE));

        ArgumentCaptor<SolrQuery> query = ArgumentCaptor.forClass(SolrQuery.class);
        verify(this.client, times(2)).query(query.capture());
        assertEquals(List.of("id:4\\:wiki16\\:xwiki\\:XWiki.User", "pages:wiki\\:Old.Page"),
            List.of(query.getAllValues().get(0).getFilterQueries()));
        assertEquals(0, query.getAllValues().get(0).getRows());
    }

    @Test
    void neverFindsAPageReferenceTooLongToBeStored() throws Exception
    {
        assertFalse(this.store.containsPage(WIKI, USER, "p".repeat(513)));
        assertThrows(BookmarkStoreException.class, () -> this.store.containsPage(WIKI, USER, " "));
        assertThrows(BookmarkStoreException.class, () -> this.store.containsPage(WIKI, null, OLD_PAGE));

        verifyNoInteractions(this.client);
    }

    @Test
    void wrapsFailuresToFindAPage() throws Exception
    {
        when(this.client.query(any(SolrQuery.class))).thenThrow(new IOException("down"))
            .thenThrow(new org.apache.solr.common.SolrException(ErrorCode.SERVER_ERROR, "remote failure"));

        assertThrows(BookmarkStoreException.class, () -> this.store.containsPage(WIKI, USER, OLD_PAGE));
        assertThrows(BookmarkStoreException.class, () -> this.store.containsPage(WIKI, USER, OLD_PAGE));
    }

    @Test
    void rejectsDuplicateDocuments() throws Exception
    {
        QueryResponse response = responseWith(bookmarkDocument(BOOKMARK_JSON, 7L, "wiki:Space.Page"),
            bookmarkDocument(BOOKMARK_JSON, 8L, "wiki:Space.Page"));
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, USER));
    }

    @Test
    void wrapsSolrFailures() throws Exception
    {
        when(this.client.query(any(SolrQuery.class))).thenThrow(new IOException("down"));

        BookmarkStoreException exception =
            assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, USER));
        assertInstanceOf(IOException.class, exception.getCause());
    }

    @Test
    void wrapsAnUnavailableCore() throws Exception
    {
        when(this.solr.getCore(SolrBookmarkStore.CORE_NAME)).thenThrow(new SolrException("missing core"));

        assertThrows(BookmarkStoreException.class, () -> this.store.listUsers(WIKI));
    }

    @Test
    void reportsACoreThatXWikiCouldNotCreateAsAStorageFailure() throws Exception
    {
        XWikiSolrCore coreWithoutClient = mock(XWikiSolrCore.class);
        when(this.solr.getCore(SolrBookmarkStore.CORE_NAME)).thenReturn(coreWithoutClient, (XWikiSolrCore) null);

        assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, USER));
        assertThrows(BookmarkStoreException.class, () -> this.store.save(WIKI, USER, BOOKMARK_JSON, 0L));
    }

    @Test
    void rejectsInvalidIdentities()
    {
        assertThrows(BookmarkStoreException.class, () -> this.store.get(" ", USER));
        assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, null));
        assertThrows(BookmarkStoreException.class, () -> this.store.get(WIKI, "u".repeat(513)));
        assertThrows(BookmarkStoreException.class, () -> this.store.deleteUser(""));
        assertThrows(BookmarkStoreException.class, () -> this.store.deleteWiki(null));
        assertThrows(BookmarkStoreException.class, () -> this.store.replacePage(WIKI, "", NEW_PAGE));
        assertThrows(BookmarkStoreException.class, () -> this.store.replacePage(" ", OLD_PAGE, NEW_PAGE));

        verifyNoInteractions(this.client);
    }

    @Test
    void savesJsonAndIndexesItsPageReferencesInOneCommittedRequest() throws Exception
    {
        when(this.client.request(any(SolrRequest.class), any())).thenReturn(written(4L));

        long version = this.store.save(WIKI, USER, BOOKMARK_JSON, 3L);

        assertEquals(4L, version);
        UpdateRequest request = capturedWrites(1).get(0);
        SolrInputDocument saved = request.getDocuments().get(0);
        assertEquals(WIKI, saved.getFieldValue(SolrBookmarkStore.WIKI_FIELD));
        assertEquals(USER, saved.getFieldValue(SolrBookmarkStore.USER_FIELD));
        assertEquals("wiki:Space.Page", saved.getFieldValue(SolrBookmarkStore.PAGES_FIELD));
        assertEquals(3L, saved.getFieldValue(VERSION));
        assertEquals("true", request.getParams().get("versions"));
        assertEquals("true", request.getParams().get("commit"));
        // Nothing is read back: the version comes from the response.
        verify(this.client, never()).query(any(SolrQuery.class));
        verify(this.client, never()).commit();
    }

    @Test
    void requiresThatANewDocumentDoesNotExistYet() throws Exception
    {
        when(this.client.request(any(SolrRequest.class), any())).thenReturn(written(1L));

        this.store.save(WIKI, USER, BOOKMARK_JSON, 0L);

        assertEquals(-1L, capturedWrites(1).get(0).getDocuments().get(0).getFieldValue(VERSION));
    }

    @Test
    void rejectsInvalidJsonOrVersionBeforeCallingSolr()
    {
        assertThrows(BookmarkInvalidDataException.class, () -> this.store.save(WIKI, USER, "{}", 0L));
        assertThrows(BookmarkStoreException.class, () -> this.store.save(WIKI, USER, BOOKMARK_JSON, -1L));

        verifyNoInteractions(this.client);
    }

    @Test
    void reportsAFailureIfSolrDoesNotReturnTheVersion() throws Exception
    {
        NamedList<Object> withoutVersion = new NamedList<>();
        NamedList<Object> withInvalidVersion = new NamedList<>();
        NamedList<Object> adds = new NamedList<>();
        adds.add("id", "4");
        withInvalidVersion.add("adds", adds);
        when(this.client.request(any(SolrRequest.class), any())).thenReturn(null, withoutVersion, withInvalidVersion);

        for (int attempt = 0; attempt < 3; attempt++) {
            BookmarkStoreException exception =
                assertThrows(BookmarkStoreException.class, () -> this.store.save(WIKI, USER, BOOKMARK_JSON, 3L));
            assertFalse(exception instanceof BookmarkStoreConflictException);
        }
    }

    @Test
    void wrapsWriteFailures() throws Exception
    {
        when(this.client.request(any(SolrRequest.class), any())).thenThrow(new IOException("down"));

        BookmarkStoreException exception =
            assertThrows(BookmarkStoreException.class, () -> this.store.save(WIKI, USER, BOOKMARK_JSON, 3L));
        assertFalse(exception instanceof BookmarkStoreConflictException);
    }

    @Test
    void mapsTheNativeSolrVersionConflict() throws Exception
    {
        when(this.client.request(any(SolrRequest.class), any())).thenThrow(versionConflict());

        assertThrows(BookmarkStoreConflictException.class, () -> this.store.save(WIKI, USER, BOOKMARK_JSON, 3L));
    }

    @Test
    void mapsAWrappedSolrVersionConflict() throws Exception
    {
        when(this.client.request(any(SolrRequest.class), any()))
            .thenThrow(new org.apache.solr.client.solrj.SolrServerException(versionConflict()));

        assertThrows(BookmarkStoreConflictException.class, () -> this.store.save(WIKI, USER, BOOKMARK_JSON, 3L));
    }

    @Test
    void deletesAUserInEveryWiki() throws Exception
    {
        this.store.deleteUser(USER);

        verify(this.client).deleteByQuery("user:xwiki\\:XWiki.User");
        verify(this.client).commit();
    }

    @Test
    void deletesAWiki() throws Exception
    {
        this.store.deleteWiki(WIKI);

        verify(this.client).deleteByQuery("wiki:wiki");
        verify(this.client).commit();
    }

    @Test
    void wrapsDeletionFailures() throws Exception
    {
        when(this.client.deleteByQuery(anyString())).thenThrow(new IOException("down"));

        assertThrows(BookmarkStoreException.class, () -> this.store.deleteWiki(WIKI));
    }

    @Test
    void listsUsersOverSeveralPagesInAlphabeticalOrder() throws Exception
    {
        QueryResponse first = responseWith(userDocument("xwiki:XWiki.Zoe"));
        when(first.getNextCursorMark()).thenReturn("next");
        QueryResponse second = responseWith(userDocument("xwiki:XWiki.adam"));
        when(second.getNextCursorMark()).thenReturn("next");
        when(this.client.query(any(SolrQuery.class))).thenReturn(first, second);

        assertEquals(List.of("xwiki:XWiki.adam", "xwiki:XWiki.Zoe"), this.store.listUsers(WIKI));
    }

    @Test
    void refusesToReturnTooManyUsers() throws Exception
    {
        SolrDocument[] page = new SolrDocument[500];
        java.util.Arrays.fill(page, userDocument(USER));
        QueryResponse response = responseWith(page);
        int[] calls = {0};
        when(response.getNextCursorMark()).thenAnswer(invocation -> "cursor" + calls[0]++);
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        assertThrows(BookmarkStoreException.class, () -> this.store.listUsers(WIKI));
    }

    @Test
    void looksForTheUsersOfARenamedPageWithinTheWiki() throws Exception
    {
        QueryResponse response = responseWith();
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        assertEquals(0, this.store.replacePage(WIKI, OLD_PAGE, NEW_PAGE));

        ArgumentCaptor<SolrQuery> queryCaptor = ArgumentCaptor.forClass(SolrQuery.class);
        verify(this.client).query(queryCaptor.capture());
        assertEquals(List.of("wiki:wiki", "pages:wiki\\:Old.Page"), List.of(queryCaptor.getValue().getFilterQueries()));
    }

    @Test
    void replacesPageReferencesAndUpdatesTheSolrIndex() throws Exception
    {
        QueryResponse usersResponse = responseWith(userDocument(USER));
        QueryResponse currentResponse = responseWith(bookmarkDocument(OLD_JSON, 3L, OLD_PAGE));
        when(this.client.query(any(SolrQuery.class))).thenReturn(usersResponse, currentResponse);
        when(this.client.request(any(SolrRequest.class), any())).thenReturn(written(4L));

        assertEquals(1, this.store.replacePage(WIKI, OLD_PAGE, NEW_PAGE));

        SolrInputDocument saved = capturedWrites(1).get(0).getDocuments().get(0);
        assertEquals(NEW_JSON, saved.getFieldValue(SolrBookmarkStore.DATA_FIELD));
        assertEquals(NEW_PAGE, saved.getFieldValue(SolrBookmarkStore.PAGES_FIELD));
    }

    @Test
    void retriesAPageReplacementAfterAConflict() throws Exception
    {
        QueryResponse usersResponse = responseWith(userDocument(USER));
        QueryResponse currentResponse = responseWith(bookmarkDocument(OLD_JSON, 3L, OLD_PAGE));
        when(this.client.query(any(SolrQuery.class))).thenReturn(usersResponse, currentResponse, currentResponse);
        when(this.client.request(any(SolrRequest.class), any())).thenThrow(versionConflict())
            .thenReturn(written(5L));

        assertEquals(1, this.store.replacePage(WIKI, OLD_PAGE, NEW_PAGE));

        capturedWrites(2);
    }

    @Test
    void givesUpAPageReplacementAfterThreeConflicts() throws Exception
    {
        QueryResponse usersResponse = responseWith(userDocument(USER));
        QueryResponse currentResponse = responseWith(bookmarkDocument(OLD_JSON, 3L, OLD_PAGE));
        when(this.client.query(any(SolrQuery.class))).thenReturn(usersResponse, currentResponse);
        when(this.client.request(any(SolrRequest.class), any())).thenThrow(versionConflict());

        assertThrows(BookmarkStoreConflictException.class, () -> this.store.replacePage(WIKI, OLD_PAGE, NEW_PAGE));

        capturedWrites(3);
        assertEquals("Unable to update the bookmarks of [xwiki:XWiki.User] after a page rename: "
            + "SolrException: version conflict", this.logCapture.getMessage(0));
    }

    @Test
    void updatesTheOtherUsersWhenTheBookmarksOfOneUserCannotBeUpdated() throws Exception
    {
        String otherUser = "xwiki:XWiki.Zoe";
        QueryResponse usersResponse = responseWith(userDocument(USER), userDocument(otherUser));
        QueryResponse conflictingResponse = responseWith(bookmarkDocument(OLD_JSON, 3L, OLD_PAGE));
        QueryResponse currentResponse = responseWith(bookmarkDocument(OLD_JSON, 6L, OLD_PAGE));
        when(this.client.query(any(SolrQuery.class))).thenReturn(usersResponse, conflictingResponse,
            conflictingResponse, conflictingResponse, currentResponse);
        // The first user keeps conflicting until the replacement is given up; the second one is then updated.
        when(this.client.request(any(SolrRequest.class), any())).thenThrow(versionConflict(), versionConflict(),
            versionConflict()).thenReturn(written(7L));

        BookmarkStoreException exception =
            assertThrows(BookmarkStoreConflictException.class, () -> this.store.replacePage(WIKI, OLD_PAGE, NEW_PAGE));

        SolrInputDocument lastSaved = capturedWrites(4).get(3).getDocuments().get(0);
        assertEquals(otherUser, lastSaved.getFieldValue(SolrBookmarkStore.USER_FIELD));
        assertEquals(NEW_JSON, lastSaved.getFieldValue(SolrBookmarkStore.DATA_FIELD));
        assertEquals(0, exception.getSuppressed().length);
        assertEquals("Unable to update the bookmarks of [xwiki:XWiki.User] after a page rename: "
            + "SolrException: version conflict", this.logCapture.getMessage(0));
    }

    @Test
    void reportsTheFirstFailureWithTheNextOnesAttached() throws Exception
    {
        String otherUser = "xwiki:XWiki.Zoe";
        QueryResponse usersResponse = responseWith(userDocument(USER), userDocument(otherUser));
        QueryResponse corruptedResponse = responseWith(bookmarkDocument("{", 3L, OLD_PAGE));
        when(this.client.query(any(SolrQuery.class))).thenReturn(usersResponse, corruptedResponse);

        BookmarkStoreException exception =
            assertThrows(BookmarkStoreException.class, () -> this.store.replacePage(WIKI, OLD_PAGE, NEW_PAGE));

        assertEquals(1, exception.getSuppressed().length);
        verify(this.client, never()).request(any(SolrRequest.class), any());
        assertTrue(this.logCapture.getMessage(0).startsWith(
            "Unable to update the bookmarks of [xwiki:XWiki.User] after a page rename: JsonEOFException: "));
        assertTrue(this.logCapture.getMessage(1).startsWith(
            "Unable to update the bookmarks of [xwiki:XWiki.Zoe] after a page rename: JsonEOFException: "));
    }

    @Test
    void skipsUsersWhoNoLongerHaveTheRenamedPage() throws Exception
    {
        QueryResponse usersResponse = responseWith(userDocument(USER));
        QueryResponse currentResponse = responseWith(bookmarkDocument(NEW_JSON, 3L, NEW_PAGE));
        when(this.client.query(any(SolrQuery.class))).thenReturn(usersResponse, currentResponse);

        assertEquals(0, this.store.replacePage(WIKI, OLD_PAGE, NEW_PAGE));

        verify(this.client, never()).request(any(SolrRequest.class), any());
    }

    @Test
    void ignoresARenameToTheSamePage() throws Exception
    {
        assertEquals(0, this.store.replacePage(WIKI, OLD_PAGE, OLD_PAGE));

        verifyNoInteractions(this.client);
    }

    private static org.apache.solr.common.SolrException versionConflict()
    {
        return new org.apache.solr.common.SolrException(org.apache.solr.common.SolrException.ErrorCode.CONFLICT,
            "version conflict");
    }

    @Test
    void isAvailableWhenAQueryWithoutRowsSucceeds() throws Exception
    {
        QueryResponse response = responseWith();
        when(this.client.query(any(SolrQuery.class))).thenReturn(response);

        assertTrue(this.store.isAvailable());

        ArgumentCaptor<SolrQuery> query = ArgumentCaptor.forClass(SolrQuery.class);
        verify(this.client).query(query.capture());
        assertEquals(0, query.getValue().getRows());
    }

    @Test
    void isNotAvailableWhenTheCoreOrTheServerFails() throws Exception
    {
        XWikiSolrCore coreWithoutClient = mock(XWikiSolrCore.class);
        when(this.solr.getCore(SolrBookmarkStore.CORE_NAME)).thenThrow(new SolrException("missing core"))
            .thenReturn(coreWithoutClient).thenReturn(this.core);
        when(this.client.query(any(SolrQuery.class))).thenThrow(new IOException("down"))
            .thenThrow(new org.apache.solr.common.SolrException(ErrorCode.SERVER_ERROR, "remote failure"));

        for (int attempt = 0; attempt < 4; attempt++) {
            assertFalse(this.store.isAvailable());
            assertEquals("The bookmark storage is not available.", this.logCapture.getMessage(attempt));
        }
    }

    /**
     * @return the response of Solr to a write with {@code versions=true}: the version of the written document
     */
    private static NamedList<Object> written(long version)
    {
        NamedList<Object> adds = new NamedList<>();
        adds.add("4:wiki16:xwiki:XWiki.User", version);
        NamedList<Object> response = new NamedList<>();
        response.add("adds", adds);
        return response;
    }

    @SuppressWarnings("unchecked")
    private List<UpdateRequest> capturedWrites(int count) throws Exception
    {
        ArgumentCaptor<SolrRequest<?>> captor = ArgumentCaptor.forClass(SolrRequest.class);
        verify(this.client, times(count)).request(captor.capture(), any());
        return captor.getAllValues().stream().map(UpdateRequest.class::cast).toList();
    }

    private static QueryResponse responseWith(SolrDocument... sourceDocuments)
    {
        SolrDocumentList documents = new SolrDocumentList();
        documents.addAll(List.of(sourceDocuments));
        QueryResponse response = mock(QueryResponse.class);
        when(response.getResults()).thenReturn(documents);
        return response;
    }

    private static SolrDocument userDocument(String user)
    {
        SolrDocument document = new SolrDocument();
        document.setField(SolrBookmarkStore.USER_FIELD, user);
        return document;
    }

    private static SolrDocument bookmarkDocument(String json, long version, String... pages)
    {
        SolrDocument document = new SolrDocument();
        document.setField(SolrBookmarkStore.DATA_FIELD, json);
        document.setField(VERSION, version);
        for (String page : pages) {
            document.addField(SolrBookmarkStore.PAGES_FIELD, page);
        }
        return document;
    }
}
