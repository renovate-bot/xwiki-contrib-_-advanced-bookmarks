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

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.context.concurrent.ContextStoreManager;
import org.xwiki.contrib.advancedbookmarks.BookmarkException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.job.Job;
import org.xwiki.job.JobException;
import org.xwiki.job.JobExecutor;
import org.xwiki.job.JobStatusStore;
import org.xwiki.job.event.status.JobStatus;

/**
 * Starts the import of a wiki export file as a {@link BookmarkWikiImportJob}, at most one at a time for each wiki, and
 * gives the status of the last import of the current wiki.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = BookmarkWikiImports.class)
@Singleton
public class BookmarkWikiImports
{
    /**
     * The context entries given to the job, so that it runs in the current wiki with the rights of the current user
     * and of the author of the code that started it: administrators' rights require both (see
     * {@link BookmarkRights#isAdmin()}), and the job thread would otherwise have an unrelated author. Their names are
     * those of the XWiki context store.
     */
    private static final List<String> CONTEXT_ENTRIES = List.of("wiki", "user", "author", "locale");

    @Inject
    private BookmarkWikiTransfer wikiTransfer;

    @Inject
    private BookmarkRights rights;

    @Inject
    private JobExecutor jobExecutor;

    @Inject
    private JobStatusStore jobStatusStore;

    @Inject
    private ContextStoreManager contextStore;

    /**
     * Validate a wiki export file, then import it in the background. The caller checks that the current user
     * administers the wiki; the job checks the rights again for each user.
     *
     * @param content the content of the wiki export file
     * @param replace {@code true} to replace the bookmarks of each user, {@code false} to merge the file with them
     * @return the number of users in the file
     * @throws org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException if the file is invalid
     * @throws BookmarkStoreConflictException if an import of the current wiki is already running
     * @throws BookmarkException if the import cannot be started
     */
    public int start(byte[] content, boolean replace) throws BookmarkException
    {
        // Validated before starting, so that an invalid file is reported at once and nothing is imported from it.
        // Reading a large file takes time: it is done outside of the lock, which would delay the other imports.
        int userCount = this.wikiTransfer.validate(content);
        BookmarkWikiImportRequest request = new BookmarkWikiImportRequest();
        request.setId(getJobId());
        request.setContent(content);
        request.setReplace(replace);
        request.setUserCount(userCount);
        // The status holds no information worth keeping after a restart.
        request.setStatusSerialized(false);
        // The log of the job also goes to the server log, where administrators look for failures.
        request.setStatusLogIsolated(false);
        execute(request);
        return userCount;
    }

    /**
     * Synchronized so that two imports of the same wiki cannot both find that none is running.
     */
    private synchronized void execute(BookmarkWikiImportRequest request) throws BookmarkException
    {
        if (isRunning(getStatus())) {
            throw new BookmarkStoreConflictException(null);
        }
        try {
            request.setContext(this.contextStore.save(CONTEXT_ENTRIES));
            this.jobExecutor.execute(BookmarkWikiImportJob.JOB_TYPE, request);
        } catch (ComponentLookupException | JobException e) {
            throw new BookmarkException("Unable to start the import of bookmarks.", e);
        }
    }

    /**
     * @return the status of the running or last import of the current wiki, or {@code null} if there was none since
     *         the server started
     */
    public BookmarkWikiImportStatus getStatus()
    {
        List<String> jobId = getJobId();
        Job job = this.jobExecutor.getJob(jobId);
        // A finished job leaves the executor; its status stays in the memory cache of the status store.
        JobStatus status = job != null ? job.getStatus() : this.jobStatusStore.getJobStatus(jobId);
        return status instanceof BookmarkWikiImportStatus importStatus ? importStatus : null;
    }

    /**
     * @param status the status of an import, possibly {@code null}
     * @return {@code true} if the import has not finished
     */
    public static boolean isRunning(BookmarkWikiImportStatus status)
    {
        return status != null && status.getState() != JobStatus.State.FINISHED;
    }

    private List<String> getJobId()
    {
        return List.of("advancedbookmarks", "import", this.rights.getCurrentWiki());
    }
}
