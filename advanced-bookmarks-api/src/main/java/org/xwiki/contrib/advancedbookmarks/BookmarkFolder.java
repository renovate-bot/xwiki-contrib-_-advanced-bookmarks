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
package org.xwiki.contrib.advancedbookmarks;

/**
 * A user-defined bookmark folder. Folders form a tree through their parent identifier; the empty identifier
 * {@link #ROOT} designates the implicit "Uncategorized" root.
 * <p>
 * This is a mutable bean so that it can be (de)serialized as JSON.
 *
 * @version $Id$
 * @since 1.0
 */
public class BookmarkFolder
{
    /**
     * Identifier of the implicit root folder, which holds uncategorized bookmarks.
     */
    public static final String ROOT = "";

    private String id;

    private String name;

    private String parent = ROOT;

    private int order;

    /**
     * @return the unique folder identifier
     */
    public String getId()
    {
        return this.id;
    }

    /**
     * @param id the unique folder identifier
     */
    public void setId(String id)
    {
        this.id = id;
    }

    /**
     * @return the display name
     */
    public String getName()
    {
        return this.name;
    }

    /**
     * @param name the display name
     */
    public void setName(String name)
    {
        this.name = name;
    }

    /**
     * @return the parent folder identifier, {@link #ROOT} for a top-level folder
     */
    public String getParent()
    {
        return this.parent;
    }

    /**
     * @param parent the parent folder identifier, {@link #ROOT} for a top-level folder
     */
    public void setParent(String parent)
    {
        this.parent = parent;
    }

    /**
     * @return the position among siblings
     */
    public int getOrder()
    {
        return this.order;
    }

    /**
     * @param order the position among siblings
     */
    public void setOrder(int order)
    {
        this.order = order;
    }
}
