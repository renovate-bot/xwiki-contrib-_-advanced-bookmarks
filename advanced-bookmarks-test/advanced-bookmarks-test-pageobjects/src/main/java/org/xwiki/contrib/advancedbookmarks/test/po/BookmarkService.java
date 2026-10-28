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

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.BaseElement;

/**
 * Calls the JSON service of Advanced Bookmarks ({@code AdvancedBookmarks.Code.Service}) from the browser, with the
 * session and the CSRF token of the user currently logged in, exactly like the scripts of the extension do. A page of
 * the wiki must be displayed, since the CSRF token is read from it.
 *
 * @version $Id$
 */
public class BookmarkService extends BaseElement
{
    /**
     * The status of a successful call.
     */
    public static final long OK = 200;

    private static final String PAGE = "page";

    private static final String USER = "user";

    private static final String DATA = "data";

    private static final String VERSION = "version";

    private static final String MODE = "mode";

    private static final Duration SCRIPT_TIMEOUT = Duration.ofMinutes(2);

    // The response body is parsed in the browser so that Selenium returns it as maps, lists, strings and longs.
    private static final String CALL_SCRIPT = String.join("\n",
        "var parameters = arguments[1], post = arguments[2], file = arguments[3];",
        "var done = arguments[arguments.length - 1];",
        // The test framework adds its own form_token to the URL: dropped, so that the parameters of a GET are not
        // appended after a second '?' and a POST only carries the token of the user, like the extension scripts.
        "var url = new URL(arguments[0], window.location.href);",
        "url.search = '';",
        "var options = {credentials: 'same-origin', headers: {'Accept': 'application/json'}};",
        "if (post) {",
        "  var token = document.documentElement.getAttribute('data-xwiki-form-token')",
        "    || (document.querySelector('meta[name=\"form_token\"]') || {}).content || '';",
        "  var body = file === null ? new URLSearchParams() : new FormData();",
        "  Object.keys(parameters).forEach(function(key) { body.append(key, parameters[key]); });",
        "  if (file !== null) {",
        "    body.append('file', new Blob([file], {type: 'application/json'}), 'bookmarks.json');",
        "  }",
        "  body.append('form_token', token);",
        "  options.method = 'POST';",
        "  options.body = body;",
        "} else {",
        "  Object.keys(parameters).forEach(function(key) { url.searchParams.append(key, parameters[key]); });",
        "}",
        "fetch(url, options).then(function(response) {",
        "  return response.text().then(function(text) {",
        "    var json = null;",
        "    try { json = JSON.parse(text); } catch (e) { json = null; }",
        "    done({status: response.status, json: json});",
        "  });",
        "}, function(error) { done({status: 0, json: null}); });");

    private final String serviceURL;

    /**
     * @param setup the test utilities, used to compute the URL of the service
     */
    public BookmarkService(TestUtils setup)
    {
        DocumentReference service =
            new DocumentReference(setup.getCurrentWiki(), Arrays.asList("AdvancedBookmarks", "Code"), "Service");
        this.serviceURL = setup.getURL(service, "get", "");
    }

    /**
     * @param page the page to add or remove
     * @return the response; on success, {@code bookmarked} is the new state
     */
    public Response toggle(String page)
    {
        return post(parameters("toggle", PAGE, page), null);
    }

    /**
     * @param page the page to check
     * @return the response, with {@code bookmarked} and {@code bookmarkable}
     */
    public Response isBookmarked(String page)
    {
        return get(parameters("isBookmarked", PAGE, page));
    }

    /**
     * @param user the owner of the bookmarks, empty for the current user
     * @return the response, with {@code user}, {@code version}, {@code folders} and {@code bookmarks}
     */
    public Response getBookmarks(String user)
    {
        return get(parameters("getBookmarks", USER, user));
    }

    /**
     * @param user the owner of the bookmarks, empty for the current user
     * @param data the bookmarks, as JSON with the {@code folders} and {@code bookmarks} properties
     * @param version the version read before the change
     * @return the response, with the new {@code version}
     */
    public Response save(String user, String data, String version)
    {
        return post(parameters("save", USER, user, DATA, data, VERSION, version), null);
    }

    /**
     * @param user the owner of the bookmarks, empty for the current user
     * @return the response, with the export file as {@code content}
     */
    public Response exportBookmarks(String user)
    {
        return get(parameters("export", USER, user));
    }

    /**
     * @param user the owner of the bookmarks, empty for the current user
     * @param content the export file
     * @param mode {@code merge} or {@code replace}
     * @param version the version read before the import
     * @return the response, with {@code imported} and {@code skipped}
     */
    public Response importBookmarks(String user, String content, String mode, String version)
    {
        return post(
            parameters("import", USER, user, DATA, content, MODE, mode, VERSION, version), null);
    }

    /**
     * @return the response, with the export file of the whole wiki as {@code content}
     */
    public Response exportWiki()
    {
        return get(parameters("exportWiki"));
    }

    /**
     * @param content the export file of a whole wiki, sent as a file
     * @param mode {@code merge} or {@code replace}
     * @return the response of the start of the import
     */
    public Response startWikiImport(String content, String mode)
    {
        return post(parameters("startWikiImport", MODE, mode), content);
    }

    /**
     * @return the response, with the {@code state} of the last wiki import and its counters
     */
    public Response getWikiImportStatus()
    {
        return get(parameters("wikiImportStatus"));
    }

    /**
     * Waits until the bookmarks of the current user satisfy a condition, for instance because the page that changes
     * them saves automatically.
     *
     * @param condition the condition on the {@code getBookmarks} response
     */
    public void waitUntilBookmarks(Predicate<Response> condition)
    {
        getDriver().waitUntilCondition(driver -> condition.test(getBookmarks("")));
    }

    /**
     * Waits until the last wiki import is finished.
     *
     * @return the response, with the {@code state} of the import and its counters
     */
    public Response waitUntilWikiImportFinished()
    {
        getDriver().waitUntilCondition(driver -> "finished".equals(getWikiImportStatus().get("state")));
        return getWikiImportStatus();
    }

    private Response get(Map<String, String> parameters)
    {
        return call(parameters, false, null);
    }

    private Response post(Map<String, String> parameters, String file)
    {
        return call(parameters, true, file);
    }

    @SuppressWarnings("unchecked")
    private Response call(Map<String, String> parameters, boolean post, String file)
    {
        // Large saves and imports take longer than the default script timeout of the driver.
        getDriver().manage().timeouts().scriptTimeout(SCRIPT_TIMEOUT);
        Map<String, Object> result =
            (Map<String, Object>) getDriver().executeAsyncScript(CALL_SCRIPT, this.serviceURL, parameters, post, file);
        Object json = result.get("json");
        return new Response((Long) result.get("status"),
            json instanceof Map ? (Map<String, Object>) json : Map.of());
    }

    private static Map<String, String> parameters(String operation, String... keysAndValues)
    {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("operation", operation);
        for (int i = 0; i < keysAndValues.length; i += 2) {
            parameters.put(keysAndValues[i], keysAndValues[i + 1]);
        }
        return parameters;
    }

    /**
     * The response of the service.
     *
     * @param status the HTTP status
     * @param json the parsed JSON body, empty when the body is not a JSON object
     * @version $Id$
     */
    public record Response(long status, Map<String, Object> json)
    {
        /**
         * @param key a key of the JSON body
         * @return its value, {@code null} if missing
         */
        public Object get(String key)
        {
            return this.json.get(key);
        }

        /**
         * @return the bookmarks of a {@code getBookmarks} response
         */
        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> getBookmarkList()
        {
            return (List<Map<String, Object>>) this.json.getOrDefault("bookmarks", List.of());
        }

        /**
         * @return the folders of a {@code getBookmarks} response
         */
        @SuppressWarnings("unchecked")
        public List<Map<String, Object>> getFolderList()
        {
            return (List<Map<String, Object>>) this.json.getOrDefault("folders", List.of());
        }

        /**
         * @return the referenced pages of the bookmarks of a {@code getBookmarks} response, in order
         */
        public List<String> getPages()
        {
            return getBookmarkList().stream().map(bookmark -> (String) bookmark.get(PAGE)).toList();
        }
    }
}
