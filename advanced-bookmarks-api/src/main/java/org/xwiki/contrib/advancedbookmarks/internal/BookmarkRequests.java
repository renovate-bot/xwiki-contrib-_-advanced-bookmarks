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

import java.io.IOException;
import java.io.InputStream;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.container.Container;
import org.xwiki.container.Request;
import org.xwiki.container.servlet.ServletRequest;
import org.xwiki.csrf.CSRFToken;

/**
 * Checks that the current HTTP request may modify bookmarks: writes are only accepted in POST requests carrying a
 * valid CSRF token.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = BookmarkRequests.class)
@Singleton
public class BookmarkRequests
{
    /**
     * The request parameter holding the CSRF token, as for the other XWiki forms.
     */
    public static final String TOKEN_PARAMETER = "form_token";

    @Inject
    private Container container;

    @Inject
    private CSRFToken csrfToken;

    /**
     * @return {@code true} if the current request is an HTTP POST request
     */
    public boolean isPost()
    {
        HttpServletRequest request = getHttpRequest();
        return request != null && "POST".equalsIgnoreCase(request.getMethod());
    }

    /**
     * @return {@code true} if the current request carries the valid CSRF token of the current user
     */
    public boolean hasValidToken()
    {
        HttpServletRequest request = getHttpRequest();
        return request != null && this.csrfToken.isTokenValid(request.getParameter(TOKEN_PARAMETER));
    }

    /**
     * @param name the name of a request parameter
     * @return the value of the parameter, or {@code null} if it is missing or outside of an HTTP request
     */
    public String getParameter(String name)
    {
        HttpServletRequest request = getHttpRequest();
        return request != null ? request.getParameter(name) : null;
    }

    /**
     * Read a file sent in a {@code multipart/form-data} request. Unlike URL-encoded form parameters, such files are
     * not limited by the maximum POST size of the servlet container.
     *
     * @param name the name of the file part
     * @param maxBytes the maximum size of the file
     * @return the content of the file
     * @throws BookmarkInvalidDataException if the request has no such file, or the file is larger than allowed
     */
    public byte[] readFile(String name, int maxBytes) throws BookmarkInvalidDataException
    {
        HttpServletRequest request = getHttpRequest();
        if (request == null) {
            throw new BookmarkInvalidDataException(null);
        }
        try {
            Part part = request.getPart(name);
            if (part == null || part.getSize() > maxBytes) {
                throw new BookmarkInvalidDataException(null);
            }
            try (InputStream input = part.getInputStream()) {
                // One more byte than allowed is read, in case the declared size is wrong.
                byte[] content = input.readNBytes(maxBytes + 1);
                if (content.length > maxBytes) {
                    throw new BookmarkInvalidDataException(null);
                }
                return content;
            }
        } catch (IOException | ServletException | IllegalStateException e) {
            // Also thrown when the request is not a multipart request.
            throw new BookmarkInvalidDataException(e);
        }
    }

    /**
     * @param mode the import mode chosen by the user: {@code merge} or {@code replace}
     * @return {@code true} for the {@code replace} mode
     * @throws BookmarkInvalidDataException if the mode is neither of them
     */
    public static boolean isReplaceMode(String mode) throws BookmarkInvalidDataException
    {
        if ("replace".equals(mode)) {
            return true;
        }
        if ("merge".equals(mode)) {
            return false;
        }
        throw new BookmarkInvalidDataException(null);
    }

    private HttpServletRequest getHttpRequest()
    {
        Request request = this.container.getRequest();
        return request instanceof ServletRequest servletRequest ? servletRequest.getRequest() : null;
    }
}
