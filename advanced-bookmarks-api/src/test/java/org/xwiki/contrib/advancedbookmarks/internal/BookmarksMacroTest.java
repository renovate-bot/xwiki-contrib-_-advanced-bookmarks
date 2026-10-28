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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.InOrder;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.block.match.ClassBlockMatcher;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.test.LogLevel;
import org.xwiki.test.annotation.BeforeComponent;
import org.xwiki.test.junit5.LogCaptureExtension;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;
import org.xwiki.test.mockito.MockitoComponentManager;
import org.xwiki.wiki.user.UserScope;
import org.xwiki.wiki.user.WikiUserManager;
import org.xwiki.wiki.user.WikiUserManagerException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests the blocks produced by the bookmarks macro for guests, users and administrators.
 *
 * @version $Id$
 */
@ComponentTest
class BookmarksMacroTest
{
    private static final DocumentReference ALICE = ReferenceStubs.page("wiki:XWiki.alice");

    private static final DocumentReference BOB = ReferenceStubs.page("wiki:XWiki.bob");

    private static final String ALICE_NAME = "XWiki.alice";

    private static final String BOB_NAME = "XWiki.bob";

    private static final String CLASS = "class";

    private static final String WIKI = "wiki";

    private static final String CONTAINER = "advanced-bookmarks-manager";

    private static final String USER = "data-user";

    private static final String PANEL_LIMIT = "data-panel-limit";

    private static final String ADMIN = "data-admin";

    @RegisterExtension
    private LogCaptureExtension logCapture = new LogCaptureExtension(LogLevel.WARN);

    @InjectMockComponents
    private BookmarksMacro macro;

    @MockComponent
    private BookmarkRights rights;

    @MockComponent
    private BookmarkReferences references;

    @MockComponent
    private BookmarkRequests requests;

    @MockComponent
    private BookmarkPages pages;

    @MockComponent
    private BookmarkConfiguration configuration;

    @MockComponent
    private ContextualLocalizationManager localization;

    @MockComponent
    private WikiUserManager wikiUserManager;

    @MockComponent
    @Named("jsrx")
    private SkinExtension jsrx;

    @MockComponent
    @Named("ssrx")
    private SkinExtension ssrx;

    @MockComponent
    @Named("jsfx")
    private SkinExtension jsfx;

    @BeforeComponent
    void registerReferences(MockitoComponentManager componentManager) throws Exception
    {
        ReferenceStubs.register(componentManager);
    }

    @BeforeEach
    void setUp() throws Exception
    {
        // A translation is replaced by its key followed by its parameters, so that both are checked.
        when(this.localization.getTranslationPlain(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            Object[] arguments = invocation.getArguments();
            return arguments[0] + Arrays.toString(Arrays.copyOfRange(arguments, 1, arguments.length));
        });
        when(this.rights.getCurrentUser()).thenReturn(ALICE);
        when(this.rights.getCurrentWiki()).thenReturn(WIKI);
        when(this.references.resolveUser(null)).thenReturn(ALICE);
        when(this.rights.canManage(ALICE)).thenReturn(true);
        // Not the default limit, to check that the configured one is passed to the script.
        when(this.configuration.getPanelLimit()).thenReturn(15);
    }

    @Test
    void asksGuestsToLogIn()
    {
        when(this.rights.getCurrentUser()).thenReturn(null);

        List<Block> blocks = execute();

        assertEquals(1, blocks.size());
        assertMessage(blocks.get(0), "box warningmessage", "advancedbookmarks.manager.loginRequired[]");
        verifyNoInteractions(this.jsrx, this.ssrx, this.jsfx);
    }

    @Test
    void displaysTheBookmarksOfTheCurrentUser()
    {
        List<Block> blocks = execute();

        assertEquals(1, blocks.size());
        assertEquals(Map.of(CLASS, CONTAINER, USER, "wiki:XWiki.alice", PANEL_LIMIT, "15"),
            blocks.get(0).getParameters());
        assertEquals(List.of("advancedbookmarks.manager.loading[]"), words(blocks.get(0)));
        InOrder scripts = inOrder(this.jsrx);
        scripts.verify(this.jsrx).use("advancedbookmarks/common.js");
        scripts.verify(this.jsrx).use("advancedbookmarks/manager.js");
        verify(this.ssrx).use("advancedbookmarks/manager.css");
        verifyNoInteractions(this.jsfx);
    }

    @Test
    void letsAnAdministratorManageTheBookmarksOfAnotherUser() throws Exception
    {
        when(this.requests.getParameter(BookmarksMacro.USER_PARAMETER)).thenReturn(BOB_NAME);
        when(this.references.resolveUser(BOB_NAME)).thenReturn(BOB);
        when(this.rights.canManage(BOB)).thenReturn(true);
        when(this.rights.isAdmin()).thenReturn(true);
        when(this.wikiUserManager.getUserScope(WIKI)).thenReturn(UserScope.LOCAL_AND_GLOBAL);
        when(this.pages.getUserName(BOB)).thenReturn("Bob <Doe>");

        List<Block> blocks = execute();

        assertEquals(2, blocks.size());
        assertMessage(blocks.get(0), "box infomessage", "advancedbookmarks.manager.otherUser[Bob <Doe>]");
        assertEquals(Map.of(CLASS, CONTAINER, USER, "wiki:XWiki.bob", PANEL_LIMIT, "15", ADMIN, "true",
            "data-user-scope", "LOCAL_AND_GLOBAL"), blocks.get(1).getParameters());
        verify(this.jsfx).use("uicomponents/suggest/suggestUsersAndGroups.js");
    }

    @Test
    void omitsTheUserScopeWhenItCannotBeRead() throws Exception
    {
        when(this.rights.isAdmin()).thenReturn(true);
        when(this.wikiUserManager.getUserScope(WIKI)).thenThrow(new WikiUserManagerException("failure"));

        List<Block> blocks = execute();

        assertEquals(Map.of(CLASS, CONTAINER, USER, "wiki:XWiki.alice", PANEL_LIMIT, "15", ADMIN, "true"),
            blocks.get(0).getParameters());
        assertEquals("Unable to read the user scope of the wiki: WikiUserManagerException: failure", this.logCapture.getMessage(0));
    }

    @Test
    void refusesUsersThatCannotBeManaged() throws Exception
    {
        when(this.requests.getParameter(BookmarksMacro.USER_PARAMETER)).thenReturn(BOB_NAME);
        when(this.references.resolveUser(BOB_NAME)).thenReturn(BOB);

        assertForbidden();
    }

    @Test
    void refusesInvalidUserReferences() throws Exception
    {
        when(this.requests.getParameter(BookmarksMacro.USER_PARAMETER)).thenReturn("invalid");
        when(this.references.resolveUser("invalid")).thenThrow(new BookmarkInvalidDataException(null));

        assertForbidden();
        verify(this.rights).canManage(null);
    }

    @Test
    void displaysTheBookmarksOfTheUserOfTheProfileToThisUser() throws Exception
    {
        when(this.references.resolveUser(ALICE_NAME)).thenReturn(ALICE);
        when(this.rights.isAdmin()).thenReturn(true);

        List<Block> blocks = execute(ALICE_NAME);

        assertEquals(1, blocks.size());
        // No user picker: the profile only shows the bookmarks of its own user.
        assertEquals(Map.of(CLASS, CONTAINER, USER, "wiki:XWiki.alice", PANEL_LIMIT, "15"),
            blocks.get(0).getParameters());
        verifyNoInteractions(this.jsfx, this.wikiUserManager);
    }

    @Test
    void displaysTheBookmarksOfTheUserOfTheProfileToAnAdministrator() throws Exception
    {
        when(this.references.resolveUser(BOB_NAME)).thenReturn(BOB);
        when(this.rights.canManage(BOB)).thenReturn(true);
        when(this.rights.isAdmin()).thenReturn(true);
        when(this.pages.getUserName(BOB)).thenReturn("Bob");

        List<Block> blocks = execute(BOB_NAME);

        assertEquals(2, blocks.size());
        assertMessage(blocks.get(0), "box infomessage", "advancedbookmarks.manager.otherUser[Bob]");
        assertEquals(Map.of(CLASS, CONTAINER, USER, "wiki:XWiki.bob", PANEL_LIMIT, "15"),
            blocks.get(1).getParameters());
        verifyNoInteractions(this.jsfx, this.wikiUserManager);
    }

    @Test
    void refusesTheBookmarksOfTheUserOfTheProfileToOtherUsers() throws Exception
    {
        when(this.references.resolveUser(BOB_NAME)).thenReturn(BOB);

        assertForbidden(BOB_NAME);
    }

    @Test
    void prefersTheMacroParameterToTheRequestParameter() throws Exception
    {
        // Alice cannot override the user of the profile of Bob from the URL.
        when(this.requests.getParameter(BookmarksMacro.USER_PARAMETER)).thenReturn(ALICE_NAME);
        when(this.references.resolveUser(BOB_NAME)).thenReturn(BOB);

        assertForbidden(BOB_NAME);
        verify(this.references, never()).resolveUser(ALICE_NAME);
    }

    @Test
    void ignoresABlankMacroParameter() throws Exception
    {
        when(this.requests.getParameter(BookmarksMacro.USER_PARAMETER)).thenReturn(BOB_NAME);
        when(this.references.resolveUser(BOB_NAME)).thenReturn(BOB);
        when(this.rights.canManage(BOB)).thenReturn(true);

        List<Block> blocks = execute(" ");

        assertEquals("wiki:XWiki.bob", blocks.get(1).getParameters().get(USER));
    }

    @Test
    void refusesAnInvalidMacroParameter() throws Exception
    {
        when(this.references.resolveUser("invalid")).thenThrow(new BookmarkInvalidDataException(null));

        assertForbidden("invalid");
        verify(this.rights).canManage(null);
    }

    @Test
    void isNotAnInlineMacro()
    {
        assertFalse(this.macro.supportsInlineMode());
    }

    private void assertForbidden()
    {
        assertForbidden(null);
    }

    private void assertForbidden(String user)
    {
        List<Block> blocks = execute(user);

        assertEquals(1, blocks.size());
        assertMessage(blocks.get(0), "box errormessage", "advancedbookmarks.manager.forbidden[]");
        verifyNoInteractions(this.jsrx, this.ssrx, this.jsfx);
        verify(this.pages, never()).getUserName(any());
    }

    private List<Block> execute()
    {
        return execute(null);
    }

    private List<Block> execute(String user)
    {
        BookmarksMacroParameters parameters = new BookmarksMacroParameters();
        parameters.setUser(user);
        assertEquals(user, parameters.getUser());
        return this.macro.execute(parameters, null, null);
    }

    private static void assertMessage(Block block, String cssClass, String text)
    {
        assertEquals(GroupBlock.class, block.getClass());
        assertEquals(Map.of(CLASS, cssClass), block.getParameters());
        assertEquals(List.of(text), words(block));
    }

    private static List<String> words(Block block)
    {
        List<WordBlock> words = block.getBlocks(new ClassBlockMatcher(WordBlock.class), Block.Axes.DESCENDANT);
        return words.stream().map(WordBlock::getWord).toList();
    }
}
