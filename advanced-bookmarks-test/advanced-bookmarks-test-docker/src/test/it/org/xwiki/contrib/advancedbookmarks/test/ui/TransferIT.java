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
 * Functional tests of the export and import of bookmarks, for a user and for the whole wiki.
 *
 * @version $Id$
 */
@UITest
class TransferIT
{
    private static final String USER = "TransferUser";

    private static final String PASSWORD = "TransferPassword";

    private static final String EMPTY = "{\"folders\":[],\"bookmarks\":[]}";

    private static final String VERSION = "version";

    private static final String CONTENT = "content";

    private static final String REPLACE = "replace";

    @Test
    @Order(1)
    void exportAndImport(TestUtils setup)
    {
        String wiki = setup.getCurrentWiki();
        DocumentReference first = new DocumentReference(wiki, "TransferIT", "First");
        DocumentReference second = new DocumentReference(wiki, "TransferIT", "Second");
        List<String> pages = List.of(wiki + ":TransferIT.First", wiki + ":TransferIT.Second");
        AdminActions.loginAsAdmin(setup);
        setup.createPage(first, "", "First page");
        setup.createPage(second, "", "Second page");

        setup.createUserAndLogin(USER, PASSWORD);
        setup.gotoPage(first);
        BookmarkService service = new BookmarkService(setup);
        pages.forEach(service::toggle);
        String export = (String) service.exportBookmarks("").get(CONTENT);
        assertTrue(export.contains("\"format\":\"advanced-bookmarks\""), export);

        // Replace mode restores the export.
        clear(service);
        BookmarkService.Response restored = service.importBookmarks("", export, REPLACE, currentVersion(service));
        assertEquals(BookmarkService.OK, restored.status());
        assertEquals(pages, service.getBookmarks("").getPages());

        // Merge mode skips the pages already bookmarked: importing the same file again changes nothing.
        BookmarkService.Response merged = service.importBookmarks("", export, "merge", currentVersion(service));
        assertEquals(BookmarkService.OK, merged.status());
        assertEquals(0L, merged.get("imported"));
        assertEquals(2L, merged.get("skipped"));
        assertEquals(pages, service.getBookmarks("").getPages());

        // The export of the whole wiki, imported by the administrator, restores the bookmarks of the user.
        AdminActions.loginAsAdmin(setup);
        setup.gotoPage(first);
        BookmarkService adminService = new BookmarkService(setup);
        BookmarkService.Response wikiExport = adminService.exportWiki();
        assertEquals(BookmarkService.OK, wikiExport.status());
        String wikiContent = (String) wikiExport.get(CONTENT);
        assertTrue(wikiContent.contains("\"XWiki." + USER + "\""), wikiContent);

        setup.login(USER, PASSWORD);
        setup.gotoPage(first);
        clear(new BookmarkService(setup));

        AdminActions.loginAsAdmin(setup);
        setup.gotoPage(first);
        assertEquals(BookmarkService.OK, adminService.startWikiImport(wikiContent, REPLACE).status());
        setup.getDriver().waitUntilCondition(
            driver -> "finished".equals(adminService.getWikiImportStatus().get("state")));
        BookmarkService.Response status = adminService.getWikiImportStatus();
        assertEquals(Boolean.FALSE, status.get("failed"));
        assertEquals(0L, status.get("failedUsers"));
        // The job runs with the rights of the administrator who started it: no user of the file may be refused.
        assertEquals(0L, status.get("skippedUsers"));
        assertEquals(pages, adminService.getBookmarks("XWiki." + USER).getPages());
    }

    private static String currentVersion(BookmarkService service)
    {
        return (String) service.getBookmarks("").get(VERSION);
    }

    private static void clear(BookmarkService service)
    {
        assertEquals(BookmarkService.OK, service.save("", EMPTY, currentVersion(service)).status());
        assertTrue(service.getBookmarks("").getPages().isEmpty());
    }
}
