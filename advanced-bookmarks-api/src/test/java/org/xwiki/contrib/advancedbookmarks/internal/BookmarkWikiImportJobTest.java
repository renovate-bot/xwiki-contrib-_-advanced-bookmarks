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

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.context.Execution;
import org.xwiki.context.ExecutionContext;
import org.xwiki.contrib.advancedbookmarks.BookmarkAccessDeniedException;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.job.DefaultRequest;
import org.xwiki.job.event.status.JobProgressManager;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.ComponentList;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the background import of a wiki export file.
 *
 * @version $Id$
 */
@ComponentTest
@ComponentList(BookmarkJsonCodec.class)
class BookmarkWikiImportJobTest
{
    private static final DocumentReference ALICE = ReferenceStubs.page("wiki:XWiki.alice");

    private static final DocumentReference BOB = ReferenceStubs.page("wiki:XWiki.bob");

    private static final DocumentReference CAROL = ReferenceStubs.page("wiki:XWiki.carol");

    private static final DocumentReference DAVE = ReferenceStubs.page("wiki:XWiki.dave");

    private static final DocumentReference EVE = ReferenceStubs.page("wiki:XWiki.eve");

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.INFO);

    @InjectMockComponents
    private BookmarkWikiImportJob job;

    @MockComponent
    private BookmarkWikiTransfer wikiTransfer;

    @MockComponent
    private BookmarkTransfer transfer;

    @MockComponent
    private JobProgressManager progressManager;

    @MockComponent
    private Execution execution;

    private BookmarkWikiImportRequest request;

    private JsonNode bookmarks;

    @BeforeEach
    void setUp() throws Exception
    {
        // An existing execution context is reused by the job.
        when(this.execution.getContext()).thenReturn(new ExecutionContext());
        this.bookmarks = new ObjectMapper().readTree("{\"folders\":[],\"bookmarks\":[]}");
        this.request = new BookmarkWikiImportRequest();
        this.request.setId(List.of("advancedbookmarks", "import", ReferenceStubs.WIKI));
        this.request.setContent(new byte[] { 1 });
        this.request.setReplace(true);
        this.request.setUserCount(5);
        this.request.setVerbose(false);
        this.request.setStatusSerialized(false);
    }

    @Test
    void importsEachUserAndCountsTheOutcome() throws Exception
    {
        when(this.wikiTransfer.readUsers(any(), any())).thenAnswer(invocation -> {
            BookmarkWikiTransfer.UserVisitor visitor = invocation.getArgument(1);
            for (DocumentReference user : List.of(ALICE, BOB, CAROL, DAVE, EVE)) {
                visitor.visit(user, this.bookmarks);
            }
            return 5;
        });
        when(this.transfer.importData(eq(ALICE), any(), eq(true), eq(BookmarkTransfer.ANY_VERSION)))
            .thenReturn(new BookmarkTransfer.ImportResult(1L, 3, 1));
        when(this.transfer.importData(eq(BOB), any(), eq(true), anyLong()))
            .thenThrow(new BookmarkAccessDeniedException());
        // Carol modifies her bookmarks during the import: the second attempt succeeds.
        when(this.transfer.importData(eq(CAROL), any(), eq(true), anyLong()))
            .thenThrow(new BookmarkStoreConflictException(null))
            .thenReturn(new BookmarkTransfer.ImportResult(2L, 2, 0));
        when(this.transfer.importData(eq(DAVE), any(), eq(true), anyLong()))
            .thenThrow(new BookmarkStoreConflictException(null));
        when(this.transfer.importData(eq(EVE), any(), eq(true), anyLong()))
            .thenThrow(new BookmarkStoreException("storage down", null));

        this.job.initialize(this.request);
        this.job.run();

        BookmarkWikiImportStatus status = this.job.getStatus();
        assertEquals(JobStatus.State.FINISHED, status.getState());
        assertNull(status.getError());
        assertEquals(new BookmarkWikiImportStatus.Counts(2, 1, 2, 5, 1), status.getCounts());
        assertNull(this.job.getRequest().getContent());
        assertEquals(BookmarkWikiImportJob.JOB_TYPE, this.job.getType());
        verify(this.transfer, times(3)).importData(eq(DAVE), any(), eq(true), anyLong());
        verify(this.progressManager).pushLevelProgress(5, this.job);
        verify(this.progressManager, times(5)).startStep(this.job);
        verify(this.progressManager, times(5)).endStep(this.job);
        verify(this.progressManager).popLevelProgress(this.job);

        assertEquals("Skipped the bookmarks of [wiki:XWiki.bob]: not a user of this wiki.",
            this.logCapture.getMessage(0));
        assertEquals("Unable to import the bookmarks of [wiki:XWiki.dave]: "
            + "BookmarkStoreConflictException: The bookmark document was modified concurrently.", this.logCapture.getMessage(1));
        assertEquals("Unable to import the bookmarks of [wiki:XWiki.eve]: BookmarkStoreException: storage down",
            this.logCapture.getMessage(2));
    }

    @Test
    void recordsAnUnexpectedFailureAndReleasesTheFile() throws Exception
    {
        BookmarkInvalidDataException failure = new BookmarkInvalidDataException(null);
        when(this.wikiTransfer.readUsers(any(), any())).thenThrow(failure);

        this.job.initialize(this.request);
        this.job.run();

        BookmarkWikiImportStatus status = this.job.getStatus();
        assertEquals(JobStatus.State.FINISHED, status.getState());
        assertInstanceOf(BookmarkInvalidDataException.class, status.getError());
        assertNull(this.request.getContent());
        assertEquals(new BookmarkWikiImportStatus.Counts(0, 0, 0, 0, 0), status.getCounts());
        assertEquals("Exception thrown during job execution", this.logCapture.getMessage(0));
        verify(this.progressManager).popLevelProgress(this.job);
    }

    @Test
    void readsTheDefaultValuesOfAnEmptyRequestAndCopiesARequest()
    {
        // The job framework converts a generic request into the request of the job.
        assertEquals(5, new BookmarkWikiImportRequest(new DefaultRequest(this.request)).getUserCount());

        BookmarkWikiImportRequest empty = new BookmarkWikiImportRequest();

        assertNull(empty.getContent());
        assertEquals(0, empty.getUserCount());
        assertFalse(empty.isReplace());
    }
}
