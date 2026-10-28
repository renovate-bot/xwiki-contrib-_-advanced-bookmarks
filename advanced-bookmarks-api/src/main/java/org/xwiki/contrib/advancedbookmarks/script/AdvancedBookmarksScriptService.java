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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkData;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkManager;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkConfiguration;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkJsonCodec;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkReferences;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkRequests;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkResults;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkRights;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkTransfer;
import org.xwiki.contrib.advancedbookmarks.internal.BookmarkViews;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.script.service.ScriptService;
import org.xwiki.script.service.ScriptServiceManager;

/**
 * Exposes bookmarks to scripts as maps ready to be serialized as JSON. Every result has a {@code status} key holding
 * an HTTP status code and, on failure, an {@code error} key holding a code without any detail: {@code forbidden},
 * {@code invalidToken}, {@code methodNotAllowed}, {@code invalid}, {@code tooLarge}, {@code conflict} or
 * {@code storageError}.
 * Details are logged on the server.
 * <p>
 * Write operations only accept POST requests carrying a valid CSRF token in the {@code form_token} parameter.
 * <p>
 * The administration tools are exposed by the {@code $services.advancedbookmarks.admin} sub-service.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named(AdvancedBookmarksScriptService.ROLE_HINT)
@Singleton
public class AdvancedBookmarksScriptService implements ScriptService
{
    /**
     * The hint of this script service, which prefixes the hints of its sub-services.
     */
    public static final String ROLE_HINT = "advancedbookmarks";

    private static final String VERSION = "version";

    private static final String BOOKMARKS = "bookmarks";

    private static final String SIZE = "size";

    @Inject
    private BookmarkManager bookmarkManager;

    @Inject
    private BookmarkRights rights;

    @Inject
    private BookmarkJsonCodec jsonCodec;

    @Inject
    private BookmarkReferences references;

    @Inject
    private BookmarkTransfer transfer;

    @Inject
    private BookmarkConfiguration configuration;

    @Inject
    private BookmarkViews views;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private BookmarkResults results;

    @Inject
    private ScriptServiceManager scriptServiceManager;

    /**
     * Give access to the sub-services, such as {@code $services.advancedbookmarks.admin}.
     *
     * @param <S> the type of the sub-service
     * @param serviceName the name of the sub-service
     * @return the sub-service, or {@code null} if there is none with this name
     */
    @SuppressWarnings("unchecked")
    public <S extends ScriptService> S get(String serviceName)
    {
        return (S) this.scriptServiceManager.get(ROLE_HINT + '.' + serviceName);
    }

    /**
     * Read all the bookmarks of a user, in display order.
     *
     * @param user the owner of the bookmarks, such as {@code XWiki.jdoe} or {@code jdoe}; empty for the current user
     * @return {@code user}, {@code version}, {@code folders} and {@code bookmarks}; each bookmark has the {@code page},
     *         {@code label}, {@code folder}, {@code order}, {@code panel} and {@code viewable} keys and, when the
     *         current user may view the page, {@code exists} and {@code url}; the space used by the bookmarks and the
     *         space allowed, in bytes, under the {@code size} and {@code maxSize} keys
     */
    public Map<String, Object> getBookmarks(String user)
    {
        return this.results.execute(() -> {
            DocumentReference owner = this.references.resolveUser(user);
            BookmarkData data = this.bookmarkManager.getBookmarks(owner);
            Map<String, Object> result = BookmarkResults.success();
            result.put("user", this.serializer.serialize(owner));
            result.put(VERSION, String.valueOf(data.getVersion()));
            result.put(SIZE, this.jsonCodec.getSize(data));
            result.put("maxSize", BookmarkJsonCodec.MAX_JSON_BYTES);
            result.put("folders", this.views.getFolders(data.getFolders()));
            result.put(BOOKMARKS, this.views.getBookmarks(this.bookmarkManager.getDisplayOrder(data)));
            return result;
        });
    }

    /**
     * @return the panel bookmarks of the current user, under the {@code bookmarks} key
     */
    public Map<String, Object> getPanel()
    {
        return this.results.execute(() -> {
            BookmarkData data = this.bookmarkManager.getBookmarks(this.rights.getCurrentUser());
            Map<String, Object> result = BookmarkResults.success();
            result.put(BOOKMARKS, this.views.getBookmarks(this.bookmarkManager.getPanel(data)));
            return result;
        });
    }

    /**
     * @param page the serialized reference of a page of the current wiki
     * @return whether the page is bookmarked by the current user, under the {@code bookmarked} key, and whether it may
     *         be added to the bookmarks, under the {@code bookmarkable} key ({@code false} for an excluded page)
     */
    public Map<String, Object> isBookmarked(String page)
    {
        return this.results.execute(() -> {
            DocumentReference pageReference = this.references.resolvePage(page);
            Map<String, Object> result =
                bookmarked(this.bookmarkManager.isBookmarked(this.rights.getCurrentUser(), pageReference));
            result.put("bookmarkable", !this.configuration.isExcluded(pageReference));
            return result;
        });
    }

    /**
     * @return {@code true} if the current wiki displays the star in the action bar of the pages
     */
    public boolean isStarEnabled()
    {
        return this.configuration.isStarEnabled();
    }

    /**
     * Add a page to the bookmarks of the current user, or remove it if it is already bookmarked.
     *
     * @param page the serialized reference of a page of the current wiki
     * @return whether the page is bookmarked after the operation, under the {@code bookmarked} key
     */
    public Map<String, Object> toggle(String page)
    {
        return this.results.executeWrite(() -> bookmarked(
            this.bookmarkManager.toggle(this.rights.getCurrentUser(), this.references.resolvePage(page))));
    }

    /**
     * Replace all the bookmarks of a user, provided they have not been modified since they were read.
     *
     * @param user the owner of the bookmarks; empty for the current user
     * @param json the folders and bookmarks, as a JSON object with the {@code folders} and {@code bookmarks} arrays
     * @param version the version the bookmarks were read at, as returned by {@link #getBookmarks(String)}; {@code 0}
     *            if there were none
     * @return the new version, under the {@code version} key, and the space used by the saved bookmarks, in bytes,
     *         under the {@code size} key
     */
    public Map<String, Object> save(String user, String json, String version)
    {
        return this.results.executeWrite(() -> {
            DocumentReference owner = this.references.resolveUser(user);
            BookmarkData data = this.jsonCodec.read(json);
            data.setVersion(parseVersion(version));
            Map<String, Object> result = BookmarkResults.success();
            result.put(VERSION, String.valueOf(this.bookmarkManager.save(owner, data)));
            // Saving normalizes the data in place: its size is the size of the stored bookmarks.
            result.put(SIZE, this.jsonCodec.getSize(data));
            return result;
        });
    }

    /**
     * Export the bookmarks of a user. Page references are exported without their wiki, so that the file can be
     * imported into another wiki.
     *
     * @param user the owner of the bookmarks; empty for the current user
     * @return the content of the export file, as a JSON string, under the {@code content} key
     */
    public Map<String, Object> exportBookmarks(String user)
    {
        return this.results.execute(() -> {
            Map<String, Object> result = BookmarkResults.success();
            result.put("content", this.transfer.export(this.references.resolveUser(user)));
            return result;
        });
    }

    /**
     * Import an export file into the bookmarks of a user, provided they have not been modified since they were read.
     *
     * @param user the owner of the bookmarks; empty for the current user
     * @param json the content of the export file
     * @param mode {@code merge} to add the folders and the bookmarks of new pages to the existing ones, or
     *            {@code replace} to replace the existing bookmarks
     * @param version the version the bookmarks were read at, as returned by {@link #getBookmarks(String)}; {@code 0}
     *            if there were none
     * @return the new version under the {@code version} key, and the number of bookmarks of the file that were
     *         imported and skipped under the {@code imported} and {@code skipped} keys
     */
    public Map<String, Object> importBookmarks(String user, String json, String mode, String version)
    {
        return this.results.executeWrite(() -> {
            DocumentReference owner = this.references.resolveUser(user);
            BookmarkTransfer.ImportResult imported = this.transfer.importBookmarks(owner, json,
                BookmarkRequests.isReplaceMode(mode), parseVersion(version));
            Map<String, Object> result = BookmarkResults.success();
            result.put(VERSION, String.valueOf(imported.version()));
            result.put("imported", imported.imported());
            result.put("skipped", imported.skipped());
            return result;
        });
    }

    /**
     * Versions are exchanged as decimal strings: the versions of Solr exceed the integer precision of JavaScript
     * numbers (2^53), and a rounded version would always be reported as a conflict.
     */
    private static long parseVersion(String version) throws BookmarkInvalidDataException
    {
        try {
            long result = Long.parseLong(version);
            if (result >= 0) {
                return result;
            }
        } catch (NumberFormatException e) {
            // Also thrown for a null version. Reported below as invalid data, without detail.
        }
        throw new BookmarkInvalidDataException(null);
    }

    private static Map<String, Object> bookmarked(boolean bookmarked)
    {
        Map<String, Object> result = BookmarkResults.success();
        result.put("bookmarked", bookmarked);
        return result;
    }
}
