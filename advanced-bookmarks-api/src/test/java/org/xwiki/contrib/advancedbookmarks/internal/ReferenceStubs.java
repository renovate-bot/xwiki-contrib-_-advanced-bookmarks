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

import org.xwiki.component.util.DefaultParameterizedType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.test.mockito.MockitoComponentManager;

/**
 * Simplified reference resolution and serialization for tests: {@code wiki:Space.Page}, where the wiki defaults to
 * {@link #WIKI} and the space to the base reference passed to the resolver, or {@code Main}.
 *
 * @version $Id$
 */
public final class ReferenceStubs
{
    /**
     * The current wiki of the tests.
     */
    public static final String WIKI = "wiki";

    private ReferenceStubs()
    {
    }

    /**
     * @param reference a reference such as {@code wiki:Space.Page}, {@code Space.Page} or {@code Page}
     * @return the parsed reference
     */
    public static DocumentReference page(String reference)
    {
        return parse(reference, "Main");
    }

    /**
     * Register a simplified current resolver, default serializer, local serializer (without the wiki) and compact
     * serializer (without the current wiki). Plain implementations are used rather than mocks because tests resolve
     * thousands of references, which is very slow with Mockito.
     *
     * @param componentManager the component manager of the test
     * @throws Exception if the components cannot be registered
     */
    public static void register(MockitoComponentManager componentManager) throws Exception
    {
        DocumentReferenceResolver<String> resolver = (reference, parameters) -> parse(reference,
            parameters.length > 0 && parameters[0] instanceof EntityReference base ? base.getName() : "Main");
        EntityReferenceSerializer<String> serializer =
            (reference, parameters) -> serialize(new DocumentReference(reference));
        componentManager.registerComponent(
            new DefaultParameterizedType(null, DocumentReferenceResolver.class, String.class), "current", resolver);
        componentManager.registerComponent(
            new DefaultParameterizedType(null, EntityReferenceSerializer.class, String.class), "default", serializer);
        EntityReferenceSerializer<String> localSerializer =
            (reference, parameters) -> serialize(new DocumentReference(reference)).replaceFirst("^[^:]*:", "");
        componentManager.registerComponent(
            new DefaultParameterizedType(null, EntityReferenceSerializer.class, String.class), "local",
            localSerializer);
        EntityReferenceSerializer<String> compactWikiSerializer =
            (reference, parameters) -> serialize(new DocumentReference(reference)).replaceFirst("^" + WIKI + ":", "");
        componentManager.registerComponent(
            new DefaultParameterizedType(null, EntityReferenceSerializer.class, String.class), "compactwiki",
            compactWikiSerializer);
    }

    /**
     * @param reference a document reference
     * @return its serialization, {@code wiki:Space.Page}
     */
    public static String serialize(DocumentReference reference)
    {
        return reference.getWikiReference().getName() + ':' + reference.getLastSpaceReference().getName() + '.'
            + reference.getName();
    }

    private static DocumentReference parse(String reference, String defaultSpace)
    {
        String wiki = WIKI;
        String local = reference;
        int colon = reference.indexOf(':');
        if (colon >= 0) {
            wiki = reference.substring(0, colon);
            local = reference.substring(colon + 1);
        }
        int dot = local.lastIndexOf('.');
        String space = dot >= 0 ? local.substring(0, dot) : defaultSpace;
        return new DocumentReference(wiki, space, local.substring(dot + 1));
    }
}
