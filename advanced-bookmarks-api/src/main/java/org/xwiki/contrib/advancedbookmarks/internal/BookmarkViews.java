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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkEntry;
import org.xwiki.contrib.advancedbookmarks.BookmarkFolder;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;

/**
 * Converts folders and bookmarks to maps ready to be serialized as JSON for the browser.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = BookmarkViews.class)
@Singleton
public class BookmarkViews
{
    private static final String ORDER = "order";

    @Inject
    private BookmarkRights rights;

    @Inject
    private BookmarkPages pages;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    /**
     * @param folders folders
     * @return a map per folder, with the {@code id}, {@code name}, {@code parent} and {@code order} keys
     */
    public List<Map<String, Object>> getFolders(List<BookmarkFolder> folders)
    {
        return folders.stream().map(BookmarkViews::toMap).toList();
    }

    /**
     * The page and label are the owner's own data and are always returned, so that saving what was read loses
     * nothing. Live information about the page is only returned if the current user may view it; the page reference
     * must then not be displayed either.
     *
     * @param bookmarks bookmarks
     * @return a map per bookmark, with the {@code page}, {@code label}, {@code folder}, {@code order}, {@code panel}
     *         and {@code viewable} keys and, when the current user may view the page, {@code exists} and {@code url};
     *         for a bookmark without label of a page the current user may view, the text to display instead under the
     *         {@code title} key (see {@link BookmarkPages#getDisplayTitle})
     */
    public List<Map<String, Object>> getBookmarks(List<BookmarkEntry> bookmarks)
    {
        return bookmarks.stream().map(this::toMap).toList();
    }

    private Map<String, Object> toMap(BookmarkEntry bookmark)
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("page", bookmark.getPage());
        map.put("label", bookmark.getLabel());
        map.put("folder", bookmark.getFolder());
        map.put(ORDER, bookmark.getOrder());
        map.put("panel", bookmark.isPanel());
        DocumentReference page = this.resolver.resolve(bookmark.getPage());
        boolean viewable = this.rights.canView(page);
        map.put("viewable", viewable);
        if (viewable) {
            boolean exists = this.pages.exists(page);
            map.put("exists", exists);
            map.put("url", this.pages.getURL(page));
            // Only computed when needed: the title of a page may be costly to render.
            if (StringUtils.isBlank(bookmark.getLabel())) {
                map.put("title", this.pages.getDisplayTitle(page, exists));
            }
        }
        return map;
    }

    private static Map<String, Object> toMap(BookmarkFolder folder)
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", folder.getId());
        map.put("name", folder.getName());
        map.put("parent", folder.getParent());
        map.put(ORDER, folder.getOrder());
        return map;
    }
}
