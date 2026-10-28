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
package org.xwiki.contrib.advancedbookmarks.script;

import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkAccessDeniedException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStore;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkRequests;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkResults;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkRights;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkWikiImportStatus;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkWikiImports;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkWikiTransfer;
import org.xwiki.script.service.ScriptService;

/**
 * Administration tools of the current wiki, reserved to its administrators: availability of the storage, export of
 * the bookmarks of all the users, and import of such an export in the background. Accessed as
 * {@code $services.advancedbookmarks.admin}. Results are maps ready to be serialized as JSON, as described by
 * {@link BookmarkResults}.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named(AdvancedBookmarksScriptService.ROLE_HINT + ".admin")
@Singleton
public class AdvancedBookmarksAdminScriptService implements ScriptService
{
    /**
     * The name of the file part of an import request.
     */
    public static final String FILE_PART = "file";

    private static final String STATE = "state";

    private static final int PERCENT = 100;

    @Inject
    private BookmarkResults results;

    @Inject
    private BookmarkRights rights;

    @Inject
    private BookmarkRequests requests;

    @Inject
    private BookmarkStore store;

    @Inject
    private BookmarkWikiTransfer wikiTransfer;

    @Inject
    private BookmarkWikiImports imports;

    /**
     * @return whether the bookmark storage can be queried, under the {@code available} key; the cause of an
     *         unavailability is logged
     */
    public Map<String, Object> getStoreStatus()
    {
        return executeAdmin(() -> {
            Map<String, Object> result = BookmarkResults.success();
            result.put("available", this.store.isAvailable());
            return result;
        });
    }

    /**
     * Export the bookmarks of all the users of the current wiki.
     *
     * @return the content of the export file under the {@code content} key, and the number of users exported and
     *         skipped (bookmarks that could not be read, the reason being logged) under the {@code exported} and
     *         {@code skipped} keys
     */
    public Map<String, Object> exportWiki()
    {
        return executeAdmin(() -> {
            BookmarkWikiTransfer.WikiExport export = this.wikiTransfer.exportWiki();
            Map<String, Object> result = BookmarkResults.success();
            result.put("content", export.content());
            result.put("exported", export.exported());
            result.put("skipped", export.skipped());
            return result;
        });
    }

    /**
     * Validate the wiki export file sent in the {@code file} part of the current {@code multipart/form-data} request,
     * then import it in the background. Only one import of a wiki runs at a time ({@code conflict} otherwise).
     *
     * @param mode {@code merge} to add the folders and the bookmarks of new pages to the existing ones of each user,
     *            or {@code replace} to replace the bookmarks of each user of the file
     * @return the number of users in the file, under the {@code users} key
     */
    public Map<String, Object> startWikiImport(String mode)
    {
        return this.results.executeWrite(() -> {
            checkAdmin();
            boolean replace = BookmarkRequests.isReplaceMode(mode);
            byte[] content = this.requests.readFile(FILE_PART, BookmarkWikiTransfer.MAX_FILE_BYTES);
            Map<String, Object> result = BookmarkResults.success();
            result.put("users", this.imports.start(content, replace));
            return result;
        });
    }

    /**
     * The page gives this size to the browser, which can then refuse a larger file before sending it; the server
     * checks it again in {@link #startWikiImport(String)}. Not a secret, hence available without any right.
     *
     * @return the maximum size, in bytes, of a file accepted by {@link #startWikiImport(String)}
     */
    public int getMaxImportFileSize()
    {
        return BookmarkWikiTransfer.MAX_FILE_BYTES;
    }

    /**
     * @return the state of the running or last import of the current wiki under the {@code state} key:
     *         {@code none}, {@code running} or {@code finished}; for the last two, the progress as a percentage under
     *         the {@code progress} key, the counts under the {@code importedUsers}, {@code skippedUsers},
     *         {@code failedUsers}, {@code importedBookmarks} and {@code skippedBookmarks} keys, and whether the
     *         import stopped on an unexpected error under the {@code failed} key
     */
    public Map<String, Object> getWikiImportStatus()
    {
        return executeAdmin(() -> {
            Map<String, Object> result = BookmarkResults.success();
            BookmarkWikiImportStatus status = this.imports.getStatus();
            if (status == null) {
                result.put(STATE, "none");
                return result;
            }
            result.put(STATE, BookmarkWikiImports.isRunning(status) ? "running" : "finished");
            result.put("progress", (int) Math.floor(status.getProgress().getOffset() * PERCENT));
            BookmarkWikiImportStatus.Counts counts = status.getCounts();
            result.put("importedUsers", counts.importedUsers());
            result.put("skippedUsers", counts.skippedUsers());
            result.put("failedUsers", counts.failedUsers());
            result.put("importedBookmarks", counts.importedBookmarks());
            result.put("skippedBookmarks", counts.skippedBookmarks());
            result.put("failed", status.getError() != null);
            return result;
        });
    }

    private Map<String, Object> executeAdmin(BookmarkResults.Operation operation)
    {
        return this.results.execute(() -> {
            checkAdmin();
            return operation.run();
        });
    }

    private void checkAdmin() throws BookmarkAccessDeniedException
    {
        if (!this.rights.isAdmin()) {
            throw new BookmarkAccessDeniedException();
        }
    }
}
