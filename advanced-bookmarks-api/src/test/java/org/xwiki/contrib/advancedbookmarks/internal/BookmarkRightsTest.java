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

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.WikiReference;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the server-side access rules of bookmarks.
 *
 * @version $Id$
 */
@ComponentTest
class BookmarkRightsTest
{
    private static final String WIKI = "subwiki";

    private static final DocumentReference ALICE = new DocumentReference("xwiki", "XWiki", "alice");

    private static final DocumentReference BOB = new DocumentReference("xwiki", "XWiki", "bob");

    private static final DocumentReference PAGE = new DocumentReference(WIKI, "Space", "Page");

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private BookmarkRights rights;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    @MockComponent
    private AuthorizationManager authorizationManager;

    private XWikiContext xcontext;

    private XWiki xwiki;

    @BeforeEach
    void setUp()
    {
        this.xcontext = mock(XWikiContext.class);
        this.xwiki = mock(XWiki.class);
        when(this.contextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getWiki()).thenReturn(this.xwiki);
        when(this.xcontext.getWikiId()).thenReturn(WIKI);
        when(this.xcontext.getUserReference()).thenReturn(ALICE);
        // Outside of a page, XWiki takes the current user as the author of the running code.
        when(this.xcontext.getAuthorReference()).thenReturn(ALICE);
    }

    @Test
    void takesTheUserAndWikiFromTheContext()
    {
        assertEquals(ALICE, this.rights.getCurrentUser());
        assertEquals(WIKI, this.rights.getCurrentWiki());
    }

    @Test
    void letsUsersManageTheirOwnBookmarks()
    {
        assertTrue(this.rights.canManage(ALICE));
        assertFalse(this.rights.canManage(null));
    }

    @Test
    void refusesEverythingToGuests()
    {
        when(this.xcontext.getUserReference()).thenReturn(null);

        assertFalse(this.rights.canManage(ALICE));
        assertFalse(this.rights.isAdmin());
    }

    @Test
    void refusesTheBookmarksOfOthersToNonAdministrators()
    {
        assertFalse(this.rights.canManage(BOB));
    }

    @Test
    void letsAdministratorsOfTheCurrentWikiManageExistingUsers() throws Exception
    {
        when(this.authorizationManager.hasAccess(Right.ADMIN, ALICE, new WikiReference(WIKI))).thenReturn(true);
        XWikiDocument bobProfile = mock(XWikiDocument.class);
        when(bobProfile.getXObject(any(org.xwiki.model.reference.EntityReference.class)))
            .thenReturn(new BaseObject());
        when(this.xwiki.getDocument(BOB, this.xcontext)).thenReturn(bobProfile);

        assertTrue(this.rights.isAdmin());
        assertTrue(this.rights.canManage(BOB));
    }

    @Test
    void requiresThatTheAuthorOfTheRunningCodeIsAnAdministratorToo() throws Exception
    {
        DocumentReference carol = new DocumentReference("xwiki", "XWiki", "carol");
        WikiReference wiki = new WikiReference(WIKI);
        when(this.authorizationManager.hasAccess(Right.ADMIN, ALICE, wiki)).thenReturn(true);
        when(this.authorizationManager.hasAccess(Right.ADMIN, carol, wiki)).thenReturn(true);
        XWikiDocument bobProfile = mock(XWikiDocument.class);
        when(bobProfile.getXObject(any(org.xwiki.model.reference.EntityReference.class)))
            .thenReturn(new BaseObject());
        when(this.xwiki.getDocument(BOB, this.xcontext)).thenReturn(bobProfile);

        // A page written by a user who does not administer the wiki, viewed by an administrator.
        when(this.xcontext.getAuthorReference()).thenReturn(BOB);
        assertFalse(this.rights.isAdmin());
        assertFalse(this.rights.canManage(BOB));
        // Users still manage their own bookmarks, whoever wrote the page.
        assertTrue(this.rights.canManage(ALICE));

        // A page written by another administrator, such as the pages of the extension.
        when(this.xcontext.getAuthorReference()).thenReturn(carol);
        assertTrue(this.rights.isAdmin());
        assertTrue(this.rights.canManage(BOB));

        when(this.xcontext.getAuthorReference()).thenReturn(null);
        assertFalse(this.rights.isAdmin());
    }

    @Test
    void refusesAdministratorsTheBookmarksOfANonUser() throws Exception
    {
        when(this.authorizationManager.hasAccess(Right.ADMIN, ALICE, new WikiReference(WIKI))).thenReturn(true);
        when(this.xwiki.getDocument(PAGE, this.xcontext)).thenReturn(mock(XWikiDocument.class));

        assertFalse(this.rights.canManage(PAGE));
    }

    @Test
    void refusesAdministratorsWhenTheUserCannotBeChecked() throws Exception
    {
        when(this.authorizationManager.hasAccess(Right.ADMIN, ALICE, new WikiReference(WIKI))).thenReturn(true);
        when(this.xwiki.getDocument(BOB, this.xcontext)).thenThrow(new XWikiException());

        assertFalse(this.rights.canManage(BOB));
        assertEquals("Unable to check whether a bookmark owner is a user: XWikiException: Error number 0 in 0",
            this.logCapture.getMessage(0));
    }

    @Test
    void doesNotLoadTheProfileOfTheCurrentUser() throws Exception
    {
        this.rights.canManage(ALICE);

        verify(this.xwiki, never()).getDocument(any(DocumentReference.class), any(XWikiContext.class));
    }

    @Test
    void checksTheViewRightOfTheCurrentUser()
    {
        when(this.authorizationManager.hasAccess(Right.VIEW, ALICE, PAGE)).thenReturn(true);

        assertTrue(this.rights.canView(PAGE));
        assertFalse(this.rights.canView(new DocumentReference(WIKI, "Space", "Secret")));
    }

    @Test
    void checksTheViewRightOfAGivenUser()
    {
        when(this.authorizationManager.hasAccess(Right.VIEW, BOB, PAGE)).thenReturn(true);

        assertTrue(this.rights.canView(BOB, PAGE));
        assertFalse(this.rights.canView(ALICE, PAGE));
    }

    @Test
    void recognizesUserDocuments()
    {
        assertFalse(BookmarkUsers.isUserDocument(null));
        assertFalse(BookmarkUsers.isUserDocument(mock(XWikiDocument.class)));
    }
}
