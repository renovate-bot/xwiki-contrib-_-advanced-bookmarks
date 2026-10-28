# Advanced Bookmarks

Advanced Bookmarks is an XWiki extension for managing personal bookmarks in folders, with a page star and a quick-access panel.

* Project Lead: [Christophe Neria](https://www.xwiki.org/xwiki/bin/view/XWiki/Christophe_cn)
* Documentation & Downloads: [Documentation & Download](https://extensions.xwiki.org/xwiki/bin/view/Extension/Advanced%20Bookmarks/)
* [Issue Tracker](https://jira.xwiki.org/browse/ADVBOOK)
* Communication: [Forum](https://forum.xwiki.org/), [Chat](https://dev.xwiki.org/xwiki/bin/view/Community/Chat)
* [Development Practices](https://dev.xwiki.org)
* Minimal XWiki version supported: XWiki 17.10.2
* License: LGPL 2.1
* Translations: English, French
* Sonar Dashboard: [![Status](https://sonarcloud.io/api/project_badges/measure?project=org.xwiki.contrib.advancedbookmarks:advanced-bookmarks&metric=alert_status)](https://sonarcloud.io/dashboard?id=org.xwiki.contrib.advancedbookmarks:advanced-bookmarks)
* Continuous Integration Status: [![Build Status](https://ci.xwiki.org/job/XWiki%20Contrib/job/advanced-bookmarks/job/master/badge/icon)](https://ci.xwiki.org/job/XWiki%20Contrib/job/advanced-bookmarks/job/master/)

The user, administrator and developer documentation, published on www.xwiki.org under `documentation/extensions/{user,admin,dev}/advanced-bookmarks`, is kept in [docs/documentation/pages.py](docs/documentation/pages.py) (the page set read by the `docpages.py` tool of [xwiki-dev-llm](https://github.com/xwiki/xwiki-dev-llm)), with its screenshots in [docs/documentation/shots/](docs/documentation/shots/). This README is aimed at developers and administrators.

## Installation

Install the **Advanced Bookmarks** extension (`org.xwiki.contrib.advancedbookmarks:advanced-bookmarks-ui`) with the Extension Manager, as a wiki administrator. Its Java part, `advanced-bookmarks-api`, is installed automatically.

The Java part can only be installed at farm level: the Solr core it creates is shared by all the wikis of the farm, and XWiki creates Solr cores from the farm-level components. Installing the extension on the whole farm requires programming rights.

The installing user must be an administrator of the wiki: the star is only displayed when the author of its UI extension has the administration right, and the features reserved to administrators (managing the bookmarks of other users, administration tools) also require that the author of the page running them administers the wiki. If the installing account later loses this right, reinstall the extension, or save its pages again, as an administrator.

Upgrading the extension replaces a farm-level JAR, after which XWiki reloads the other farm-level JAR extensions. Some of them may log warnings or errors during this reload (for instance *Likes*: "Right unregistration is disabled on purpose…"); an extension whose components fail to reload stays partly inactive until XWiki is restarted. Plan upgrades with a restart of XWiki.

## Usage

- **Star**: a star button is added to the action bar of every page (next to *Edit* and *Create*), for logged-in users. It adds the page to the uncategorized bookmarks, or removes it. It is not displayed on excluded pages (see [Configuration](#configuration)), nor on the pages of the extension itself.
- **Panel**: the *Bookmarks* panel (`Panels.MyBookmarks`) lists the bookmarks selected for the panel, or the first ones, up to the configured panel size (ten by default), with a link to the management page. It is not added to the page layout automatically: add it in *Administration > Look & Feel > Panels*. Deleted pages are struck through; pages the user cannot view are shown without link.
- **Management page**: `AdvancedBookmarks.WebHome` displays the `{{bookmarks/}}` macro, which can also be used in any other page. Users organize their bookmarks in folders and subfolders, by drag and drop or with the keyboard (arrow buttons and *Move to* lists), rename them, select bookmarks for the panel (up to the configured panel size) and filter the list. Changes are saved automatically; if the bookmarks were modified meanwhile (another tab, the star), they are reloaded. A *Space used* indicator shows how much of the space allowed for the bookmarks is used, and warns when it is almost full.
- **Size limit**: the bookmarks of a user (folders and bookmarks, stored as JSON) take at most 32,766 bytes, which is about 150 to 300 bookmarks depending on the length of the page references and labels. This is the largest value the Solr `string` field holding them accepts. Beyond it, saving, adding with the star or importing is refused with the `tooLarge` error and a message asking to remove bookmarks; nothing is changed.
- **Export and import**: the *Export* button of the management page downloads the bookmarks as a JSON file. *Import…* reads such a file (32 KB at most) and, after confirmation, either adds it to the current bookmarks or replaces them. When adding, an imported folder is merged with the folder of the same name (ignoring case) at the same place, other folders are created, imported items come after the existing ones of their folder, and pages already bookmarked are skipped: importing an export of the same bookmarks changes nothing. Page references are exported without their wiki, so a file can be imported into another wiki or farm; pages of another wiki, excluded pages and pages the user cannot view found in a file are skipped.
- **Administrators** get a user picker on the management page: the chosen user's bookmarks open in a new tab (`AdvancedBookmarks.WebHome?user=XWiki.jdoe`), with a banner recalling whose bookmarks are displayed. The server checks the administration right on every request.
- **Profile tab**: a *Bookmarks* tab is added to the user profile. It displays the same management interface for the user of the profile, and is only shown to this user and to the administrators of the wiki; anyone else reaching it gets an error message instead of the bookmarks. The tab needs the user profile of XWiki (`xwiki-platform-user-profile-ui`, installed by the standard flavor); without it, the tab is simply not displayed. Bookmarks are kept for each wiki: the profile of a global user, which is on the main wiki, shows the bookmarks of the main wiki only.
- **Macro parameter**: `{{bookmarks user="XWiki.jdoe"/}}` displays the bookmarks of the given user instead of the current one (this is what the profile tab does). The parameter takes precedence over the `user` request parameter, and no user picker is displayed. Only the user and the administrators of the wiki can view these bookmarks.

## Configuration

Each wiki has its own settings, in *Administration > Other > Advanced Bookmarks* (stored in the page `AdvancedBookmarks.Code.Configuration` of the wiki). A wiki that has not been configured uses the defaults.

| Setting | Default | Description |
| --- | --- | --- |
| Panel size | 10 | Maximum number of bookmarks displayed in the panel, from 1 to 20. An invalid value is replaced by the default, with a warning in the logs. When it is lowered, the panels are shortened at once, and the extra selections are removed the next time each user saves their bookmarks. |
| Display the star | Yes | Displays the star in the action bar of the pages. When it is hidden, bookmarks can only be added by importing a file. |
| Excluded pages | None | Pages that may not be added to the bookmarks, with the star or by an import (at most 100). Selecting the home page of a space, such as `Main.Archives.WebHome`, also excludes all its child pages. Existing bookmarks of these pages are kept and can still be removed. |

The pages of the extension itself (the `AdvancedBookmarks` space and `Panels.MyBookmarks`) are always excluded.

## Administration tools

The same administration section, above the settings, offers tools reserved to the administrators of the wiki:

- **Storage**: whether the `advancedbookmarks` Solr core can be queried. When it cannot, the cause is written to the server logs.
- **Export all the bookmarks**: downloads the bookmarks of all the users of the wiki as a single JSON file. Users whose bookmarks cannot be read (corrupted data, or leftovers of a user who no longer exists) are skipped and logged. An export larger than the import limit is refused.
- **Import…**: imports such a file in the background, user by user, in the `merge` or `replace` mode of the individual import, with a progress bar. The whole file is validated before anything is written: an invalid file changes nothing. Users of the file who do not exist in the wiki are skipped; if the bookmarks of a user keep being modified during the import, or cannot be stored, that user is counted as failed and logged, and the import goes on. Only one import of a wiki runs at a time. The file is sent as `multipart/form-data`, so it is not limited by the maximum POST size of the servlet container; it holds at most 10,000 users and 32 MB, each user being limited like an individual export (32 KB).

The wiki export file has this format:

```json
{"format": "advanced-bookmarks-wiki", "formatVersion": 1, "exportedAt": "2026-10-10T08:00:00Z",
 "users": [{"user": "XWiki.jdoe",
            "folders": [{"id": "f1", "name": "Work", "parent": "", "order": 0}],
            "bookmarks": [{"page": "Sandbox.WebHome", "label": "", "folder": "f1", "order": 0, "panel": false}]}]}
```

Local users are written without their wiki, so that the file can be imported into another wiki; global users of a farm keep the main wiki prefix (`xwiki:XWiki.jdoe`), so that they stay global.

## Modules

| Module | Packaging | Content |
| --- | --- | --- |
| `advanced-bookmarks-api` | JAR | Storage, business logic, rights, script service, `{{bookmarks/}}` macro with its JavaScript and CSS, and translations. |
| `advanced-bookmarks-ui` | XAR | Wiki pages: JSON service, star, panel, management page, profile tab, configuration and administration tools. |

## Access rules

Bookmarks are private and kept separately for each wiki of a farm. All the checks are made on the server:

- the current user and wiki always come from the XWiki context, never from the browser;
- users read and modify only their own bookmarks; administrators of a wiki can also manage the bookmarks of any existing user in that wiki, provided the page running the operation was also written by an administrator, so that a script written by someone else cannot use the rights of an administrator viewing it;
- guests have no bookmarks;
- a page can only be bookmarked by a user who can view it, and live information about a bookmarked page (whether it exists, its URL) is only returned to users who can view it;
- modifications are only accepted in POST requests carrying a valid CSRF token (`form_token` parameter).

The extension does not require programming rights.

## Script service

`$services.advancedbookmarks` returns maps ready to be serialized as JSON. Each result has a `status` key (an HTTP status code) and, on failure, an `error` key: `forbidden`, `invalidToken`, `methodNotAllowed`, `invalid`, `tooLarge` (the bookmarks would exceed their maximum size), `conflict` or `storageError`. Error details are only written to the server logs.

| Method | Description |
| --- | --- |
| `getBookmarks(user)` | Folders and bookmarks of a user (empty for the current user), in display order, with their `version`, the space they use (`size`, in bytes) and the space allowed (`maxSize`). |
| `getPanel()` | Bookmarks of the current user selected for the panel, or the first ones, up to the configured panel size. |
| `isBookmarked(page)` | Whether the current user bookmarked a page (`bookmarked`), and whether the page may be bookmarked (`bookmarkable`, `false` for an excluded page). |
| `isStarEnabled()` | Whether the current wiki displays the star (a plain boolean). |
| `toggle(page)` | Add the page to the uncategorized bookmarks of the current user, or remove it. Adding an excluded page is refused (`forbidden`). Write operation. |
| `save(user, json, version)` | Replace all the bookmarks of a user, provided they were not modified since `version` was read; `conflict` otherwise. Adding a page that is excluded or that the user cannot view is refused (`forbidden`); pages already bookmarked are kept. Returns the new `version` and the space used (`size`). Write operation. |
| `exportBookmarks(user)` | Export file of the bookmarks of a user, as a JSON string under the `content` key. |
| `importBookmarks(user, json, mode, version)` | Import an export file in the `merge` or `replace` mode, provided the bookmarks were not modified since `version` was read. Returns the new `version` and the number of bookmarks `imported` and `skipped`. Write operation. |

`$services.advancedbookmarks.admin` exposes the administration tools of the current wiki, with the same kind of results. Every method returns `forbidden` to users who do not administer the wiki, except `getMaxImportFileSize()`, which returns a plain number.

| Method | Description |
| --- | --- |
| `getStoreStatus()` | Whether the storage can be queried, under the `available` key. |
| `exportWiki()` | Wiki export file under the `content` key, with the number of users `exported` and `skipped`. |
| `startWikiImport(mode)` | Validate the wiki export file sent in the `file` part of the current `multipart/form-data` request, then import it in the background in the `merge` or `replace` mode. Returns the number of `users` of the file; `conflict` if an import of the wiki is already running. Write operation. |
| `getMaxImportFileSize()` | Maximum size, in bytes, of a file accepted by `startWikiImport` (32 MiB), given to the browser so that it can refuse a larger file before sending it. Available without any right. |
| `getWikiImportStatus()` | `state` of the running or last import (`none`, `running` or `finished`) and, unless `none`, its `progress` (percentage), the counts `importedUsers`, `skippedUsers`, `failedUsers`, `importedBookmarks` and `skippedBookmarks`, and `failed` if it stopped on an unexpected error. |

The page `AdvancedBookmarks.Code.Service` exposes the same operations over HTTP, with the `get` action and an `operation` parameter (`isBookmarked`, `toggle`, `getBookmarks`, `save`, `export`, `import`, and for administrators `exportWiki`, `startWikiImport`, `wikiImportStatus`); it returns the result as JSON with the matching HTTP status.

## Data lifecycle

- When a page is renamed or moved within its wiki, the bookmarks pointing to it are updated. A page moved to another wiki is displayed as deleted.
- A deleted page stays in the bookmarks and is displayed as deleted.
- When a user is deleted, their bookmarks are erased in every wiki. When a wiki is deleted, all its bookmarks are erased.
- Renaming a user page is handled by XWiki as the deletion of the old user, so the bookmarks of the old user are erased; they are not transferred to the new name. To keep them, an administrator exports them before the rename, from the management page opened for that user (`AdvancedBookmarks.WebHome?user=<old user>`, *Export*), then imports the file after the rename, from the management page opened for the new user (*Import…*, replace mode).

## Storage

The extension stores one Solr document per user and wiki in the dedicated `advancedbookmarks` core. The document contains the serialized folders and bookmarks, the wiki and user identifiers, and a multivalued page-reference field used to find bookmarks during page renames. Solr's native `_version_` field provides optimistic concurrency control.

The core is initialized by the extension when the XWiki Solr configuration supports automatic core creation. In installations using a remote Solr server, an administrator must create the `advancedbookmarks` core before enabling the extension.

The bookmark core is not rebuilt from the XWiki database. Include its data directory in regular, consistent backups. In the default local setup, this is under the permanent XWiki data directory. For remote Solr, back up the corresponding core using the Solr operator's established backup procedure. Restoring the core requires a backup made with a compatible Solr installation.

Administrators can also keep a copy of the bookmarks of a whole wiki with the [administration tools](#administration-tools). Users (and administrators, for any user) can keep a copy of bookmarks with the *Export* button of the management page, and restore it with *Import…* in replace mode. The export file has this format:

```json
{"format": "advanced-bookmarks", "formatVersion": 1, "exportedAt": "2026-10-10T08:00:00Z",
 "folders": [{"id": "f1", "name": "Work", "parent": "", "order": 0}],
 "bookmarks": [{"page": "Sandbox.WebHome", "label": "", "folder": "f1", "order": 0, "panel": false}]}
```

## Requirements

- XWiki 17.10.2 or later
- Java 17 or later

## Build and tests

Build with JDK 17; the parent POM compiles the extension for Java 17 (`xwiki.java.version`), and XWiki releases of a 17.10-based extension must be made with that JDK. With a newer JDK, Mockito prints a warning that the XWiki test framework reports as a test failure. Build the extension and run its unit and component tests with Maven:

```bash
mvn -B -ntp clean verify
```

Add `-Pquality` to also check the test coverage, as the XWiki continuous integration does.

The JavaScript resources of the API module have their own unit tests, written with [Jest](https://jestjs.io/) in `advanced-bookmarks-api/src/test/javascript` and run by Maven in the `test` phase. Node.js is downloaded by the `frontend-maven-plugin`, so no Node.js installation is needed; `-DskipTests` skips these tests as well. A coverage below the threshold of `package.json` fails the build.

When Maven and Java are not installed locally, the same build can be run with Docker from the project root (mounting `~/.npm` keeps the npm packages between builds):

```bash
docker run --rm -v "$PWD":/projet -v "$HOME/.m2":/root/.m2 -v "$HOME/.npm":/root/.npm -w /projet maven:3.9-eclipse-temurin-17 mvn -B -ntp clean verify
```

On Windows, keep the project and these caches on the WSL file system: mounting Windows folders in the container makes the build several times slower.

### Functional tests

The functional tests (module `advanced-bookmarks-test`) start XWiki with Tomcat and PostgreSQL, and a browser, in Docker containers, using the XWiki Docker test framework. They require Docker and are only run with the `integration-tests` and `docker` profiles:

```bash
mvn -B -ntp clean install -Pintegration-tests,docker
```

The tested XWiki version is the platform version of the parent POM (17.10.2). The first run downloads several Docker images and takes a while.

When Maven itself runs in a Docker container (Docker Desktop), the test framework passes host paths to the Docker daemon, so the project and the local Maven repository must be mounted at the path the daemon knows them by, and `/.dockerenv` must be removed so that the framework mounts the local Maven repository in the XWiki container. Docker named volumes cannot be used for this: the daemon does not see their content through their mount point. From WSL, mount the WSL paths at the same path in the container:

```bash
P=$PWD; M=$HOME/.m2
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v "$P":"$P" -v "$M":"$M" -v "$HOME/.npm":/root/.npm -w "$P" maven:3.9-eclipse-temurin-17 \
  sh -c "rm -f /.dockerenv && mvn -B -ntp -Dmaven.repo.local=$M/repository clean install -Pintegration-tests,docker"
```

Or from Windows with Git Bash:

```bash
R=/run/desktop/mnt/host/c/Users/<user>/.m2/repository
MSYS_NO_PATHCONV=1 docker run --rm -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v 'C:\path\to\advanced-bookmarks':/run/desktop/mnt/host/c/path/to/advanced-bookmarks \
  -v 'C:\Users\<user>\.m2':/run/desktop/mnt/host/c/Users/<user>/.m2 \
  -w /run/desktop/mnt/host/c/path/to/advanced-bookmarks maven:3.9-eclipse-temurin-17 \
  sh -c "rm -f /.dockerenv && mvn -B -ntp -Dmaven.repo.local=$R clean install -Pintegration-tests,docker"
```
