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
package org.xwiki.contrib.advancedbookmarks.test.ui;

import java.util.List;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.advancedbookmarks.test.po.BookmarkService;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests of the reactions to page events: renamed pages, deleted pages, deleted and renamed users.
 *
 * @version $Id$
 */
@UITest
class EventsIT
{
    private static final String SPACE = "EventsIT";

    @Test
    @Order(1)
    void renameAndDeletePage(TestUtils setup)
    {
        String wiki = setup.getCurrentWiki();
        DocumentReference source = new DocumentReference(wiki, SPACE, "Source");
        DocumentReference target = new DocumentReference(wiki, SPACE, "Target");
        String targetReference = wiki + ":EventsIT.Target";
        AdminActions.loginAsAdmin(setup);
        setup.createPage(source, "", "Event page");
        setup.createUserAndLogin("EventsPageUser", "EventsPagePassword");
        setup.gotoPage(source);
        new BookmarkService(setup).toggle(wiki + ":EventsIT.Source");

        AdminActions.loginAsAdmin(setup);
        AdminActions.rename(setup, source, "Target");
        setup.login("EventsPageUser", "EventsPagePassword");
        setup.gotoPage(target);
        BookmarkService service = new BookmarkService(setup);
        assertEquals(List.of(targetReference), service.getBookmarks("").getPages());

        // A deleted page stays in the bookmarks, marked as missing.
        DocumentReference shared = createSharedPage(setup);
        setup.deletePage(target);
        setup.login("EventsPageUser", "EventsPagePassword");
        setup.gotoPage(shared);
        BookmarkService.Response bookmarks = new BookmarkService(setup).getBookmarks("");
        assertEquals(List.of(targetReference), bookmarks.getPages());
        assertEquals(Boolean.FALSE, bookmarks.getBookmarkList().get(0).get("exists"));
    }

    @Test
    @Order(2)
    void deleteUser(TestUtils setup)
    {
        String user = "EventsDeletedUser";
        DocumentReference shared = bookmarkSharedPage(setup, user);

        AdminActions.loginAsAdmin(setup);
        setup.deletePage("XWiki", user);
        // A new user with the same name does not inherit the bookmarks of the deleted one.
        setup.createUserAndLogin(user, "EventsPassword");
        setup.gotoPage(shared);
        assertTrue(new BookmarkService(setup).getBookmarks("").getPages().isEmpty());
    }

    @Test
    @Order(3)
    void renameUser(TestUtils setup)
    {
        // Decided behavior: XWiki handles the rename of a user page as the deletion of the old user, whose bookmarks
        // are erased; they are not transferred to the new name (the README describes a manual export and import).
        String user = "EventsRenamedUser";
        DocumentReference shared = bookmarkSharedPage(setup, user);

        AdminActions.loginAsAdmin(setup);
        AdminActions.rename(setup, new DocumentReference(setup.getCurrentWiki(), "XWiki", user), user + "New");
        setup.gotoPage(shared);
        BookmarkService service = new BookmarkService(setup);
        BookmarkService.Response renamed = service.getBookmarks("XWiki." + user + "New");
        assertEquals(BookmarkService.OK, renamed.status());
        assertTrue(renamed.getPages().isEmpty());
        // The old user does not exist anymore: their bookmarks cannot be read either.
        assertEquals(403, service.getBookmarks("XWiki." + user).status());
    }

    private static DocumentReference bookmarkSharedPage(TestUtils setup, String user)
    {
        DocumentReference shared = createSharedPage(setup);
        setup.createUserAndLogin(user, "EventsPassword");
        setup.gotoPage(shared);
        BookmarkService service = new BookmarkService(setup);
        assertEquals(BookmarkService.OK, service.toggle(setup.getCurrentWiki() + ":EventsIT.Shared").status());
        assertEquals(1, service.getBookmarks("").getPages().size());
        return shared;
    }

    // The test instance has no default content: the tests create the pages they display.
    private static DocumentReference createSharedPage(TestUtils setup)
    {
        DocumentReference shared = new DocumentReference(setup.getCurrentWiki(), SPACE, "Shared");
        AdminActions.loginAsAdmin(setup);
        setup.createPage(shared, "", "Shared page");
        return shared;
    }
}
