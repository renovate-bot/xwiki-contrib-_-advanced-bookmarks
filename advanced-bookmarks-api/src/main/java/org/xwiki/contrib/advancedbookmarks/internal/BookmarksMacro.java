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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkInvalidDataException;
import org.xwiki.localization.ContextualLocalizationManager;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.GroupBlock;
import org.xwiki.rendering.block.ParagraphBlock;
import org.xwiki.rendering.block.WordBlock;
import org.xwiki.rendering.macro.AbstractMacro;
import org.xwiki.rendering.transformation.MacroTransformationContext;
import org.xwiki.skinx.SkinExtension;
import org.xwiki.wiki.user.WikiUserManager;
import org.xwiki.wiki.user.WikiUserManagerException;

/**
 * The {@code bookmarks} macro, which displays the bookmark management interface. It only produces rendering blocks,
 * which the renderer escapes: the interface itself is built in the browser by {@code advancedbookmarks/manager.js},
 * from the data attributes of the container.
 * <p>
 * The managed user is the one given in the {@code user} macro parameter (the user profile tab gives the user of the
 * profile), else the one given in the {@code user} request parameter (used by administrators), else the current user.
 * The access is always checked on the server.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named("bookmarks")
@Singleton
public class BookmarksMacro extends AbstractMacro<BookmarksMacroParameters>
{
    /**
     * The request parameter used by administrators to manage the bookmarks of another user.
     */
    public static final String USER_PARAMETER = "user";

    /**
     * The class of the container filled by the JavaScript.
     */
    public static final String CONTAINER_CLASS = "advanced-bookmarks-manager";

    private static final String CLASS = "class";

    private static final String TRANSLATION_PREFIX = "advancedbookmarks.manager.";

    /**
     * The user picker of XWiki, offered to administrators.
     */
    private static final String USER_PICKER = "uicomponents/suggest/suggestUsersAndGroups.js";

    @Inject
    private BookmarkRights rights;

    @Inject
    private BookmarkReferences references;

    @Inject
    private BookmarkRequests requests;

    @Inject
    private BookmarkPages pages;

    @Inject
    private BookmarkConfiguration configuration;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private ContextualLocalizationManager localization;

    @Inject
    private WikiUserManager wikiUserManager;

    @Inject
    @Named("jsrx")
    private SkinExtension jsrx;

    @Inject
    @Named("ssrx")
    private SkinExtension ssrx;

    @Inject
    @Named("jsfx")
    private SkinExtension jsfx;

    @Inject
    private Logger logger;

    /**
     * Create the macro.
     */
    public BookmarksMacro()
    {
        super("Bookmarks", "Manage the bookmarks of the current user: folders, order and panel selection.",
            BookmarksMacroParameters.class);
        setDefaultCategories(Set.of(DEFAULT_CATEGORY_NAVIGATION));
    }

    @Override
    public boolean supportsInlineMode()
    {
        return false;
    }

    @Override
    public List<Block> execute(BookmarksMacroParameters parameters, String content,
        MacroTransformationContext context)
    {
        DocumentReference currentUser = this.rights.getCurrentUser();
        if (currentUser == null) {
            return List.of(message("warningmessage", "loginRequired"));
        }
        // A user set by the page (the profile tab) wins over the request: a profile only shows the bookmarks of its own
        // user, whatever the URL says.
        String fixedUser = parameters.getUser();
        boolean isUserFixed = fixedUser != null && !fixedUser.isBlank();
        DocumentReference owner;
        try {
            owner = this.references
                .resolveUser(isUserFixed ? fixedUser : this.requests.getParameter(USER_PARAMETER));
        } catch (BookmarkInvalidDataException e) {
            owner = null;
        }
        if (!this.rights.canManage(owner)) {
            return List.of(message("errormessage", "forbidden"));
        }

        // The shared helpers must be loaded before the script that requires them.
        this.jsrx.use("advancedbookmarks/common.js");
        this.jsrx.use("advancedbookmarks/manager.js");
        this.ssrx.use("advancedbookmarks/manager.css");

        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(CLASS, CONTAINER_CLASS);
        attributes.put("data-user", this.serializer.serialize(owner));
        attributes.put("data-panel-limit", String.valueOf(this.configuration.getPanelLimit()));
        // The user picker opens the current page for another user, which is pointless when the user is fixed.
        if (!isUserFixed && this.rights.isAdmin()) {
            this.jsfx.use(USER_PICKER);
            attributes.put("data-admin", "true");
            String userScope = getUserScope();
            if (userScope != null) {
                attributes.put("data-user-scope", userScope);
            }
        }
        // Displayed until the script replaces it, and kept if scripts are disabled.
        Block manager = new GroupBlock(List.of(paragraph(translate("loading"))), attributes);

        if (owner.equals(currentUser)) {
            return List.of(manager);
        }
        return List.of(message("infomessage", "otherUser", this.pages.getUserName(owner)), manager);
    }

    /**
     * Administrators look for users with the user picker of XWiki, which needs to know whether the users of the
     * current wiki are local, global or both. Without it, the picker only offers local users.
     */
    private String getUserScope()
    {
        try {
            return this.wikiUserManager.getUserScope(this.rights.getCurrentWiki()).name();
        } catch (WikiUserManagerException e) {
            this.logger.warn("Unable to read the user scope of the wiki: {}", e.getMessage());
            return null;
        }
    }

    private Block message(String type, String key, Object... parameters)
    {
        return new GroupBlock(List.of(paragraph(translate(key, parameters))), Map.of(CLASS, "box " + type));
    }

    private String translate(String key, Object... parameters)
    {
        return this.localization.getTranslationPlain(TRANSLATION_PREFIX + key, parameters);
    }

    /**
     * The text is plain: a single word block is escaped by the renderer and never interpreted as wiki syntax.
     */
    private static Block paragraph(String text)
    {
        return new ParagraphBlock(List.of(new WordBlock(text)));
    }
}
