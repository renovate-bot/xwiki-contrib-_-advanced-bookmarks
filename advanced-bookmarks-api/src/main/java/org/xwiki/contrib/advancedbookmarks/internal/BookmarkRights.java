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
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;

/**
 * Server-side access rules of bookmarks. The current user and wiki always come from the XWiki context, never from
 * the browser.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = BookmarkRights.class)
@Singleton
public class BookmarkRights
{
    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private AuthorizationManager authorizationManager;

    @Inject
    private Logger logger;

    /**
     * @return the current user, or {@code null} for a guest
     */
    public DocumentReference getCurrentUser()
    {
        return this.contextProvider.get().getUserReference();
    }

    /**
     * @return the identifier of the current wiki
     */
    public String getCurrentWiki()
    {
        return this.contextProvider.get().getWikiId();
    }

    /**
     * A user manages their own bookmarks; an administrator of the current wiki also manages those of any other
     * existing user.
     *
     * @param user the owner of the bookmarks
     * @return {@code true} if the current user may read and modify the bookmarks of {@code user} in this wiki
     */
    public boolean canManage(DocumentReference user)
    {
        DocumentReference currentUser = getCurrentUser();
        if (currentUser == null || user == null) {
            return false;
        }
        return currentUser.equals(user) || isAdmin() && isExistingUser(user);
    }

    /**
     * The author of the running code must administer the wiki too: otherwise the script of a page written by anyone
     * could use the rights of an administrator viewing that page, to read the bookmarks of others and leak them.
     * Without any current page, XWiki takes the current user as the author; a background job must be given the author
     * of the code that started it (see {@link BookmarkWikiImports}).
     *
     * @return {@code true} if the current user and the author of the running code administer the current wiki
     */
    public boolean isAdmin()
    {
        XWikiContext xcontext = this.contextProvider.get();
        DocumentReference currentUser = xcontext.getUserReference();
        DocumentReference author = xcontext.getAuthorReference();
        WikiReference wiki = new WikiReference(xcontext.getWikiId());
        return administers(currentUser, wiki) && (currentUser.equals(author) || administers(author, wiki));
    }

    /**
     * @return {@code false} for a guest, whose rights are never those of an administrator
     */
    private boolean administers(DocumentReference user, WikiReference wiki)
    {
        return user != null && this.authorizationManager.hasAccess(Right.ADMIN, user, wiki);
    }

    /**
     * @param page a page
     * @return {@code true} if the current user may view this page
     */
    public boolean canView(DocumentReference page)
    {
        return canView(getCurrentUser(), page);
    }

    /**
     * @param user a user, {@code null} for a guest
     * @param page a page
     * @return {@code true} if the user may view this page
     */
    public boolean canView(DocumentReference user, DocumentReference page)
    {
        return this.authorizationManager.hasAccess(Right.VIEW, user, page);
    }

    private boolean isExistingUser(DocumentReference user)
    {
        XWikiContext xcontext = this.contextProvider.get();
        try {
            return BookmarkUsers.isUserDocument(xcontext.getWiki().getDocument(user, xcontext));
        } catch (XWikiException e) {
            this.logger.warn("Unable to check whether a bookmark owner is a user: {}",
                ExceptionUtils.getRootCauseMessage(e));
            this.logger.debug("Full error:", e);
            return false;
        }
    }
}
