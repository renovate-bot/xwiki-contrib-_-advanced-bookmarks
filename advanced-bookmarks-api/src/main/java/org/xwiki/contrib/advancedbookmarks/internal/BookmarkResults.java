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
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkAccessDeniedException;
import org.xwiki.contrib.advancedbookmarks.BookmarkException;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreConflictException;
import org.xwiki.contrib.advancedbookmarks.BookmarkTooLargeException;

/**
 * Runs the operations of the script services and translates their outcome into maps ready to be serialized as JSON.
 * Every result has a {@code status} key holding an HTTP status code and, on failure, an {@code error} key holding a
 * code without any detail: {@code forbidden}, {@code invalidToken}, {@code methodNotAllowed}, {@code invalid},
 * {@code tooLarge}, {@code conflict} or {@code storageError}. Details are logged on the server.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = BookmarkResults.class)
@Singleton
public class BookmarkResults
{
    /**
     * Key of the HTTP status code in every result.
     */
    public static final String STATUS = "status";

    /**
     * Key of the error code in the result of a failed operation.
     */
    public static final String ERROR = "error";

    private static final int OK = 200;

    private static final int BAD_REQUEST = 400;

    private static final int FORBIDDEN = 403;

    private static final int METHOD_NOT_ALLOWED = 405;

    private static final int CONFLICT = 409;

    private static final int SERVER_ERROR = 500;

    @Inject
    private BookmarkRequests requests;

    @Inject
    private Logger logger;

    /**
     * An operation of a script service, whose failures are translated into error results.
     */
    @FunctionalInterface
    public interface Operation
    {
        /**
         * @return the result of a successful operation
         * @throws BookmarkException if the operation fails
         */
        Map<String, Object> run() throws BookmarkException;
    }

    /**
     * @param operation an operation that reads data
     * @return the result of the operation, or an error result if it failed
     */
    public Map<String, Object> execute(Operation operation)
    {
        // A single return keeps the Checkstyle ReturnCount rule. A subclass is caught before its parent class, which
        // the compiler checks.
        Map<String, Object> result;
        try {
            result = operation.run();
        } catch (BookmarkAccessDeniedException e) {
            result = error(FORBIDDEN, "forbidden");
        } catch (BookmarkTooLargeException e) {
            result = error(BAD_REQUEST, "tooLarge");
        } catch (BookmarkInvalidDataException e) {
            result = error(BAD_REQUEST, "invalid");
        } catch (BookmarkStoreConflictException e) {
            result = error(CONFLICT, "conflict");
        } catch (BookmarkException e) {
            this.logger.error("Advanced Bookmarks operation failed.", e);
            result = error(SERVER_ERROR, "storageError");
        }
        return result;
    }

    /**
     * Run an operation that modifies data, only in a POST request carrying a valid CSRF token.
     *
     * @param operation an operation that modifies data
     * @return the result of the operation, or an error result if the request is refused or the operation failed
     */
    public Map<String, Object> executeWrite(Operation operation)
    {
        if (!this.requests.isPost()) {
            return error(METHOD_NOT_ALLOWED, "methodNotAllowed");
        }
        if (!this.requests.hasValidToken()) {
            return error(FORBIDDEN, "invalidToken");
        }
        return execute(operation);
    }

    /**
     * @return a new successful result, to which the operation adds its own keys
     */
    public static Map<String, Object> success()
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(STATUS, OK);
        return result;
    }

    private static Map<String, Object> error(int status, String code)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(STATUS, status);
        result.put(ERROR, code);
        return result;
    }
}
