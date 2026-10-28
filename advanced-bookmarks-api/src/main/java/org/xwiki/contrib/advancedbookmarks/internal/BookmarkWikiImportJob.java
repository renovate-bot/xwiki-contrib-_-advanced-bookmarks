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

import javax.inject.Inject;
import javax.inject.Named;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.InstantiationStrategy;
import org.xwiki.component.descriptor.ComponentInstantiationStrategy;
import org.xwiki.contrib.advancedbookmarks.BookmarkAccessDeniedException;
import org.xwiki.contrib.advancedbookmarks.BookmarkException;
import org.xwiki.job.AbstractJob;
import org.xwiki.model.reference.DocumentReference;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Imports a validated wiki export file, user by user, with the rights of the administrator who started it. Each user
 * is imported like the export file of one user, by {@link BookmarkTransfer#importData}: a failure for one user is
 * counted and logged, and the import goes on with the next one.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named(BookmarkWikiImportJob.JOB_TYPE)
@InstantiationStrategy(ComponentInstantiationStrategy.PER_LOOKUP)
public class BookmarkWikiImportJob extends AbstractJob<BookmarkWikiImportRequest, BookmarkWikiImportStatus>
{
    /**
     * The type of this job.
     */
    public static final String JOB_TYPE = "advancedbookmarks.wikiImport";

    @Inject
    private BookmarkWikiTransfer wikiTransfer;

    @Inject
    private BookmarkTransfer transfer;

    @Inject
    private BookmarkJsonCodec jsonCodec;

    @Override
    public String getType()
    {
        return JOB_TYPE;
    }

    @Override
    protected BookmarkWikiImportStatus createNewStatus(BookmarkWikiImportRequest request)
    {
        return new BookmarkWikiImportStatus(JOB_TYPE, request, this.observationManager, this.loggerManager);
    }

    @Override
    protected void runInternal() throws Exception
    {
        BookmarkWikiImportRequest request = getRequest();
        this.progressManager.pushLevelProgress(request.getUserCount(), this);
        try {
            this.wikiTransfer.readUsers(request.getContent(), (user, bookmarks) -> {
                this.progressManager.startStep(this);
                importUser(user, bookmarks, request.isReplace());
                this.progressManager.endStep(this);
            });
        } finally {
            this.progressManager.popLevelProgress(this);
            request.clearContent();
        }
    }

    private void importUser(DocumentReference user, JsonNode bookmarks, boolean replace)
    {
        try {
            // The user may modify the bookmarks meanwhile: each attempt imports into the latest ones. The file data is
            // converted again at each attempt, since an import modifies the data it receives.
            this.status.addImportedUser(BookmarkRetries.onConflict(() -> this.transfer.importData(user,
                this.jsonCodec.read(bookmarks), replace, BookmarkTransfer.ANY_VERSION)));
        } catch (BookmarkAccessDeniedException e) {
            // The administrator manages the bookmarks of every existing user: this one is not a user here.
            this.logger.info("Skipped the bookmarks of [{}]: not a user of this wiki.", user);
            this.status.addSkippedUser();
        } catch (BookmarkException e) {
            fail(user, e);
        }
    }

    private void fail(DocumentReference user, BookmarkException exception)
    {
        this.logger.warn("Unable to import the bookmarks of [{}]: {}", user, exception.getMessage());
        this.logger.debug("Full error:", exception);
        this.status.addFailedUser();
    }
}
