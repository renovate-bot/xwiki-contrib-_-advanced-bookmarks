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
import org.openqa.selenium.By;
import org.xwiki.contrib.advancedbookmarks.test.po.BookmarkService;
import org.xwiki.contrib.advancedbookmarks.test.po.BookmarksManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests of the access rules: a user only sees and changes their own bookmarks, a wiki administrator
 * manages the bookmarks of any user.
 *
 * @version $Id$
 */
@UITest
class RightsIT
{
    private static final String OWNER = "RightsOwner";

    private static final String OWNER_PASSWORD = "RightsOwnerPassword";

    private static final String OTHER = "RightsOther";

    // The exact link of the profile menu: other links of the page may carry the category of the current URL.
    private static final By PROFILE_TAB = By.cssSelector("a[href='?category=bookmarks']");

    @Test
    @Order(1)
    void accessRules(TestUtils setup)
    {
        DocumentReference page = new DocumentReference(setup.getCurrentWiki(), "RightsIT", "Page");
        String pageReference = setup.getCurrentWiki() + ":RightsIT.Page";
        String owner = setup.getCurrentWiki() + ":XWiki." + OWNER;
        DocumentReference hiddenPage = new DocumentReference(setup.getCurrentWiki(), "RightsIT", "Hidden");
        AdminActions.loginAsAdmin(setup);
        setup.createPage(page, "", "Rights page");
        setup.createPage(hiddenPage, "", "Hidden page");
        setup.setRights(hiddenPage, "", "XWiki." + OWNER, "view", false);

        // The owner sees their bookmarks in their profile tab.
        setup.createUserAndLogin(OWNER, OWNER_PASSWORD);
        setup.gotoPage(page);
        BookmarkService ownerService = new BookmarkService(setup);
        assertEquals(BookmarkService.OK, ownerService.toggle(pageReference).status());

        // A page the owner cannot view cannot be added, not even by saving the bookmarks directly.
        String version = (String) ownerService.getBookmarks("").get("version");
        BookmarkService.Response hiddenAdded = ownerService.save("", "{\"folders\":[],\"bookmarks\":[{\"page\":\""
            + pageReference + "\"},{\"page\":\"" + setup.getCurrentWiki() + ":RightsIT.Hidden\"}]}", version);
        assertEquals(403, hiddenAdded.status());
        assertEquals("forbidden", hiddenAdded.get("error"));
        setup.gotoPage("XWiki", OWNER, "view", "category=bookmarks");
        assertFalse(setup.getDriver().findElementsWithoutWaiting(PROFILE_TAB).isEmpty());
        assertEquals(owner, new BookmarksManager().waitUntilLoaded().getUser());

        // Another user can neither read nor change them.
        setup.createUserAndLogin(OTHER, "RightsOtherPassword");
        setup.gotoPage(page);
        BookmarkService service = new BookmarkService(setup);
        BookmarkService.Response denied = service.getBookmarks(owner);
        assertEquals(403, denied.status());
        assertEquals("forbidden", denied.get("error"));
        assertEquals(403, service.save(owner, "{\"folders\":[],\"bookmarks\":[]}", "0").status());
        assertEquals(403, service.exportBookmarks(owner).status());
        setup.gotoPage("AdvancedBookmarks", "WebHome", "view", "user=" + owner);
        BookmarksManager manager = new BookmarksManager();
        assertFalse(manager.isDisplayed());
        assertTrue(manager.hasErrorMessage());
        setup.gotoPage("XWiki", OWNER);
        assertTrue(setup.getDriver().findElementsWithoutWaiting(PROFILE_TAB).isEmpty());

        // The refused write changed nothing.
        setup.login(OWNER, OWNER_PASSWORD);
        setup.gotoPage(page);
        assertEquals(List.of(pageReference), new BookmarkService(setup).getBookmarks("").getPages());

        // A wiki administrator manages the bookmarks of any user, designated by a short name too.
        AdminActions.loginAsAdmin(setup);
        setup.gotoPage(page);
        BookmarkService.Response shortName = new BookmarkService(setup).getBookmarks(OWNER);
        assertEquals(BookmarkService.OK, shortName.status());
        assertEquals(owner, shortName.get("user"));
        assertEquals(List.of(pageReference), shortName.getPages());
        setup.gotoPage("AdvancedBookmarks", "WebHome", "view", "user=" + OWNER);
        assertEquals(owner, new BookmarksManager().waitUntilLoaded().getUser());
        setup.gotoPage("XWiki", OWNER);
        assertFalse(setup.getDriver().findElementsWithoutWaiting(PROFILE_TAB).isEmpty());
    }
}
