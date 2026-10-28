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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;

/**
 * Resolves the user and page references received from scripts and requests, after checking their length.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = BookmarkReferences.class)
@Singleton
public class BookmarkReferences
{
    private static final EntityReference USER_SPACE = new EntityReference("XWiki", EntityType.SPACE);

    @Inject
    private BookmarkRights rights;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    /**
     * The current user is always taken from the context; a user name received from the browser is only a request to
     * manage someone else's bookmarks, which the caller must still authorize.
     *
     * @param user a user reference such as {@code XWiki.jdoe} or {@code jdoe}; empty or {@code null} for the current
     *            user
     * @return the resolved user, {@code null} for a guest when no user is given
     * @throws BookmarkInvalidDataException if the reference is too long
     */
    public DocumentReference resolveUser(String user) throws BookmarkInvalidDataException
    {
        if (StringUtils.isBlank(user)) {
            return this.rights.getCurrentUser();
        }
        return this.resolver.resolve(checkReference(user), USER_SPACE);
    }

    /**
     * @param page a page reference, relative to the current wiki
     * @return the resolved page
     * @throws BookmarkInvalidDataException if the reference is empty or too long
     */
    public DocumentReference resolvePage(String page) throws BookmarkInvalidDataException
    {
        return this.resolver.resolve(checkReference(page));
    }

    /**
     * The single check of the texts received from users, files and the storage, which the callers report each in
     * their own way.
     *
     * @param text a text, possibly {@code null}
     * @param maxLength the maximum length of the text
     * @return {@code true} if the text is neither {@code null} nor blank, and not longer than the maximum length
     */
    public static boolean isValidText(String text, int maxLength)
    {
        return text != null && !text.isBlank() && text.length() <= maxLength;
    }

    /**
     * @param reference a user or page reference, possibly {@code null}
     * @return {@code true} if the reference is neither {@code null} nor blank, and short enough to be stored
     */
    public static boolean isValidReference(String reference)
    {
        return isValidText(reference, BookmarkJsonCodec.MAX_PAGE_REFERENCE_LENGTH);
    }

    private static String checkReference(String reference) throws BookmarkInvalidDataException
    {
        if (!isValidReference(reference)) {
            throw new BookmarkInvalidDataException(null);
        }
        return reference;
    }
}
