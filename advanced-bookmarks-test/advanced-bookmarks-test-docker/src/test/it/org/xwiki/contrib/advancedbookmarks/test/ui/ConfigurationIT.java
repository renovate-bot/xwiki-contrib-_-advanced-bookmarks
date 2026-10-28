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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.advancedbookmarks.test.po.BookmarkService;
import org.xwiki.contrib.advancedbookmarks.test.po.BookmarkStar;
import org.xwiki.contrib.advancedbookmarks.test.po.BookmarksPanel;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests of the configuration of the wiki: star display, excluded pages and panel size.
 *
 * @version $Id$
 */
@UITest
class ConfigurationIT
{
    private static final String USER = "ConfigurationUser";

    private static final String PASSWORD = "ConfigurationPassword";

    @AfterEach
    void resetConfiguration(TestUtils setup)
    {
        AdminActions.loginAsAdmin(setup);
        AdminActions.resetConfiguration(setup);
    }

    @Test
    @Order(1)
    void configureStarExclusionsAndPanel(TestUtils setup)
    {
        String wiki = setup.getCurrentWiki();
        DocumentReference page = new DocumentReference(wiki, "ConfigurationIT", "Page");
        DocumentReference excluded = new DocumentReference(wiki, List.of("ConfigurationIT", "Excluded"), "WebHome");
        DocumentReference excludedChild =
            new DocumentReference(wiki, List.of("ConfigurationIT", "Excluded"), "Child");
        AdminActions.loginAsAdmin(setup);
        AdminActions.showBookmarksPanel(setup);
        setup.createPage(page, "", "Configuration page");
        setup.createPage(excluded, "", "Excluded page");
        setup.createPage(excludedChild, "", "Excluded child");
        setup.createUserAndLogin(USER, PASSWORD);

        // The star can be hidden on the whole wiki.
        AdminActions.loginAsAdmin(setup);
        AdminActions.configure(setup, "starEnabled", "0");
        setup.login(USER, PASSWORD);
        setup.gotoPage(page);
        assertFalse(new BookmarkStar().isDisplayed());

        // A WebHome page excludes its children too: no star, and the service refuses the addition.
        AdminActions.loginAsAdmin(setup);
        AdminActions.configure(setup, "starEnabled", "1", "excludedPages", "ConfigurationIT.Excluded.WebHome");
        setup.login(USER, PASSWORD);
        setup.gotoPage(page);
        assertTrue(new BookmarkStar().isDisplayed());
        setup.gotoPage(excludedChild);
        assertFalse(new BookmarkStar().isDisplayed());
        BookmarkService service = new BookmarkService(setup);
        assertEquals(403, service.toggle(wiki + ":ConfigurationIT.Excluded.Child").status());
        assertEquals(Boolean.FALSE, service.isBookmarked(wiki + ":ConfigurationIT.Excluded.Child")
            .get("bookmarkable"));

        // The panel displays at most the configured number of bookmarks.
        assertEquals(BookmarkService.OK, service.toggle(wiki + ":ConfigurationIT.Page").status());
        AdminActions.loginAsAdmin(setup);
        AdminActions.configure(setup, "panelLimit", "1", "excludedPages", "");
        setup.login(USER, PASSWORD);
        setup.gotoPage(excluded);
        assertEquals(BookmarkService.OK, new BookmarkService(setup).toggle(wiki + ":ConfigurationIT.Excluded.WebHome")
            .status());
        setup.gotoPage(page);
        assertEquals(List.of("Configuration page"), new BookmarksPanel().getItems());
    }
}
