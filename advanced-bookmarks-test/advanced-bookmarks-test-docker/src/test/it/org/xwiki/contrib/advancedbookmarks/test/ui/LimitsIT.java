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

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.advancedbookmarks.test.po.BookmarkService;
import org.xwiki.contrib.advancedbookmarks.test.po.BookmarksManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests of the maximum size of the bookmarks of a user, which is set by the storage in Solr.
 *
 * @version $Id$
 */
@UITest
class LimitsIT
{
    // The maximum size of the stored bookmarks of a user, in UTF-8 bytes: the largest value Solr accepts.
    private static final int MAX_BYTES = 32_766;

    private static final String TOO_LARGE = "tooLarge";

    @Test
    @Order(1)
    void saveBookmarksUpToTheMaximumSize(TestUtils setup)
    {
        // The test instance has no default content: create the page displayed while calling the service.
        DocumentReference page = new DocumentReference(setup.getCurrentWiki(), "LimitsIT", "WebHome");
        AdminActions.loginAsAdmin(setup);
        setup.createPage(page, "", "Limits page");
        setup.createUserAndLogin("LimitsUser", "LimitsPassword");
        setup.gotoPage(page);
        BookmarkService service = new BookmarkService(setup);
        // A failed read would send no version, and the save would then be rejected for that reason only.
        BookmarkService.Response read = service.getBookmarks("");
        assertEquals(BookmarkService.OK, read.status(), String.valueOf(read.json()));
        assertEquals((long) MAX_BYTES, read.get("maxSize"));

        // Bookmarks in the format of the stored JSON, so that their size is the size the server measures.
        StringBuilder bookmarks = new StringBuilder();
        int count = 0;
        while (true) {
            String next = (count > 0 ? "," : "") + String.format(
                "{\"page\":\"%s:LimitsIT.Page%05d\",\"label\":\"\",\"folder\":\"\",\"order\":%d,\"panel\":false}",
                setup.getCurrentWiki(), count, count);
            if (size(json(bookmarks + next)) > MAX_BYTES) {
                break;
            }
            bookmarks.append(next);
            count++;
        }
        String data = json(bookmarks.toString());

        // Just below the limit: saved, and the space used is reported.
        BookmarkService.Response saved = service.save("", data, (String) read.get("version"));
        assertEquals(BookmarkService.OK, saved.status(), String.valueOf(saved.json()));
        assertEquals((long) size(data), saved.get("size"));
        assertEquals(count, service.getBookmarks("").getPages().size());

        // Above the limit: refused with its own error code, without any change, whether saved or added with the star.
        String tooLarge = json(bookmarks + ",{\"page\":\"" + setup.getCurrentWiki() + ":LimitsIT.Extra\"}");
        BookmarkService.Response refused = service.save("", tooLarge, (String) saved.get("version"));
        assertEquals(TOO_LARGE, refused.get("error"));
        assertEquals(TOO_LARGE, service.toggle(setup.getCurrentWiki() + ":LimitsIT.WebHome").get("error"));
        assertEquals(count, service.getBookmarks("").getPages().size());

        // The management page shows that the bookmarks are almost full; the percentage is rounded down.
        setup.gotoPage("AdvancedBookmarks", "WebHome");
        String usage = new BookmarksManager().waitUntilLoaded().getUsageText();
        assertTrue(usage.startsWith("Space used: 99%"), usage);
        assertTrue(usage.endsWith("Your bookmarks are almost full."), usage);
    }

    private static String json(String bookmarks)
    {
        return "{\"folders\":[],\"bookmarks\":[" + bookmarks + "]}";
    }

    private static int size(String json)
    {
        return json.getBytes(StandardCharsets.UTF_8).length;
    }
}
