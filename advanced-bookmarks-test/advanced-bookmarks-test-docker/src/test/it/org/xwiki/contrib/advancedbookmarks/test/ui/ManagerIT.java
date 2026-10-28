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
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.advancedbookmarks.test.po.BookmarkService;
import org.xwiki.contrib.advancedbookmarks.test.po.BookmarksManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests of the management page, {@code AdvancedBookmarks.WebHome}.
 *
 * @version $Id$
 */
@UITest
class ManagerIT
{
    private static final String FOLDER = "Work";

    @Test
    @Order(1)
    void organizeBookmarks(TestUtils setup)
    {
        DocumentReference first = new DocumentReference(setup.getCurrentWiki(), "ManagerIT", "First");
        DocumentReference second = new DocumentReference(setup.getCurrentWiki(), "ManagerIT", "Second");
        String firstPage = setup.getCurrentWiki() + ":ManagerIT.First";
        String secondPage = setup.getCurrentWiki() + ":ManagerIT.Second";
        AdminActions.loginAsAdmin(setup);
        setup.createPage(first, "", "First page");
        setup.createPage(second, "", "Second page");

        setup.createUserAndLogin("ManagerUser", "ManagerPassword");
        setup.gotoPage(first);
        BookmarkService service = new BookmarkService(setup);
        service.toggle(firstPage);
        service.toggle(secondPage);

        setup.gotoPage("AdvancedBookmarks", "WebHome");
        BookmarksManager manager = new BookmarksManager().waitUntilLoaded();
        assertFalse(manager.hasUserPicker());
        // Two bookmarks use a tiny part of the allowed space.
        double usedSpace = manager.getUsedSpace();
        assertTrue(usedSpace > 0);
        assertEquals("Space used: 0%", manager.getUsageText());
        manager.createFolder(FOLDER);
        manager.moveBookmark(firstPage, FOLDER);
        manager.togglePanel(secondPage);

        // The changes are saved automatically: wait until the server has them all.
        setup.getDriver().waitUntilCondition(driver -> {
            BookmarkService.Response bookmarks = service.getBookmarks("");
            String folderId = bookmarks.getFolderList().stream().filter(folder -> FOLDER.equals(folder.get("name")))
                .map(folder -> (String) folder.get("id")).findFirst().orElse(null);
            Map<String, Object> firstBookmark = find(bookmarks, firstPage);
            Map<String, Object> secondBookmark = find(bookmarks, secondPage);
            return folderId != null && folderId.equals(firstBookmark.get("folder"))
                && Boolean.TRUE.equals(secondBookmark.get("panel"));
        });

        // The organization is displayed again after a reload.
        setup.gotoPage("AdvancedBookmarks", "WebHome");
        BookmarksManager reloaded = new BookmarksManager().waitUntilLoaded();
        assertEquals(List.of(FOLDER), reloaded.getFolderNames().stream().filter(FOLDER::equals).toList());
        // The new folder takes some space.
        assertTrue(reloaded.getUsedSpace() > usedSpace);
    }

    private static Map<String, Object> find(BookmarkService.Response bookmarks, String page)
    {
        return bookmarks.getBookmarkList().stream().filter(bookmark -> Objects.equals(page, bookmark.get("page")))
            .findFirst().orElse(Map.of());
    }
}
