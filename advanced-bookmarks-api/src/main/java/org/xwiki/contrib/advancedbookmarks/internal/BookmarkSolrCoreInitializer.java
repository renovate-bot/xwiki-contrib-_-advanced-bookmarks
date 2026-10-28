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
package org.xwiki.contrib.advancedbookmarks.internal;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.search.solr.AbstractSolrCoreInitializer;
import org.xwiki.search.solr.SolrException;

/**
 * Creates the schema for the farm-wide Advanced Bookmarks Solr core.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("advancedbookmarks")
@Singleton
public class BookmarkSolrCoreInitializer extends AbstractSolrCoreInitializer
{
    private static final long CURRENT_VERSION = 1000000;

    @Override
    protected long getVersion()
    {
        return CURRENT_VERSION;
    }

    @Override
    protected void createSchema() throws SolrException
    {
        addStringField(SolrBookmarkStore.WIKI_FIELD, false, false);
        addStringField(SolrBookmarkStore.USER_FIELD, false, false);
        addField(SolrBookmarkStore.DATA_FIELD, "string", false, SOLR_FIELD_INDEXED, false);
        addStringField(SolrBookmarkStore.PAGES_FIELD, true, false);
    }

    @Override
    protected void migrateSchema(long currentVersion) throws SolrException
    {
        // There is no previous schema to migrate from in the initial release.
    }
}
