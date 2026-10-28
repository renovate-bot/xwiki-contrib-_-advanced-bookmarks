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
import org.xwiki.contrib.advancedbookmarks.test.po.BookmarksManager;
import org.xwiki.contrib.advancedbookmarks.test.po.BookmarksPanel;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Functional tests of the {@code Panels.MyBookmarks} panel.
 *
 * @version $Id$
 */
@UITest
class PanelIT
{
    private static final String USER = "PanelUser";

    private static final String PASSWORD = "PanelPassword";

    @Test
    @Order(1)
    void listBookmarksAndDeletedPages(TestUtils setup)
    {
        DocumentReference first = new DocumentReference(setup.getCurrentWiki(), "PanelIT", "First");
        DocumentReference second = new DocumentReference(setup.getCurrentWiki(), "PanelIT", "Second");
        AdminActions.loginAsAdmin(setup);
        AdminActions.showBookmarksPanel(setup);
        setup.createPage(first, "", "First page");
        setup.createPage(second, "", "Second page");

        setup.createUserAndLogin(USER, PASSWORD);
        setup.gotoPage(first);
        BookmarkService service = new BookmarkService(setup);
        assertEquals(BookmarkService.OK, service.toggle("PanelIT.First").status());
        assertEquals(BookmarkService.OK, service.toggle("PanelIT.Second").status());

        setup.gotoPage(first);
        assertEquals(List.of("First page", "Second page"), new BookmarksPanel().getItems());

        // A deleted page stays in the panel, struck through.
        AdminActions.loginAsAdmin(setup);
        setup.deletePage(second);
        setup.login(USER, PASSWORD);
        setup.gotoPage(first);
        BookmarksPanel panel = new BookmarksPanel();
        assertEquals(List.of("Second page"), panel.getDeletedItems());

        BookmarksManager manager = panel.clickViewAll().waitUntilLoaded();
        assertEquals(setup.getCurrentWiki() + ":XWiki." + USER, manager.getUser());
    }
}
