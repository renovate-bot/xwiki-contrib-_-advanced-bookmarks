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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkAccessDeniedException;
import org.xwiki.contrib.advancedbookmarks.BookmarkData;
import org.xwiki.contrib.advancedbookmarks.BookmarkEntry;
import org.xwiki.contrib.advancedbookmarks.BookmarkException;
import org.xwiki.contrib.advancedbookmarks.BookmarkFolder;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkManager;
import org.xwiki.contrib.advancedbookmarks.BookmarkRecord;
import org.xwiki.contrib.advancedbookmarks.BookmarkStore;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;

/**
 * Default implementation of {@link BookmarkManager}, storing the bookmarks of each user of the current wiki through
 * the {@link BookmarkStore}.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Singleton
public class DefaultBookmarkManager implements BookmarkManager
{
    @Inject
    private BookmarkStore store;

    @Inject
    private BookmarkJsonCodec jsonCodec;

    @Inject
    private BookmarkNormalizer normalizer;

    @Inject
    private BookmarkRights rights;

    @Inject
    private BookmarkPages pages;

    @Inject
    private BookmarkConfiguration configuration;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> pageResolver;

    /**
     * A step of the depth-first traversal of the folder tree: either visit a folder, or emit its own bookmarks.
     */
    private record Step(String folderId, boolean emitBookmarks)
    {
    }

    @Override
    public boolean canManage(DocumentReference user)
    {
        return this.rights.canManage(user);
    }

    @Override
    public boolean canAdd(DocumentReference user, DocumentReference page)
    {
        return !this.configuration.isExcluded(page) && this.rights.canView(user, page);
    }

    @Override
    public BookmarkData getBookmarks(DocumentReference user) throws BookmarkException
    {
        checkAccess(user);
        return read(user);
    }

    @Override
    public boolean isBookmarked(DocumentReference user, DocumentReference page) throws BookmarkException
    {
        checkAccess(user);
        // Stored references are the serialized references of the normalized bookmarks, like this one.
        return this.store.containsPage(this.rights.getCurrentWiki(), this.normalizer.serialize(user),
            this.normalizer.serialize(page));
    }

    @Override
    public boolean toggle(DocumentReference user, DocumentReference page) throws BookmarkException
    {
        checkAccess(user);
        String wiki = this.rights.getCurrentWiki();
        if (!wiki.equals(page.getWikiReference().getName())) {
            throw new BookmarkInvalidDataException(null);
        }
        String pageReference = this.normalizer.serialize(page);
        // Usually another browser tab: each attempt re-reads the bookmarks and applies the toggle on the latest data.
        return BookmarkRetries.onConflict(() -> {
            BookmarkData data = read(user);
            boolean removed = data.getBookmarks().removeIf(bookmark -> pageReference.equals(bookmark.getPage()));
            if (!removed) {
                data.getBookmarks().add(createBookmark(user, page, pageReference, data));
            }
            write(user, data);
            return !removed;
        });
    }

    @Override
    public long save(DocumentReference user, BookmarkData data) throws BookmarkException
    {
        checkAccess(user);
        this.normalizer.normalize(data, this.rights.getCurrentWiki());
        checkAddedPages(user, data);
        // Storing the bookmarks in display order keeps the stored data readable and the order stable.
        data.setBookmarks(getDisplayOrder(data));
        return write(user, data);
    }

    @Override
    public List<BookmarkEntry> getDisplayOrder(BookmarkData data)
    {
        Map<String, List<BookmarkEntry>> bookmarksByFolder = data.getBookmarks().stream()
            .sorted(Comparator.comparingInt(BookmarkEntry::getOrder))
            .collect(Collectors.groupingBy(BookmarkEntry::getFolder));
        Map<String, List<BookmarkFolder>> childrenByParent = data.getFolders().stream()
            .sorted(Comparator.comparingInt(BookmarkFolder::getOrder))
            .collect(Collectors.groupingBy(BookmarkFolder::getParent));

        List<BookmarkEntry> result = new ArrayList<>(bookmarksByFolder.getOrDefault(BookmarkFolder.ROOT, List.of()));
        // An explicit stack rather than recursion, since the folder tree may be thousands of levels deep.
        Deque<Step> stack = new ArrayDeque<>();
        pushChildren(stack, childrenByParent.getOrDefault(BookmarkFolder.ROOT, List.of()));
        while (!stack.isEmpty()) {
            Step step = stack.pop();
            if (step.emitBookmarks()) {
                result.addAll(bookmarksByFolder.getOrDefault(step.folderId(), List.of()));
            } else {
                stack.push(new Step(step.folderId(), true));
                pushChildren(stack, childrenByParent.getOrDefault(step.folderId(), List.of()));
            }
        }
        return result;
    }

    @Override
    public List<BookmarkEntry> getPanel(BookmarkData data)
    {
        int panelLimit = this.configuration.getPanelLimit();
        List<BookmarkEntry> ordered = getDisplayOrder(data);
        List<BookmarkEntry> selected =
            ordered.stream().filter(BookmarkEntry::isPanel).limit(panelLimit).collect(Collectors.toList());
        return selected.isEmpty() ? ordered.stream().limit(panelLimit).collect(Collectors.toList()) : selected;
    }

    private static void pushChildren(Deque<Step> stack, List<BookmarkFolder> children)
    {
        // Pushed in reverse so that the first child is visited first.
        for (int i = children.size() - 1; i >= 0; i--) {
            stack.push(new Step(children.get(i).getId(), false));
        }
    }

    private void checkAccess(DocumentReference user) throws BookmarkAccessDeniedException
    {
        if (!this.rights.canManage(user)) {
            throw new BookmarkAccessDeniedException();
        }
    }

    /**
     * The data received is compared with the stored bookmarks: otherwise a page that the user cannot view could be
     * added, and its new name learned when it is renamed. Pages already stored are kept as they are.
     */
    private void checkAddedPages(DocumentReference user, BookmarkData data) throws BookmarkException
    {
        Set<String> storedPages =
            read(user).getBookmarks().stream().map(BookmarkEntry::getPage).collect(Collectors.toSet());
        for (BookmarkEntry bookmark : data.getBookmarks()) {
            if (!storedPages.contains(bookmark.getPage())) {
                checkCanAdd(user, this.pageResolver.resolve(bookmark.getPage()));
            }
        }
    }

    private void checkCanAdd(DocumentReference user, DocumentReference page) throws BookmarkAccessDeniedException
    {
        if (!canAdd(user, page)) {
            throw new BookmarkAccessDeniedException();
        }
    }

    private BookmarkEntry createBookmark(DocumentReference user, DocumentReference page, String pageReference,
        BookmarkData data) throws BookmarkException
    {
        checkCanAdd(user, page);
        // No check of the number of bookmarks: the maximum size of the JSON, checked on writing, is always reached
        // first, and reported as such.
        BookmarkEntry bookmark = new BookmarkEntry();
        bookmark.setPage(pageReference);
        bookmark.setLabel(this.pages.exists(page)
            ? BookmarkNormalizer.bound(this.pages.getPlainTitle(page), BookmarkNormalizer.MAX_LABEL_LENGTH) : "");
        bookmark.setFolder(BookmarkFolder.ROOT);
        bookmark.setOrder((int) data.getBookmarks().stream()
            .filter(existing -> BookmarkFolder.ROOT.equals(existing.getFolder())).count());
        return bookmark;
    }

    private BookmarkData read(DocumentReference user) throws BookmarkStoreException
    {
        String wiki = this.rights.getCurrentWiki();
        Optional<BookmarkRecord> stored = this.store.get(wiki, this.normalizer.serialize(user));
        if (stored.isEmpty()) {
            return new BookmarkData();
        }
        BookmarkData data;
        try {
            data = this.jsonCodec.read(stored.get().json());
            this.normalizer.normalize(data, wiki);
        } catch (BookmarkInvalidDataException e) {
            // The data was validated when written: failing now means the stored document is corrupted.
            throw new BookmarkStoreException("The stored bookmark data is corrupted.", e);
        }
        data.setVersion(stored.get().version());
        return data;
    }

    private long write(DocumentReference user, BookmarkData data) throws BookmarkStoreException
    {
        return this.store.save(this.rights.getCurrentWiki(), this.normalizer.serialize(user),
            this.jsonCodec.write(data), data.getVersion());
    }
}
