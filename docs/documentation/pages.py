# ---------------------------------------------------------------------------
# See the NOTICE file distributed with this work for additional
# information regarding copyright ownership.
#
# This is free software; you can redistribute it and/or modify it
# under the terms of the GNU Lesser General Public License as
# published by the Free Software Foundation; either version 2.1 of
# the License, or (at your option) any later version.
#
# This software is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this software; if not, write to the Free
# Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
# 02110-1301 USA, or see the FSF site: http://www.fsf.org.
# ---------------------------------------------------------------------------
"""Documentation pages of Advanced Bookmarks on www.xwiki.org, as the page set read by the docpages.py tool of
https://github.com/xwiki/xwiki-dev-llm (skill xwiki-doc-writing): lint, save, pin and verify."""

EXT = 'xwiki:org.xwiki.contrib.advancedbookmarks:advanced-bookmarks-ui'
EXT_PAGE = 'extensions:Extension.Advanced Bookmarks.WebHome'
USER = 'documentation.extensions.user.advanced-bookmarks'
ADMIN = 'documentation.extensions.admin.advanced-bookmarks'
DEV = 'documentation.extensions.dev.advanced-bookmarks'


def fill(text):
    """Replace the @NAME@ placeholders: plain strings keep the {{macro}} braces of the wiki syntax intact, which an
    f-string would halve."""
    for name, value in (('EXT_PAGE', EXT_PAGE), ('USER', USER), ('ADMIN', ADMIN), ('DEV', DEV)):
        text = text.replace('@' + name + '@', value)
    return text.strip('\n')


def page(root, name, title, type, target, content, faq='', related='', attachments=()):
    space = root.split('.') + ([name] if name else [])
    return dict(space=space, page='WebHome', title=title, type=type, target=target, ext=EXT,
                content=fill(content), faq=fill(faq), related=fill(related), attachments=list(attachments))


ALL = [
    # ----------------------------------------------------------------- Users
    page(USER, None, 'Advanced Bookmarks', 'explanation', 'user', """
[[Advanced Bookmarks>>doc:@EXT_PAGE@]] keeps your bookmarks of wiki pages with your account rather than in your browser, so you find them on any computer. Your bookmarks are private: only you and the administrators of the wiki can see them.

* [[Open Your Bookmarks>>doc:@USER@.open.WebHome]] to see all of them.
* [[Add a Page to Your Bookmarks>>doc:@USER@.add-page.WebHome]] with the star of its action bar.
* [[Organize Your Bookmarks in Folders>>doc:@USER@.organize-folders.WebHome]] and subfolders.
* [[Select the Bookmarks of the Panel>>doc:@USER@.select-panel.WebHome]] to reach them from every page.
* [[Export Your Bookmarks>>doc:@USER@.export.WebHome]] to a file, and [[Import Bookmarks from a File>>doc:@USER@.import-file.WebHome]].
""", faq="""
== What happens to a bookmark when its page is renamed or deleted? ==

A renamed or moved page stays in your bookmarks under its new name. A deleted page stays in your bookmarks, struck through, until you remove it.

== Why is there no star on a page? ==

The star is only shown to logged-in users, on the pages that can be bookmarked: administrators can hide it or [[exclude pages>>doc:@ADMIN@.settings.WebHome]].

== How many bookmarks can I keep? ==

The "Space used" indicator of your bookmarks page shows how much space is left. The [[limits>>doc:@ADMIN@.limits.WebHome]] give the details.

== Are my bookmarks the same on every wiki of a farm? ==

No, each wiki keeps its own bookmarks.
""", related="""
* [[Advanced Bookmarks (for Administrators)>>doc:@ADMIN@.WebHome]]
* [[Advanced Bookmarks (for Developers)>>doc:@DEV@.WebHome]]
"""),

    page(USER, 'open', 'Open Your Bookmarks', 'howto', 'user', """
Open the page listing all your [[bookmarks>>doc:@USER@.WebHome]], where you organize them.

1. In the "My bookmarks" Panel, click "View all my bookmarks".(((
{{image reference="panel-view-all.png" size="medium" alt="View all my bookmarks link of the My bookmarks panel"/}}
)))
1. Your folders and bookmarks are displayed:(((
{{image reference="bookmarks-page.png" size="extra" alt="Bookmarks page listing folders and bookmarks"/}}
)))
""", faq="""
== The "My bookmarks" Panel is not displayed. Where are my bookmarks? ==

Open the "Bookmarks" tab of your user profile, which displays the same page. Your administrator can also add the Panel to the wiki.

== Can I display my bookmarks in another page? ==

Yes, insert the ##bookmarks## macro in the page.
""", attachments=['panel-view-all.png', 'bookmarks-page.png']),

    page(USER, 'add-page', 'Add a Page to Your Bookmarks', 'howto', 'user', """
Add the current page to your [[bookmarks>>doc:@USER@.WebHome]] in one click.

1. Open the page to bookmark.
1. Click the star of the action bar.(((
{{image reference="star-button.png" size="large" alt="Star in the action bar of a page"/}}
)))
1. The star is filled, and a message confirms that the page was added to your bookmarks, in "Uncategorized":(((
{{image reference="star-added.png" size="large" alt="Filled star and confirmation message"/}}
)))
""", faq="""
== How do I remove a page from my bookmarks? ==

Click its filled star again, or remove it from [[your bookmarks page>>doc:@USER@.open.WebHome]].
""", attachments=['star-button.png', 'star-added.png']),

    page(USER, 'organize-folders', 'Organize Your Bookmarks in Folders', 'howto', 'user', """
Group your [[bookmarks>>doc:@USER@.WebHome]] in folders, in the order you choose. Changes are saved automatically.

1. [[Open your bookmarks>>doc:@USER@.open.WebHome]].
1. Click "New folder".(((
{{image reference="new-folder-button.png" size="large" alt="New folder button of the bookmarks page"/}}
)))
1. Type the name of the folder and press Enter.(((
{{image reference="folder-name-field.png" size="large" alt="Name field of the new folder"/}}
)))
1. Choose the folder in the "Move to…" list of a bookmark, or drag the bookmark onto the folder.(((
{{image reference="move-to-list.png" size="large" alt="Move to list of a bookmark"/}}
)))
1. The bookmark is in the folder:(((
{{image reference="bookmark-in-folder.png" size="large" alt="Folder holding the moved bookmark"/}}
)))
""", faq="""
== How do I create a subfolder? ==

Click "Add a subfolder" on the parent folder.

== How do I change the order of my bookmarks? ==

Drag them, or use their "Move up" and "Move down" arrows.

== Can I give a bookmark another name? ==

Yes, click "Rename" on the bookmark. Leave the name empty to display the page reference again.

== What happens to the bookmarks of a deleted folder? ==

They are moved to "Uncategorized".
""", attachments=['new-folder-button.png', 'folder-name-field.png', 'move-to-list.png',
                  'bookmark-in-folder.png']),

    page(USER, 'select-panel', 'Select the Bookmarks of the Panel', 'howto', 'user', """
Choose the [[bookmarks>>doc:@USER@.WebHome]] that the "My bookmarks" Panel displays on every page. Without any selection, the Panel displays your first bookmarks.

1. [[Open your bookmarks>>doc:@USER@.open.WebHome]].
1. Check the "Panel" box of each bookmark to display.(((
{{image reference="panel-checkbox.png" size="large" alt="Panel box of a bookmark"/}}
)))
1. The Panel displays the selected bookmarks:(((
{{image reference="panel-selection.png" size="medium" alt="My bookmarks panel displaying the selected bookmarks"/}}
)))
""", faq="""
== How many bookmarks can the Panel display? ==

Up to the limit set by your administrator. The counter above your bookmarks shows it.
""", attachments=['panel-checkbox.png', 'panel-selection.png']),

    page(USER, 'export', 'Export Your Bookmarks', 'howto', 'user', """
Save your [[bookmarks>>doc:@USER@.WebHome]] to a file, to keep a copy or to [[import it>>doc:@USER@.import-file.WebHome]] into another wiki.

1. [[Open your bookmarks>>doc:@USER@.open.WebHome]].
1. Click "Export".(((
{{image reference="export-button.png" size="large" alt="Export button of the bookmarks page"/}}
)))
1. Your browser downloads the file ##bookmarks-<user>-<date>.json##, which holds your folders and bookmarks:(((
{{code language="json"}}
{"format":"advanced-bookmarks","formatVersion":1,"exportedAt":"2026-10-27T17:52:27.605775143Z","folders":[…],"bookmarks":[…]}
{{/code}}
)))
""", attachments=['export-button.png']),

    page(USER, 'import-file', 'Import Bookmarks from a File', 'howto', 'user', """
Add the bookmarks of an [[export file>>doc:@USER@.export.WebHome]] to your [[bookmarks>>doc:@USER@.WebHome]], or replace them with it.

1. [[Open your bookmarks>>doc:@USER@.open.WebHome]].
1. Click "Import…" and select the file.(((
{{image reference="import-button.png" size="large" alt="Import button of the bookmarks page"/}}
)))
1. Choose to add the bookmarks of the file or to replace yours, then click "Import".(((
{{image reference="import-options.png" size="large" alt="Import modes and Import button"/}}
)))
1. A message gives the number of bookmarks imported and skipped:(((
{{image reference="import-done.png" size="large" alt="Message giving the number of imported bookmarks"/}}
)))
""", faq="""
== Which bookmarks of the file are skipped? ==

Pages already in your bookmarks, pages you cannot view, pages excluded by your administrator and pages of another wiki.

== What happens to the folders of the file? ==

When adding, a folder of the file is merged with the folder of the same name at the same place, and the others are created.

== Can I undo a replacement? ==

No. Export your bookmarks first if you may need them again.
""", attachments=['import-button.png', 'import-options.png', 'import-done.png']),

    # -------------------------------------------------------- Administrators
    page(ADMIN, None, 'Advanced Bookmarks', 'explanation', 'administrator', """
[[Advanced Bookmarks>>doc:@EXT_PAGE@]] lets each user keep private bookmarks of wiki pages, in folders, with a star on every page and a Panel.

* [[Install Advanced Bookmarks>>doc:@ADMIN@.install.WebHome]] and add its Panel.
* [[Advanced Bookmarks Settings>>doc:@ADMIN@.settings.WebHome]] of each wiki: Panel size, star and excluded pages.
* [[Manage the Bookmarks of Another User>>doc:@ADMIN@.manage-another-user.WebHome]], for support.
* [[Export All the Bookmarks of a Wiki>>doc:@ADMIN@.export-wiki.WebHome]], and [[Import the Bookmarks of a Wiki>>doc:@ADMIN@.import-wiki.WebHome]].
* [[Back Up the Advanced Bookmarks Core>>doc:@ADMIN@.back-up.WebHome]].
* [[Advanced Bookmarks Limits>>doc:@ADMIN@.limits.WebHome]].
* [[Advanced Bookmarks Storage and Privacy>>doc:@ADMIN@.storage-privacy.WebHome]]: where bookmarks are stored and who can see them.
""", faq="""
== What happens to the bookmarks of a renamed user? ==

XWiki handles the rename as the deletion of the old user, so their bookmarks are erased. [[Export them>>doc:@ADMIN@.manage-another-user.WebHome]] before the rename, then import the file for the new user.

== Which bookmarks does the profile of a global user show? ==

Those of the main wiki only.

== What happens to a bookmark of a page moved to another wiki? ==

It is not updated, and the page is shown as deleted.

== What happens to bookmarks when a user or a wiki is deleted? ==

They are erased: in every wiki for a user, and all of them for a wiki.
""", related="""
* [[Advanced Bookmarks (for Users)>>doc:@USER@.WebHome]]
* [[Advanced Bookmarks (for Developers)>>doc:@DEV@.WebHome]]
"""),

    page(ADMIN, 'install', 'Install Advanced Bookmarks', 'howto', 'administrator', """
Install [[Advanced Bookmarks>>doc:@ADMIN@.WebHome]] and display its Panel. With a remote Solr server, [[create the ##advancedbookmarks## core>>doc:@ADMIN@.storage-privacy.WebHome]] first.

1. Back up the permanent directory of XWiki, in which the extension creates a Solr core:(((
{{code language="shell"}}
# /usr/local/xwiki/data is the permanent directory of the XWiki Docker image
tar czf xwiki-data.tar.gz -C /usr/local/xwiki data
{{/code}}
)))
1. As an administrator of the main wiki, with Programming Rights, install "Advanced Bookmarks" on the whole farm with the Extension Manager.
1. Go to "Administration > Look & Feel > Panels", add the "My bookmarks" Panel to a column and click "Save".(((
{{image reference="panels-administration.png" size="large" alt="My bookmarks panel added in the Panels administration"/}}
)))
1. The star and the Panel are displayed on the pages of the wiki:(((
{{image reference="star-panel-page.png" size="extra" alt="Page displaying the star in its action bar and the My bookmarks panel"/}}
)))
""", faq="""
== Why install it on the whole farm? ==

Its Java part creates a Solr core, which XWiki only does for components installed on the farm. Installing on the farm requires Programming Rights.

== Why install it as an administrator? ==

The star and the administration tools only work when the author of their pages administers the wiki. If that account loses this right, reinstall the extension as an administrator.

== Does an upgrade need a restart? ==

Plan one: an upgrade replaces a farm JAR, and some other extensions may stay partly inactive until XWiki restarts.
""", attachments=['panels-administration.png', 'star-panel-page.png']),

    page(ADMIN, 'settings', 'Advanced Bookmarks Settings', 'reference', 'administrator', """
Each wiki has its own settings for [[Advanced Bookmarks>>doc:@ADMIN@.WebHome]], in "Administration > Other > Advanced Bookmarks". Only the administrators of the wiki can change them, and a wiki never configured uses the defaults.

{{image reference="settings-section.png" size="extra" alt="Advanced Bookmarks section of the wiki administration"/}}

|=Setting|=Default|=What it does
|"Panel size"|##10##|Maximum number of bookmarks displayed in the Panel, from 1 to 20. When it is lowered, the Panels are shortened at once, and the extra selections are removed the next time each user saves their bookmarks.
|"Display the star"|Checked|Displays the star in the action bar of the pages. When it is hidden, users can only add bookmarks by importing a file.
|"Excluded pages"|None|Pages that cannot be added to the bookmarks, with the star or by an import. Selecting the home page of a space, such as ##Main.Archives.WebHome##, also excludes its child pages. Existing bookmarks of these pages are kept.

The pages of the extension itself, the ##AdvancedBookmarks## space and ##Panels.MyBookmarks##, can never be bookmarked.
""", attachments=['settings-section.png']),

    page(ADMIN, 'manage-another-user', 'Manage the Bookmarks of Another User', 'howto', 'administrator', """
Open the [[bookmarks>>doc:@ADMIN@.WebHome]] of a user of your wiki to help them, for instance to export or restore them.

1. [[Open your own bookmarks page>>doc:@USER@.open.WebHome]].
1. In "Manage the bookmarks of another user:", choose the user.(((
{{image reference="user-picker.png" size="large" alt="User picker of the bookmarks page"/}}
)))
1. The bookmarks of the user open in a new tab, under a message recalling whose bookmarks they are:(((
{{image reference="other-user-bookmarks.png" size="large" alt="Bookmarks of another user, with the reminder message"/}}
)))
""", faq="""
== Can I link to the bookmarks of a user? ==

Yes, add the ##user## parameter to the bookmarks page, such as ##AdvancedBookmarks.WebHome?user=XWiki.jdoe##.
""", attachments=['user-picker.png', 'other-user-bookmarks.png']),

    page(ADMIN, 'export-wiki', 'Export All the Bookmarks of a Wiki', 'howto', 'administrator', """
Save the bookmarks of all the users of the wiki to a single file, as a [[backup>>doc:@ADMIN@.back-up.WebHome]] or to move them to another wiki.

1. [[Open the Advanced Bookmarks settings>>doc:@ADMIN@.settings.WebHome]].
1. Click "Export all the bookmarks".(((
{{image reference="export-all-button.png" size="large" alt="Export all the bookmarks button"/}}
)))
1. Your browser downloads the file ##bookmarks-wiki-<wiki>-<date>.json##, and a message gives the number of users exported:(((
{{image reference="export-all-done.png" size="large" alt="Message giving the number of exported users"/}}
)))
""", faq="""
== Why are some users skipped? ==

Their bookmarks could not be read. The server log names them.
""", attachments=['export-all-button.png', 'export-all-done.png']),

    page(ADMIN, 'import-wiki', 'Import the Bookmarks of a Wiki', 'howto', 'administrator', """
Restore a [[wiki export>>doc:@ADMIN@.export-wiki.WebHome]] user by user, in the background.

1. [[Open the Advanced Bookmarks settings>>doc:@ADMIN@.settings.WebHome]].
1. Under "Export and import of all the bookmarks", click "Import…" and select the file.(((
{{image reference="wiki-import-button.png" size="large" alt="Import button of the administration tools"/}}
)))
1. Choose to add the bookmarks of the file or to replace those of its users, then click "Import".(((
{{image reference="wiki-import-options.png" size="large" alt="Wiki import modes and Import button"/}}
)))
1. When the import is over, a summary gives the number of users imported, skipped and failed:(((
{{image reference="wiki-import-done.png" size="large" alt="Summary of the wiki import"/}}
)))
""", faq="""
== Which users are skipped? ==

The users of the file who do not exist in this wiki. A user whose bookmarks cannot be imported is counted as failed and logged, and the import goes on.

== What happens with an invalid file? ==

Nothing is changed: the whole file is checked before the first user is imported.

== Can two imports run at the same time? ==

No, only one import of a wiki runs at a time.
""", attachments=['wiki-import-button.png', 'wiki-import-options.png', 'wiki-import-done.png']),

    page(ADMIN, 'back-up', 'Back Up the Advanced Bookmarks Core', 'howto', 'administrator', """
Copy the Solr core of [[Advanced Bookmarks>>doc:@ADMIN@.WebHome]], which XWiki cannot rebuild from its database. Back it up together with the database, so that both copies match.

1. Stop XWiki.
1. Archive the core directory, under the permanent directory of XWiki:(((
{{code language="shell"}}
# /usr/local/xwiki/data is the permanent directory of the XWiki Docker image
tar czf advancedbookmarks-core.tar.gz -C /usr/local/xwiki/data/store/solr advancedbookmarks_9
{{/code}}
)))
1. Start XWiki.
1. The archive holds the core:(((
{{code language="shell"}}
$ tar tzf advancedbookmarks-core.tar.gz | head -n 4
advancedbookmarks_9/
advancedbookmarks_9/data/
advancedbookmarks_9/data/snapshot_metadata/
advancedbookmarks_9/data/tlog/
{{/code}}
)))
""", faq="""
== How do I back up a remote Solr server? ==

Back up the ##advancedbookmarks## core with the backup procedure of the Solr server.

== Can I back up the bookmarks without stopping XWiki? ==

Yes, [[export all the bookmarks of each wiki>>doc:@ADMIN@.export-wiki.WebHome]]; the file can be imported again.

== Why does the directory name end with 9? ==

The suffix is the major version of Solr used by XWiki.
"""),

    page(ADMIN, 'limits', 'Advanced Bookmarks Limits', 'reference', 'administrator', """
The limits of [[Advanced Bookmarks>>doc:@ADMIN@.WebHome]] protect the storage and the server. An operation that would exceed one is refused with a message, and nothing is changed.

|=Limit|=Value|=What it applies to
|Bookmarks of a user|32,766 bytes of JSON|About 150 to 300 bookmarks, depending on the length of the page references and labels: the largest value the Solr field accepts. The "Space used" indicator of the bookmarks page warns users from 80%, and tells them that their bookmarks are almost full from 95%.
|Excluded pages|100 per wiki|Pages that cannot be bookmarked.
|Folder name|100 characters|
|Bookmark label|200 characters|
|User export file|32 KB|File imported by a user.
|Wiki export file|32 MB and 10,000 users|File imported by an administrator.
"""),

    page(ADMIN, 'storage-privacy', 'Advanced Bookmarks Storage and Privacy', 'explanation', 'administrator', """
[[Advanced Bookmarks>>doc:@ADMIN@.WebHome]] keeps bookmarks outside the XWiki database, and checks every access on the server.

== Storage ==

{{plantuml}}
@startuml
!theme bluegray
rectangle "XWiki farm" {
  rectangle "Main wiki" as main
  rectangle "Other wikis" as wikis
}
database "XWiki database\\n(pages)" as db
database "Solr core advancedbookmarks\\n(one document per user and wiki)" as solr
main --> db
wikis --> db
main --> solr
wikis --> solr
@enduml
{{/plantuml}}

Bookmarks are stored in a dedicated Solr core, ##advancedbookmarks##, shared by all the wikis of the farm. The XWiki database is not used, so [[back up the core>>doc:@ADMIN@.back-up.WebHome]] separately. With the Solr embedded in XWiki (the default), the extension creates the core itself. With a remote Solr server, an administrator creates the core before installing the extension, as for the Like and Ratings applications.

== Rights and Privacy ==

The extension does not need Programming Rights to run.

* Users read and modify only their own bookmarks. The administrators of a wiki can also [[manage those of any user>>doc:@ADMIN@.manage-another-user.WebHome]] of that wiki, provided the page running the operation was written by an administrator too, so that a script written by someone else cannot use their rights.
* Guests have no bookmarks.
* A page can only be bookmarked by a user who can view it. The link and the status of a bookmarked page are only given to users who can view it.
* Modifications are only accepted in POST requests carrying a valid CSRF token.
"""),

    # ------------------------------------------------------------ Developers
    page(DEV, None, 'Advanced Bookmarks', 'explanation', 'developer', """
[[Advanced Bookmarks>>doc:@EXT_PAGE@]] exposes the bookmarks of the current wiki to wiki scripts and to JavaScript, through the same component, so both go through the same rights checks.

{{plantuml}}
@startuml
!theme bluegray
rectangle "Star, Panel and bookmarks page\\n(JavaScript)" as js
rectangle "Wiki scripts" as scripts
rectangle "AdvancedBookmarks.Code.Service\\n(HTTP service page)" as http
rectangle "$services.advancedbookmarks\\n(script services)" as services
rectangle "Bookmark manager\\n(rights, validation, versions)" as manager
database "Solr core\\nadvancedbookmarks" as solr
js --> http : JSON
http --> services
scripts --> services
services --> manager
manager --> solr
@enduml
{{/plantuml}}

* [[Advanced Bookmarks Script Services>>doc:@DEV@.script-services.WebHome]]: ##$services.advancedbookmarks## and its ##admin## part.
* [[Advanced Bookmarks HTTP Service>>doc:@DEV@.http-service.WebHome]]: the same operations over HTTP, for JavaScript.
* [[Bookmark Export File Format>>doc:@DEV@.export-file-format.WebHome]]: the files of the user and wiki exports.

The bookmarks of a user are one Solr document per wiki, holding the folders and bookmarks as JSON, with a version. Each write passes the version read by the caller: a write based on an outdated version is refused with ##conflict##, and the caller reads the bookmarks again.
""", related="""
* [[Advanced Bookmarks (for Users)>>doc:@USER@.WebHome]]
* [[Advanced Bookmarks (for Administrators)>>doc:@ADMIN@.WebHome]]
"""),

    page(DEV, 'script-services', 'Advanced Bookmarks Script Services', 'reference', 'developer', """
##$services.advancedbookmarks## gives scripts access to the bookmarks of the [[current wiki>>doc:@DEV@.WebHome]]. Each method returns a map ready to be serialized as JSON, with an HTTP status code under the ##status## key and, on failure, an ##error## key. Write methods only accept POST requests carrying a valid CSRF token in the ##form_token## parameter. The ##admin## methods return ##forbidden## to the users who do not administer the wiki.

|=Member|=Returns|=Default|=What it does
|##getBookmarks(String user)##|##user##, ##version##, ##size##, ##maxSize##, ##folders##, ##bookmarks##|Current user|Reads the folders and bookmarks of a user, in display order. Each bookmark has ##page##, ##label##, ##folder##, ##order##, ##panel## and ##viewable##, plus ##exists## and ##url## when the current user can view the page. ##size## and ##maxSize## are the space used and allowed, in bytes.
|##getPanel()##|##bookmarks##| |Reads the bookmarks of the current user selected for the Panel, or the first ones, up to the Panel size of the wiki.
|##isBookmarked(String page)##|##bookmarked##, ##bookmarkable##| |Tells whether the current user bookmarked the page, and whether the page may be bookmarked (##false## for an excluded page).
|##isStarEnabled()##|##boolean##| |Tells whether the current wiki displays the star.
|##toggle(String page)##|##bookmarked##| |Adds the page to the uncategorized bookmarks of the current user, or removes it. Write method.
|##save(String user, String json, String version)##|##version##, ##size##|Current user|Replaces the bookmarks of a user with the ##folders## and ##bookmarks## arrays of ##json##, provided they were not modified since ##version## was read (##0## if there were none). Write method.
|##exportBookmarks(String user)##|##content##|Current user|Returns the [[export file>>doc:@DEV@.export-file-format.WebHome]] of a user, as a JSON string.
|##importBookmarks(String user, String json, String mode, String version)##|##version##, ##imported##, ##skipped##|Current user|Imports an export file in ##merge## or ##replace## mode, provided the bookmarks were not modified since ##version## was read. Write method.
|##admin.getStoreStatus()##|##available##| |Tells whether the Solr core can be queried.
|##admin.exportWiki()##|##content##, ##exported##, ##skipped##| |Returns the wiki export file of the current wiki, and the number of users exported and skipped.
|##admin.startWikiImport(String mode)##|##users##| |Checks the wiki export file sent in the ##file## part of the current ##multipart/form-data## request, then imports it in the background in ##merge## or ##replace## mode. Write method.
|##admin.getWikiImportStatus()##|##state##, ##progress##, ##importedUsers##, ##skippedUsers##, ##failedUsers##, ##importedBookmarks##, ##skippedBookmarks##, ##failed##| |Reads the state of the running or last import: ##none##, ##running## or ##finished##. ##failed## is ##true## when it stopped on an unexpected error.

|=##error##|=##status##|=Cause
|##forbidden##|##403##|The current user may not perform the operation, or the page is excluded or cannot be viewed.
|##invalidToken##|##403##|The CSRF token is missing or invalid.
|##methodNotAllowed##|##405##|A write method was called outside a POST request.
|##invalid##|##400##|A parameter or the JSON is invalid.
|##tooLarge##|##400##|The bookmarks would exceed their maximum size.
|##conflict##|##409##|The bookmarks were modified since ##version## was read, or a wiki import is already running.
|##storageError##|##500##|The Solr core failed. The details are in the server log.

{{code language="velocity"}}
{{velocity}}
#set ($result = $services.advancedbookmarks.getBookmarks(''))
#if ($result.status == 200)
  You have $result.bookmarks.size() bookmarks.
#else
  The bookmarks cannot be read: $result.error
#end
{{/velocity}}
{{/code}}

The script displays, for instance:

{{code language="none"}}
You have 7 bookmarks.
{{/code}}
"""),

    page(DEV, 'http-service', 'Advanced Bookmarks HTTP Service', 'reference', 'developer', """
The page ##AdvancedBookmarks.Code.Service## exposes the [[script services>>doc:@DEV@.script-services.WebHome]] over HTTP, for JavaScript. Call it with the ##get## action and an ##operation## parameter: it answers with the JSON result of the matching method, under the same HTTP status.

|=Operation|=Request|=Parameters|=Method called
|##isBookmarked##|GET|##page##|##isBookmarked##
|##toggle##|POST|##page##, ##form_token##|##toggle##
|##getBookmarks##|GET|##user## (optional)|##getBookmarks##
|##save##|POST|##user## (optional), ##data##, ##version##, ##form_token##|##save##
|##export##|GET|##user## (optional)|##exportBookmarks##
|##import##|POST|##user## (optional), ##data##, ##mode##, ##version##, ##form_token##|##importBookmarks##
|##exportWiki##|GET| |##admin.exportWiki##
|##startWikiImport##|POST, ##multipart/form-data##|##mode##, ##file##, ##form_token##|##admin.startWikiImport##
|##wikiImportStatus##|GET| |##admin.getWikiImportStatus##

An unknown operation returns the ##unknownOperation## error with the ##400## status, and the ##unavailable## error with the ##503## status when the Java part of the extension is not installed.

{{code language="javascript"}}
// Add the current page to the bookmarks of the current user, or remove it.
const service = XWiki.Model.resolve('AdvancedBookmarks.Code.Service', XWiki.EntityType.DOCUMENT);
const body = new FormData();
body.append('operation', 'toggle');
body.append('page', XWiki.Model.serialize(XWiki.currentDocument.documentReference));
body.append('form_token', document.documentElement.dataset.xwikiFormToken);
const response = await fetch(new XWiki.Document(service).getURL('get'), {method: 'POST', body});
console.log(await response.json());
{{/code}}

The console then shows:

{{code language="json"}}
{"status": 200, "bookmarked": true}
{{/code}}
"""),

    page(DEV, 'export-file-format', 'Bookmark Export File Format', 'reference', 'developer', """
[[Users>>doc:@USER@.export.WebHome]] and [[administrators>>doc:@ADMIN@.export-wiki.WebHome]] export bookmarks as JSON files. Page references are written without their wiki, so that a file can be imported into another wiki.

|=Key|=Type|=What it holds
|##format##|String|##advanced-bookmarks## for a user export, ##advanced-bookmarks-wiki## for a wiki export.
|##formatVersion##|Number|##1##
|##exportedAt##|String|Date of the export, in ISO 8601 format.
|##folders##|Array|User export: the folders, each with ##id##, ##name##, ##parent## (##id## of the parent folder, empty at the top level) and ##order##.
|##bookmarks##|Array|User export: the bookmarks, each with ##page## (page reference), ##label## (empty to display the page reference), ##folder## (##id## of the folder, empty for "Uncategorized"), ##order## and ##panel## (##true## when selected for the Panel).
|##users##|Array|Wiki export: one object per user, with ##user##, ##folders## and ##bookmarks##. Local users are written without their wiki; global users of a farm keep the main wiki prefix, such as ##xwiki:XWiki.jdoe##.

A user export, indented for reading:

{{code language="json"}}
{
  "format": "advanced-bookmarks",
  "formatVersion": 1,
  "exportedAt": "2026-10-27T17:52:27.605775143Z",
  "folders": [
    {"id": "c460da96-9230-40bf-949d-355ed987bb51", "name": "Projects", "parent": "", "order": 0}
  ],
  "bookmarks": [
    {"page": "Sandbox.TestPage1", "label": "Release plan", "folder": "c460da96-9230-40bf-949d-355ed987bb51", "order": 0, "panel": true},
    {"page": "Main.WebHome", "label": "", "folder": "", "order": 0, "panel": false}
  ]
}
{{/code}}

A wiki export, with the same folders and bookmarks for each user:

{{code language="json"}}
{
  "format": "advanced-bookmarks-wiki",
  "formatVersion": 1,
  "exportedAt": "2026-10-27T17:52:27.605775143Z",
  "users": [
    {"user": "XWiki.jdoe", "folders": [], "bookmarks": [{"page": "Main.WebHome", "label": "", "folder": "", "order": 0, "panel": false}]},
    {"user": "xwiki:XWiki.asmith", "folders": [], "bookmarks": []}
  ]
}
{{/code}}
"""),
]

SHOTS = 'shots'

PIN = {
    USER + '.WebHome': ['open', 'add-page', 'organize-folders', 'select-panel', 'export', 'import-file'],
    ADMIN + '.WebHome': ['install', 'settings', 'manage-another-user', 'export-wiki', 'import-wiki', 'back-up',
                         'limits', 'storage-privacy'],
    DEV + '.WebHome': ['script-services', 'http-service', 'export-file-format'],
}
