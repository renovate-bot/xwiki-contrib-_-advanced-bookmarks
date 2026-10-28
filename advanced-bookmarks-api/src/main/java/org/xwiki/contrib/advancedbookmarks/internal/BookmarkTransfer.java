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

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;
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
import org.xwiki.contrib.advancedbookmarks.BookmarkManager;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;

/**
 * Exports the bookmarks of a user to a JSON file, and imports such a file by merging it with the existing bookmarks
 * or by replacing them. Rights, normalization, limits and version conflicts are those of {@link BookmarkManager}.
 * <p>
 * Page references are exported without their wiki and resolved in the current wiki on import, so that bookmarks can
 * be moved to another wiki or farm.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = BookmarkTransfer.class)
@Singleton
public class BookmarkTransfer
{
    /**
     * Value of the {@code format} property, which identifies an export file.
     */
    public static final String FORMAT = "advanced-bookmarks";

    /**
     * Version of the export format, in the {@code formatVersion} property.
     */
    public static final int FORMAT_VERSION = 1;

    /**
     * Version given to {@link #importData} to import into the current bookmarks, whatever their version.
     */
    public static final long ANY_VERSION = -1;

    /**
     * Property of an export file holding the kind of file.
     */
    public static final String FORMAT_KEY = "format";

    /**
     * Property of an export file holding the version of its format.
     */
    public static final String FORMAT_VERSION_KEY = "formatVersion";

    @Inject
    private BookmarkManager bookmarkManager;

    @Inject
    private BookmarkJsonCodec jsonCodec;

    @Inject
    private BookmarkNormalizer normalizer;

    @Inject
    private BookmarkRights rights;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @Inject
    @Named("local")
    private EntityReferenceSerializer<String> localSerializer;

    /**
     * The outcome of an import.
     *
     * @param version the new storage version of the bookmarks
     * @param imported the number of bookmarks added by the import
     * @param skipped the number of bookmarks of the file that were not added: already bookmarked, invalid, of
     *            another wiki, of an excluded page or of a page the owner cannot view
     */
    public record ImportResult(long version, int imported, int skipped)
    {
    }

    /**
     * @param user the owner of the bookmarks
     * @return the export file of the bookmarks of the user, as compact JSON
     * @throws BookmarkException if the current user may not manage these bookmarks, or they cannot be read
     */
    public String export(DocumentReference user) throws BookmarkException
    {
        return this.jsonCodec.write(exportData(user), createHeader(FORMAT));
    }

    /**
     * @param user the owner of the bookmarks
     * @return the bookmarks of the user as they are exported: page references without their wiki
     * @throws BookmarkException if the current user may not manage these bookmarks, or they cannot be read
     */
    public BookmarkData exportData(DocumentReference user) throws BookmarkException
    {
        // The data is read for this export only, so it can be modified in place.
        BookmarkData data = this.bookmarkManager.getBookmarks(user);
        for (BookmarkEntry bookmark : data.getBookmarks()) {
            bookmark.setPage(this.localSerializer.serialize(this.resolver.resolve(bookmark.getPage())));
        }
        return data;
    }

    /**
     * @param format the value of the {@code format} property, which identifies the kind of export file
     * @return the properties written at the beginning of an export file
     */
    public static Map<String, Object> createHeader(String format)
    {
        Map<String, Object> header = new LinkedHashMap<>();
        header.put(FORMAT_KEY, format);
        header.put(FORMAT_VERSION_KEY, FORMAT_VERSION);
        header.put("exportedAt", Instant.now().toString());
        return header;
    }

    /**
     * Import an export file into the bookmarks of a user, provided they have not been modified since they were read.
     *
     * @param user the owner of the bookmarks
     * @param json the content of the export file
     * @param replace {@code true} to replace the existing bookmarks, {@code false} to merge the file with them
     * @param version the version the bookmarks were read at, {@code 0} if there were none
     * @return the new version and the number of bookmarks imported and skipped
     * @throws BookmarkException if the current user may not manage these bookmarks, the file is invalid, the result
     *             exceeds the limits, the bookmarks were modified since they were read, or they cannot be stored
     */
    public ImportResult importBookmarks(DocumentReference user, String json, boolean replace, long version)
        throws BookmarkException
    {
        // Checked before the file is read, so that a user without rights cannot have it parsed.
        if (!this.bookmarkManager.canManage(user)) {
            throw new BookmarkAccessDeniedException();
        }
        return importData(user, this.jsonCodec.read(json, Map.of(FORMAT_KEY, FORMAT, FORMAT_VERSION_KEY,
            FORMAT_VERSION)), replace, version);
    }

    /**
     * Import exported bookmarks into the bookmarks of a user, provided they have not been modified since they were
     * read.
     *
     * @param user the owner of the bookmarks
     * @param imported the imported folders and bookmarks, page references being resolved in the current wiki; they
     *            are modified by the import
     * @param replace {@code true} to replace the existing bookmarks, {@code false} to merge the file with them
     * @param version the version the bookmarks were read at, {@code 0} if there were none, or {@link #ANY_VERSION}
     *            to import into the current bookmarks whatever their version
     * @return the new version and the number of bookmarks imported and skipped
     * @throws BookmarkException if the current user may not manage these bookmarks, the result exceeds the limits,
     *             the bookmarks were modified since they were read, or they cannot be stored
     */
    public ImportResult importData(DocumentReference user, BookmarkData imported, boolean replace, long version)
        throws BookmarkException
    {
        BookmarkData current = this.bookmarkManager.getBookmarks(user);
        if (version != ANY_VERSION && current.getVersion() != version) {
            // Checked now rather than only by the store, since a merge must start from what the user saw.
            throw new BookmarkStoreConflictException(null);
        }
        int fileCount = imported.getBookmarks().size();
        // Normalized first, so that pages are compared under their canonical reference in the current wiki, and so
        // that the imported folders form a valid tree.
        this.normalizer.normalize(imported, this.rights.getCurrentWiki());
        // Like the star, an import may only add pages that may be added: excluded pages and pages the owner cannot
        // view are counted as skipped. Pages already bookmarked are kept, as when the bookmarks are saved.
        Set<String> currentPages =
            current.getBookmarks().stream().map(BookmarkEntry::getPage).collect(Collectors.toSet());
        imported.getBookmarks().removeIf(bookmark -> !currentPages.contains(bookmark.getPage())
            && !this.bookmarkManager.canAdd(user, this.resolver.resolve(bookmark.getPage())));
        int keptCount = 0;
        BookmarkData result = imported;
        if (!replace) {
            keptCount = current.getBookmarks().size();
            merge(current, imported);
            result = current;
        }
        // The version that was read: the store still detects a modification made since then.
        result.setVersion(current.getVersion());
        long newVersion = this.bookmarkManager.save(user, result);
        // The save normalizes the result in place, so its size accounts for the bookmarks that were dropped.
        int importedCount = result.getBookmarks().size() - keptCount;
        return new ImportResult(newVersion, importedCount, fileCount - importedCount);
    }

    /**
     * Add the imported folders and new bookmarks to the existing ones. The existing bookmarks come first, so that the
     * normalization keeps their panel selection first.
     */
    private static void merge(BookmarkData current, BookmarkData imported)
    {
        Map<String, String> newIds = mergeFolders(current, imported);

        Set<String> pages = current.getBookmarks().stream().map(BookmarkEntry::getPage).collect(Collectors.toSet());
        List<BookmarkEntry> added = new ArrayList<>();
        for (BookmarkEntry bookmark : imported.getBookmarks()) {
            if (!pages.contains(bookmark.getPage())) {
                bookmark.setFolder(newIds.get(bookmark.getFolder()));
                added.add(bookmark);
            }
        }
        append(current.getBookmarks(), added, BookmarkEntry::getFolder, BookmarkEntry::getOrder,
            BookmarkEntry::setOrder);
    }

    /**
     * An imported folder is merged with the folder of the same name at the same place, existing or imported before
     * it; the other imported folders are added with new identifiers. Parents are visited before their subfolders, so
     * that the place of a subfolder is known when it is visited.
     *
     * @return the identifier given to each imported folder, and to the root
     */
    private static Map<String, String> mergeFolders(BookmarkData current, BookmarkData imported)
    {
        Set<String> usedIds = new HashSet<>();
        Map<String, String> idsByPlace = new HashMap<>();
        for (BookmarkFolder folder : sortByOrder(current.getFolders(), BookmarkFolder::getOrder)) {
            usedIds.add(folder.getId());
            // With duplicate names at the same place, the first folder receives the imported content.
            idsByPlace.putIfAbsent(getPlace(folder.getParent(), folder.getName()), folder.getId());
        }
        Map<String, List<BookmarkFolder>> importedChildren = sortByOrder(imported.getFolders(),
            BookmarkFolder::getOrder).stream().collect(Collectors.groupingBy(BookmarkFolder::getParent));

        Map<String, String> newIds = new HashMap<>();
        newIds.put(BookmarkFolder.ROOT, BookmarkFolder.ROOT);
        List<BookmarkFolder> added = new ArrayList<>();
        Deque<String> pending = new ArrayDeque<>(List.of(BookmarkFolder.ROOT));
        while (!pending.isEmpty()) {
            String parent = pending.pop();
            for (BookmarkFolder folder : importedChildren.getOrDefault(parent, List.of())) {
                String importedId = folder.getId();
                String place = getPlace(newIds.get(parent), folder.getName());
                String id = idsByPlace.get(place);
                if (id == null) {
                    id = newFolderId(usedIds);
                    idsByPlace.put(place, id);
                    folder.setId(id);
                    folder.setParent(newIds.get(parent));
                    added.add(folder);
                }
                newIds.put(importedId, id);
                pending.push(importedId);
            }
        }
        append(current.getFolders(), added, BookmarkFolder::getParent, BookmarkFolder::getOrder,
            BookmarkFolder::setOrder);
        return newIds;
    }

    /**
     * Folder identifiers cannot contain a slash, so the place is unambiguous. Names are compared like a user would:
     * ignoring the case and the surrounding spaces.
     */
    private static String getPlace(String parent, String name)
    {
        return parent + '/' + name.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Append the added items to the existing ones, each added item being positioned after the existing items of its
     * group (folder or parent folder). Positions are renumbered rather than offset, since stored and imported orders
     * are arbitrary integers whose sum could overflow.
     */
    private static <T> void append(List<T> existing, List<T> added, Function<T, String> getGroup,
        ToIntFunction<T> getOrder, ObjIntConsumer<T> setOrder)
    {
        Map<String, List<T>> groups = new HashMap<>();
        for (List<T> items : List.of(existing, added)) {
            for (T item : sortByOrder(items, getOrder)) {
                groups.computeIfAbsent(getGroup.apply(item), group -> new ArrayList<>()).add(item);
            }
        }
        for (List<T> group : groups.values()) {
            for (int position = 0; position < group.size(); position++) {
                setOrder.accept(group.get(position), position);
            }
        }
        existing.addAll(added);
    }

    private static <T> List<T> sortByOrder(List<T> items, ToIntFunction<T> getOrder)
    {
        // A stable sort: items of equal order keep their relative position.
        return items.stream().sorted(Comparator.comparingInt(getOrder)).toList();
    }

    /**
     * @return a new folder identifier, unique among the given ones, to which it is added
     */
    private static String newFolderId(Set<String> folderIds)
    {
        String id;
        do {
            // A UUID matches the folder identifier syntax checked by the normalizer.
            id = UUID.randomUUID().toString();
        } while (!folderIds.add(id));
        return id;
    }
}
