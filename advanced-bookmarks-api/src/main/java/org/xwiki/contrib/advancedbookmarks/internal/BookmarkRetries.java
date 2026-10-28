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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.advancedbookmarks.BookmarkException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;

/**
 * Runs an update of stored bookmarks again when another update changed them meanwhile. A few attempts are enough:
 * concurrent updates of the bookmarks of one user are rare, typically two browser tabs.
 *
 * @version $Id$
 * @since 1.0
 */
public final class BookmarkRetries
{
    /**
     * The number of attempts before a version conflict is reported to the caller.
     */
    public static final int MAX_ATTEMPTS = 3;

    private static final Logger LOGGER = LoggerFactory.getLogger(BookmarkRetries.class);

    /**
     * An update of stored bookmarks, which reads them at each attempt so that it applies to their latest version.
     *
     * @param <T> the type of the result of the update
     * @param <E> the type of the exceptions of the update
     * @version $Id$
     * @since 1.0
     */
    @FunctionalInterface
    public interface Update<T, E extends BookmarkException>
    {
        /**
         * @return the result of the update
         * @throws BookmarkStoreConflictException if the bookmarks were changed since they were read
         * @throws E if the update fails
         */
        T run() throws BookmarkStoreConflictException, E;
    }

    private BookmarkRetries()
    {
        // Utility class.
    }

    /**
     * @param <T> the type of the result of the update
     * @param <E> the type of the exceptions of the update
     * @param update the update to run, which must read the stored bookmarks again at each attempt
     * @return the result of the first attempt that does not meet a version conflict
     * @throws BookmarkStoreConflictException if the last attempt still meets a version conflict
     * @throws E if an attempt fails for another reason, which is not retried
     */
    public static <T, E extends BookmarkException> T onConflict(Update<T, E> update)
        throws BookmarkStoreConflictException, E
    {
        for (int attempt = 1;; attempt++) {
            try {
                return update.run();
            } catch (BookmarkStoreConflictException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
                LOGGER.debug("Retrying a bookmark update after a version conflict.");
            }
        }
    }
}
