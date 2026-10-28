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

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.bridge.event.DocumentDeletedEvent;
import org.xwiki.bridge.event.WikiDeletedEvent;
import org.xwiki.component.annotation.Component;
import org.xwiki.contrib.advancedbookmarks.BookmarkStore;
import org.xwiki.contrib.advancedbookmarks.BookmarkStoreException;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.observation.event.AbstractLocalEventListener;
import org.xwiki.observation.event.Event;
import org.xwiki.refactoring.event.DocumentRenamedEvent;

import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Keeps the stored bookmarks consistent with the wiki: follows renamed pages, and erases the bookmarks of deleted
 * users and deleted wikis. Deleted pages are left in the bookmarks, which display them as deleted.
 * <p>
 * Only local events are handled: the store is shared, so a remote event has already been handled by the node that
 * produced it.
 *
 * @version $Id$
 * @since 1.0
 */
@Component
@Named(BookmarkEventListener.NAME)
@Singleton
public class BookmarkEventListener extends AbstractLocalEventListener
{
    /**
     * The name of this listener.
     */
    public static final String NAME = "org.xwiki.contrib.advancedbookmarks.internal.BookmarkEventListener";

    @Inject
    private BookmarkStore store;

    @Inject
    private EntityReferenceSerializer<String> serializer;

    @Inject
    private Logger logger;

    /**
     * Default constructor.
     */
    public BookmarkEventListener()
    {
        super(NAME, new DocumentRenamedEvent(), new DocumentDeletedEvent(), new WikiDeletedEvent());
    }

    @Override
    public void processLocalEvent(Event event, Object source, Object data)
    {
        try {
            if (event instanceof DocumentRenamedEvent renamedEvent) {
                onPageRenamed(renamedEvent.getSourceReference(), renamedEvent.getTargetReference());
            } else if (event instanceof DocumentDeletedEvent && source instanceof XWikiDocument document) {
                onDocumentDeleted(document);
            } else if (event instanceof WikiDeletedEvent wikiEvent) {
                this.store.deleteWiki(wikiEvent.getWikiId());
                this.logger.info("Deleted the bookmarks of the deleted wiki [{}].", wikiEvent.getWikiId());
            }
        } catch (BookmarkStoreException e) {
            // Never make a rename or a deletion fail because of bookmarks.
            this.logger.error("Failed to update the bookmarks after the event [{}].", event, e);
        }
    }

    private void onPageRenamed(DocumentReference oldPage, DocumentReference newPage) throws BookmarkStoreException
    {
        if (oldPage == null || newPage == null) {
            return;
        }
        String wiki = oldPage.getWikiReference().getName();
        if (!wiki.equals(newPage.getWikiReference().getName())) {
            // Bookmarks are isolated per wiki: a page moved to another wiki is displayed as deleted.
            this.logger.debug("Bookmarks are not updated for a page moved to another wiki.");
            return;
        }
        int count =
            this.store.replacePage(wiki, this.serializer.serialize(oldPage), this.serializer.serialize(newPage));
        if (count > 0) {
            this.logger.debug("Updated the bookmarks of [{}] users after a page rename.", count);
        }
    }

    private void onDocumentDeleted(XWikiDocument document) throws BookmarkStoreException
    {
        // The deleted document is empty: its content, and thus its user object, is in the original document.
        XWikiDocument original = document.getOriginalDocument();
        if (BookmarkUsers.isUserDocument(original)) {
            this.store.deleteUser(this.serializer.serialize(original.getDocumentReference()));
            this.logger.info("Deleted the bookmarks of the deleted user [{}].", original.getDocumentReference());
        }
    }
}
