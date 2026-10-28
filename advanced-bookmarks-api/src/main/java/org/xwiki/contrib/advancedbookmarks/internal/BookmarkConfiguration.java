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

import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceProvider;
import org.xwiki.model.reference.LocalDocumentReference;

/**
 * The settings of Advanced Bookmarks for the current wiki, validated: a missing or invalid value is replaced by its
 * default, so that a configuration mistake never prevents pages from being displayed.
 *
 * @version $Id$
 * @since 1.0
 */
@Component(roles = BookmarkConfiguration.class)
@Singleton
public class BookmarkConfiguration
{
    /**
     * Number of bookmarks of the panel when the wiki does not configure it.
     */
    public static final int DEFAULT_PANEL_LIMIT = 10;

    /**
     * Highest panel limit an administrator may configure.
     */
    public static final int MAX_PANEL_LIMIT = 20;

    /**
     * Maximum number of excluded pages taken into account.
     */
    public static final int MAX_EXCLUDED_PAGES = 100;

    /**
     * Name of the panel limit property.
     */
    public static final String PANEL_LIMIT = "panelLimit";

    /**
     * Name of the property enabling the star.
     */
    public static final String STAR_ENABLED = "starEnabled";

    /**
     * Name of the excluded pages property.
     */
    public static final String EXCLUDED_PAGES = "excludedPages";

    /**
     * The pages of the extension itself are never bookmarked: they are tools, not content.
     */
    private static final String EXTENSION_SPACE = "AdvancedBookmarks";

    private static final LocalDocumentReference PANEL = new LocalDocumentReference("Panels", "MyBookmarks");

    @Inject
    @Named(BookmarkConfigurationSource.HINT)
    private ConfigurationSource source;

    @Inject
    @Named("current")
    private DocumentReferenceResolver<String> pageResolver;

    @Inject
    private EntityReferenceProvider defaultReferences;

    @Inject
    private Logger logger;

    /**
     * @return the maximum number of bookmarks of the panel, between 1 and {@value #MAX_PANEL_LIMIT}
     */
    public int getPanelLimit()
    {
        Object value = this.source.getProperty(PANEL_LIMIT);
        if (value == null) {
            return DEFAULT_PANEL_LIMIT;
        }
        // Compared as a long, so that a huge value is not wrapped into the valid range.
        if (value instanceof Number number && number.longValue() >= 1 && number.longValue() <= MAX_PANEL_LIMIT) {
            return number.intValue();
        }
        this.logger.warn("Invalid panel limit [{}] in the Advanced Bookmarks configuration, using [{}] instead.",
            value, DEFAULT_PANEL_LIMIT);
        return DEFAULT_PANEL_LIMIT;
    }

    /**
     * @return {@code true} if the star is displayed in the action bar of the pages, which is the default
     */
    public boolean isStarEnabled()
    {
        Object value = this.source.getProperty(STAR_ENABLED);
        if (value == null) {
            return true;
        }
        // Boolean properties of XWiki objects are stored as integers.
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        if (value instanceof Boolean enabled) {
            return enabled;
        }
        this.logger.warn("Invalid star setting [{}] in the Advanced Bookmarks configuration, keeping the star.",
            value);
        return true;
    }

    /**
     * A page is excluded when it is one of the pages of the extension, when it is an excluded page or when it is a
     * child of an excluded space home page (such as {@code Main.Archives.WebHome}, which excludes the whole
     * {@code Main.Archives} space).
     *
     * @param page a page
     * @return {@code true} if the page may not be added to the bookmarks
     */
    public boolean isExcluded(DocumentReference page)
    {
        if (isExtensionPage(page)) {
            return true;
        }
        String spaceHomeName = this.defaultReferences.getDefaultReference(EntityType.DOCUMENT).getName();
        for (DocumentReference excluded : getExcludedPages()) {
            if (page.equals(excluded) || (spaceHomeName.equals(excluded.getName())
                && page.hasParent(excluded.getLastSpaceReference()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExtensionPage(DocumentReference page)
    {
        return EXTENSION_SPACE.equals(page.getSpaceReferences().get(0).getName())
            || PANEL.equals(page.getLocalDocumentReference());
    }

    private List<DocumentReference> getExcludedPages()
    {
        Object value = this.source.getProperty(EXCLUDED_PAGES);
        List<?> values;
        if (value instanceof List<?> list) {
            values = list;
        } else {
            values = value == null ? List.of() : List.of(value);
        }
        if (values.size() > MAX_EXCLUDED_PAGES) {
            this.logger.warn("The Advanced Bookmarks configuration excludes [{}] pages: only the first [{}] are used.",
                values.size(), MAX_EXCLUDED_PAGES);
            values = values.subList(0, MAX_EXCLUDED_PAGES);
        }
        List<DocumentReference> pages = new ArrayList<>();
        for (Object excluded : values) {
            String reference = excluded instanceof String text ? text : null;
            if (BookmarkReferences.isValidReference(reference)) {
                // Like any page property, a relative value is relative to the page holding it, never to the viewed
                // page: otherwise the exclusion would change from one page to another.
                pages.add(this.pageResolver.resolve(reference.trim(), BookmarkConfigurationSource.DOCUMENT_REFERENCE));
            } else if (reference == null || !reference.isBlank()) {
                // An empty value is what an empty selection may leave: it is not worth a warning.
                this.logger.warn("Ignoring an invalid excluded page in the Advanced Bookmarks configuration.");
            }
        }
        return pages;
    }
}
