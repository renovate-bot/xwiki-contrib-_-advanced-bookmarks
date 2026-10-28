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

import javax.inject.Provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceProvider;
import org.xwiki.rendering.syntax.Syntax;
import org.xwiki.test.LogLevel;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the page information used to display bookmarks.
 *
 * @version $Id$
 */
@ComponentTest
class BookmarkPagesTest
{
    private static final DocumentReference PAGE = new DocumentReference("wiki", "Space", "Page");

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private BookmarkPages pages;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    private XWikiContext xcontext;

    private XWiki xwiki;

    @MockComponent
    private EntityReferenceProvider defaultReferences;

    @BeforeEach
    void setUp()
    {
        when(this.defaultReferences.getDefaultReference(EntityType.DOCUMENT))
            .thenReturn(new EntityReference("WebHome", EntityType.DOCUMENT));
        this.xcontext = mock(XWikiContext.class);
        this.xwiki = mock(XWiki.class);
        when(this.contextProvider.get()).thenReturn(this.xcontext);
        when(this.xcontext.getWiki()).thenReturn(this.xwiki);
    }

    @Test
    void checksWhetherAPageExists() throws Exception
    {
        when(this.xwiki.exists(PAGE, this.xcontext)).thenReturn(false);

        assertFalse(this.pages.exists(PAGE));
    }

    @Test
    void assumesAPageExistsWhenThisCannotBeChecked() throws Exception
    {
        when(this.xwiki.exists(PAGE, this.xcontext)).thenThrow(new XWikiException());

        assertTrue(this.pages.exists(PAGE));
        assertEquals("Unable to check whether a bookmarked page exists: XWikiException: Error number 0 in 0",
            this.logCapture.getMessage(0));
    }

    @Test
    void computesTheViewURL()
    {
        when(this.xwiki.getURL(PAGE, "view", this.xcontext)).thenReturn("/bin/view/Space/Page");

        assertEquals("/bin/view/Space/Page", this.pages.getURL(PAGE));
    }

    @Test
    void computesTheUserName()
    {
        when(this.xwiki.getPlainUserName(PAGE, this.xcontext)).thenReturn("Alice Doe");

        assertEquals("Alice Doe", this.pages.getUserName(PAGE));
    }

    @Test
    void computesThePlainTitle() throws Exception
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getRenderedTitle(Syntax.PLAIN_1_0, this.xcontext)).thenReturn("Title");
        when(this.xwiki.getDocument(PAGE, this.xcontext)).thenReturn(document);

        assertEquals("Title", this.pages.getPlainTitle(PAGE));
    }

    @Test
    void displaysTheTitleOfAnExistingPage() throws Exception
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getRenderedTitle(Syntax.PLAIN_1_0, this.xcontext)).thenReturn(" Title ");
        when(this.xwiki.getDocument(PAGE, this.xcontext)).thenReturn(document);

        assertEquals("Title", this.pages.getDisplayTitle(PAGE, true));
    }

    @Test
    void displaysTheNameOfAPageWithoutTitleOrMissing() throws Exception
    {
        XWikiDocument document = mock(XWikiDocument.class);
        when(document.getRenderedTitle(Syntax.PLAIN_1_0, this.xcontext)).thenReturn(" ");
        when(this.xwiki.getDocument(PAGE, this.xcontext)).thenReturn(document);

        assertEquals("Page", this.pages.getDisplayTitle(PAGE, true));
        assertEquals("Space", this.pages.getDisplayTitle(new DocumentReference("wiki", "Space", "WebHome"), false));
        verify(this.xwiki, times(1)).getDocument(any(DocumentReference.class), any(XWikiContext.class));
    }

    @Test
    void returnsAnEmptyTitleWhenThePageCannotBeLoaded() throws Exception
    {
        when(this.xwiki.getDocument(PAGE, this.xcontext)).thenThrow(new XWikiException());

        assertEquals("", this.pages.getPlainTitle(PAGE));
        assertEquals("Unable to compute the title of a bookmarked page: XWikiException: Error number 0 in 0",
            this.logCapture.getMessage(0));
    }
}
