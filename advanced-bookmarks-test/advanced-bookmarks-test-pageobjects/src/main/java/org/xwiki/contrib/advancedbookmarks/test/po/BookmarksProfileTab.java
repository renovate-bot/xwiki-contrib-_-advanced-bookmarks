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
package org.xwiki.contrib.advancedbookmarks.test.po;

import org.openqa.selenium.By;
import org.xwiki.test.ui.po.BaseElement;

/**
 * The bookmarks tab that Advanced Bookmarks adds to the menu of the profile of a user.
 *
 * @version $Id$
 */
public class BookmarksProfileTab extends BaseElement
{
    // The exact link of the profile menu: other links of the page may carry the category of the current URL.
    private static final By TAB = By.cssSelector("a[href='?category=bookmarks']");

    /**
     * @return {@code true} when the profile page currently displayed has the bookmarks tab
     */
    public boolean isDisplayed()
    {
        return !getDriver().findElementsWithoutWaiting(TAB).isEmpty();
    }
}
