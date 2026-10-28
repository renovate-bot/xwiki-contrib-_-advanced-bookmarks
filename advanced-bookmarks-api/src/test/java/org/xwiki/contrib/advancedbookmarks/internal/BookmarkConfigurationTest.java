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
import java.util.Arrays;
import java.util.List;

import jakarta.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.configuration.ConfigurationSource;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceProvider;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests the validation of the configuration and the page exclusion rules.
 *
 * @version $Id$
 */
@ComponentTest
class BookmarkConfigurationTest
{
    private static final String WIKI = "wiki";

    private static final DocumentReference PAGE = new DocumentReference(WIKI, "Space", "Page");

    private static final List<String> CODE_SPACE = List.of("AdvancedBookmarks", "Code");

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private BookmarkConfiguration configuration;

    @MockComponent
    @Named(BookmarkConfigurationSource.HINT)
    private ConfigurationSource source;

    @MockComponent
    @Named("current")
    private DocumentReferenceResolver<String> resolver;

    @MockComponent
    private EntityReferenceProvider defaultReferences;

    @BeforeEach
    void setUp()
    {
        when(this.defaultReferences.getDefaultReference(EntityType.DOCUMENT))
            .thenReturn(new EntityReference("WebHome", EntityType.DOCUMENT));
        // Local references with dots between the spaces and the page name, in the current wiki. A single name is a
        // page of the space of the base reference, which must be the configuration page.
        when(this.resolver.resolve(anyString(), eq(BookmarkConfigurationSource.DOCUMENT_REFERENCE)))
            .thenAnswer(invocation -> {
                List<String> names = Arrays.asList(invocation.<String>getArgument(0).split("\\."));
                List<String> spaces = names.size() > 1 ? names.subList(0, names.size() - 1) : CODE_SPACE;
                return new DocumentReference(WIKI, spaces, names.get(names.size() - 1));
            });
    }

    @Test
    void usesTheDefaultsWhenNothingIsConfigured()
    {
        assertEquals(BookmarkConfiguration.DEFAULT_PANEL_LIMIT, this.configuration.getPanelLimit());
        assertTrue(this.configuration.isStarEnabled());
        assertFalse(this.configuration.isExcluded(PAGE));
    }

    @Test
    void acceptsPanelLimitsFromOneToTwenty()
    {
        configure(BookmarkConfiguration.PANEL_LIMIT, 1);
        assertEquals(1, this.configuration.getPanelLimit());

        configure(BookmarkConfiguration.PANEL_LIMIT, 20L);
        assertEquals(20, this.configuration.getPanelLimit());
    }

    @Test
    void replacesAnInvalidPanelLimitByTheDefault()
    {
        // 2^32 + 5 would become 5 if it were converted to an int before being checked.
        for (Object value : List.of(0, 21, -3, 4294967301L, "abc")) {
            configure(BookmarkConfiguration.PANEL_LIMIT, value);

            assertEquals(BookmarkConfiguration.DEFAULT_PANEL_LIMIT, this.configuration.getPanelLimit());
        }
        assertEquals(5, this.logCapture.size());
        assertEquals("Invalid panel limit [0] in the Advanced Bookmarks configuration, using [10] instead.",
            this.logCapture.getMessage(0));
        assertEquals("Invalid panel limit [21] in the Advanced Bookmarks configuration, using [10] instead.",
            this.logCapture.getMessage(1));
        assertEquals("Invalid panel limit [-3] in the Advanced Bookmarks configuration, using [10] instead.",
            this.logCapture.getMessage(2));
        assertEquals("Invalid panel limit [4294967301] in the Advanced Bookmarks configuration, using [10] instead.",
            this.logCapture.getMessage(3));
        assertEquals("Invalid panel limit [abc] in the Advanced Bookmarks configuration, using [10] instead.",
            this.logCapture.getMessage(4));
    }

    @Test
    void readsTheStarSettingStoredAsAnIntegerOrABoolean()
    {
        configure(BookmarkConfiguration.STAR_ENABLED, 0);
        assertFalse(this.configuration.isStarEnabled());

        configure(BookmarkConfiguration.STAR_ENABLED, 1);
        assertTrue(this.configuration.isStarEnabled());

        configure(BookmarkConfiguration.STAR_ENABLED, Boolean.FALSE);
        assertFalse(this.configuration.isStarEnabled());
    }

    @Test
    void keepsTheStarWhenItsSettingIsInvalid()
    {
        configure(BookmarkConfiguration.STAR_ENABLED, "off");

        assertTrue(this.configuration.isStarEnabled());
        assertEquals("Invalid star setting [off] in the Advanced Bookmarks configuration, keeping the star.",
            this.logCapture.getMessage(0));
    }

    @Test
    void excludesTheWholeSpaceOfAnExcludedSpaceHomePage()
    {
        configure(BookmarkConfiguration.EXCLUDED_PAGES, List.of("Main.Archives.WebHome"));

        assertTrue(this.configuration.isExcluded(page("Main", "Archives", "WebHome")));
        assertTrue(this.configuration.isExcluded(page("Main", "Archives", "Old")));
        assertTrue(this.configuration.isExcluded(page("Main", "Archives", "2020", "WebHome")));
        assertFalse(this.configuration.isExcluded(page("Main", "WebHome")));
        assertFalse(this.configuration.isExcluded(page("Main", "ArchivesBis", "WebHome")));
        assertFalse(this.configuration.isExcluded(new DocumentReference("other", List.of("Main", "Archives"), "Old")));
    }

    @Test
    void excludesOnlyAnExcludedTerminalPage()
    {
        // A single selected page may be stored as a plain string.
        configure(BookmarkConfiguration.EXCLUDED_PAGES, " Main.Terminal ");

        assertTrue(this.configuration.isExcluded(page("Main", "Terminal")));
        assertFalse(this.configuration.isExcluded(page("Main", "Other")));
        assertFalse(this.configuration.isExcluded(page("Main", "Terminal", "WebHome")));
    }

    @Test
    void resolvesARelativeExcludedPageFromTheConfigurationPage()
    {
        configure(BookmarkConfiguration.EXCLUDED_PAGES, List.of("Terminal"));

        // Whatever the viewed page, the same page is excluded.
        assertFalse(this.configuration.isExcluded(page("Main", "Terminal")));
        assertTrue(this.configuration.isExcluded(new DocumentReference(WIKI, CODE_SPACE, "Terminal")));
    }

    @Test
    void alwaysExcludesThePagesOfTheExtension()
    {
        assertTrue(this.configuration.isExcluded(page("AdvancedBookmarks", "WebHome")));
        assertTrue(this.configuration.isExcluded(page("AdvancedBookmarks", "Code", "Service")));
        assertTrue(this.configuration.isExcluded(page("Panels", "MyBookmarks")));
        assertFalse(this.configuration.isExcluded(page("Panels", "Other")));
        assertFalse(this.configuration.isExcluded(page("Main", "AdvancedBookmarks", "WebHome")));
    }

    @Test
    void ignoresEmptyAndInvalidExcludedPages()
    {
        configure(BookmarkConfiguration.EXCLUDED_PAGES,
            Arrays.asList("", "  ", null, 42, "p".repeat(513), "Space.Page"));

        assertTrue(this.configuration.isExcluded(PAGE));
        // Empty values are what an empty selection may leave: only the three invalid values are reported.
        assertEquals(3, this.logCapture.size());
        for (int i = 0; i < 3; i++) {
            assertEquals("Ignoring an invalid excluded page in the Advanced Bookmarks configuration.",
                this.logCapture.getMessage(i));
        }
    }

    @Test
    void usesOnlyTheFirstHundredExcludedPages()
    {
        List<String> excluded = new ArrayList<>();
        for (int i = 0; i < BookmarkConfiguration.MAX_EXCLUDED_PAGES + 1; i++) {
            excluded.add("Space.Page" + i);
        }
        configure(BookmarkConfiguration.EXCLUDED_PAGES, excluded);

        assertTrue(this.configuration.isExcluded(page("Space", "Page99")));
        assertFalse(this.configuration.isExcluded(page("Space", "Page100")));
        // Reported on each read of the configuration.
        for (int i = 0; i < 2; i++) {
            assertEquals("The Advanced Bookmarks configuration excludes [101] pages: only the first [100] are used.",
                this.logCapture.getMessage(i));
        }
    }

    private void configure(String key, Object value)
    {
        when(this.source.getProperty(key)).thenReturn(value);
    }

    private static DocumentReference page(String... names)
    {
        List<String> list = List.of(names);
        return new DocumentReference(WIKI, list.subList(0, list.size() - 1), list.get(list.size() - 1));
    }
}
