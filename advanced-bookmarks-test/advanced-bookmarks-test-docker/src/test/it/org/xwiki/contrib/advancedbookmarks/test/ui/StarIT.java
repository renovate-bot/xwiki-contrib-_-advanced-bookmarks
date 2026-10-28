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

import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.contrib.advancedbookmarks.test.po.BookmarkStar;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Functional tests of the star of the page action bar.
 *
 * @version $Id$
 */
@UITest
class StarIT
{
    @Test
    @Order(1)
    void toggleBookmark(TestUtils setup, TestReference testReference)
    {
        setup.createUserAndLogin("StarUser", "StarPassword");
        setup.createPage(testReference, "Star test page");

        BookmarkStar star = new BookmarkStar();
        assertTrue(star.isDisplayed());
        assertFalse(star.isBookmarked());

        star.toggle();
        assertTrue(star.isBookmarked());

        // The state comes from the server: it is kept when the page is loaded again.
        setup.gotoPage(testReference);
        star = new BookmarkStar();
        assertTrue(star.isBookmarked());

        star.toggle();
        assertFalse(star.isBookmarked());
    }
}
