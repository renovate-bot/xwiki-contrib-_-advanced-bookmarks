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

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceProvider;
import org.xwiki.rendering.syntax.Syntax;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;

/**
 * Reads information about bookmarked pages. Callers are responsible for checking that the current user may view a
 * page before exposing any information returned here.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = BookmarkPages.class)
@Singleton
public class BookmarkPages
{
    private static final String VIEW_ACTION = "view";

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private EntityReferenceProvider defaultReferences;

    @Inject
    private Logger logger;

    /**
     * The text displayed for a bookmark without label: the title of the page or, failing that, its name (the name of
     * its space for a space home page, such as {@code Main.WebHome}).
     *
     * @param page a page
     * @param exists whether the page exists: the title of a missing page is not computed
     * @return the text, never empty
     */
    public String getDisplayTitle(DocumentReference page, boolean exists)
    {
        String title = exists ? getPlainTitle(page).trim() : "";
        if (!title.isEmpty()) {
            return title;
        }
        String spaceHomeName = this.defaultReferences.getDefaultReference(EntityType.DOCUMENT).getName();
        return spaceHomeName.equals(page.getName()) ? page.getLastSpaceReference().getName() : page.getName();
    }

    /**
     * @param page a page
     * @return {@code false} if the page is known not to exist
     */
    public boolean exists(DocumentReference page)
    {
        XWikiContext xcontext = this.contextProvider.get();
        try {
            return xcontext.getWiki().exists(page, xcontext);
        } catch (XWikiException e) {
            // Better display the bookmark normally than wrongly display it as deleted.
            this.logger.warn("Unable to check whether a bookmarked page exists: {}",
                ExceptionUtils.getRootCauseMessage(e));
            return true;
        }
    }

    /**
     * @param page a page
     * @return the URL to view the page
     */
    public String getURL(DocumentReference page)
    {
        XWikiContext xcontext = this.contextProvider.get();
        return xcontext.getWiki().getURL(page, VIEW_ACTION, xcontext);
    }

    /**
     * @param user a user
     * @return the display name of the user, as plain text
     */
    public String getUserName(DocumentReference user)
    {
        XWikiContext xcontext = this.contextProvider.get();
        return xcontext.getWiki().getPlainUserName(user, xcontext);
    }

    /**
     * @param page an existing page
     * @return the title of the page as plain text, or an empty string if it cannot be computed
     */
    public String getPlainTitle(DocumentReference page)
    {
        XWikiContext xcontext = this.contextProvider.get();
        XWiki xwiki = xcontext.getWiki();
        try {
            return xwiki.getDocument(page, xcontext).getRenderedTitle(Syntax.PLAIN_1_0, xcontext);
        } catch (XWikiException e) {
            // The title is only a default label: its absence is not an error for the user.
            this.logger.warn("Unable to compute the title of a bookmarked page: {}",
                ExceptionUtils.getRootCauseMessage(e));
            return "";
        }
    }
}
