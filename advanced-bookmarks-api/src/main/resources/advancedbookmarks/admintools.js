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
/*
 * Administration tools of Advanced Bookmarks, displayed in the administration section of the wiki: export of the
 * bookmarks of all the users, and import of such a file in the background with its progress.
 *
 * The tools are built without any form element, since the administration section displays them next to its own
 * form. Every text is inserted with textContent. The server checks all the rights again.
 */
define('xwiki-advanced-bookmarks-admin-messages', ['xwiki-advanced-bookmarks-common'], function(common) {
  return {
    prefix: 'advancedbookmarks.',
    keys: [
      'admin.tools.export',
      'admin.tools.exportDone',
      'admin.tools.exportFailed',
      'admin.tools.import',
      'admin.tools.importTooLarge',
      'admin.tools.importFile',
      'admin.tools.importMerge',
      'admin.tools.importReplace',
      'admin.tools.importReplaceWarning',
      'admin.tools.importConfirm',
      'admin.tools.cancel',
      'admin.tools.importRunning',
      'admin.tools.importDone',
      'admin.tools.importStopped',
      'admin.tools.importFailed',
      'admin.tools.importInvalid',
      'admin.tools.importAlreadyRunning'
    ].concat(common.errorKeys)
  };
});
require(['xwiki-meta', 'xwiki-advanced-bookmarks-common', 'xwiki-l10n!xwiki-advanced-bookmarks-admin-messages'],
    function(xm, common, l10n) {
  'use strict';

  var BAD_REQUEST = 400;
  var CONFLICT = 409;
  var POLL_DELAY = 1000;

  var createElement = common.createElement;
  var notify = common.notify;

  var t = function(key) {
    return l10n.get.apply(l10n, ['admin.tools.' + key].concat(Array.prototype.slice.call(arguments, 1)));
  };

  var call = function(parameters, post) {
    return common.callService(l10n, parameters, post);
  };

  /**
   * @return the maximum size of an import file given by the page, or 0 when it is missing or invalid: the server
   *     then rejects a file too large on its own
   */
  var getMaxFileBytes = function(container) {
    var value = Number(container.getAttribute('data-max-file-bytes'));
    return Number.isInteger(value) && value > 0 ? value : 0;
  };

  var initialize = function(container) {
    var maxFileBytes = getMaxFileBytes(container);
    var fileInput = createElement('input', {type: 'file', accept: '.json,application/json', className: 'hidden',
      'aria-hidden': 'true', tabIndex: -1});
    var exportButton = createElement('button', {type: 'button', className: 'btn btn-default', text: t('export')});
    var importButton = createElement('button', {type: 'button', className: 'btn btn-default', text: t('import')});
    var importForm = createElement('div', {className: 'advanced-bookmarks-admin-import', hidden: true});
    var progressBar = createElement('div', {className: 'progress-bar', role: 'progressbar', 'aria-valuemin': '0',
      'aria-valuemax': '100', 'aria-valuenow': '0'});
    var progress = createElement('div', {className: 'progress', hidden: true}, [progressBar]);
    var statusText = createElement('p', {'aria-live': 'polite'});
    container.textContent = '';
    [
      createElement('p', {}, [exportButton, ' ', importButton, fileInput]),
      importForm,
      progress,
      statusText
    ].forEach(function(element) {
      container.appendChild(element);
    });

    // The chosen file, sent when the user confirms the import, and the controls of the form.
    var importFile = null;
    var importControls = null;

    var setBusy = function(busy) {
      exportButton.disabled = busy;
      importButton.disabled = busy;
    };

    var clearImport = function() {
      importFile = null;
      importControls = null;
    };

    var showStatus = function(text) {
      statusText.textContent = text;
    };

    var showProgress = function(percent) {
      progress.hidden = false;
      progressBar.style.width = percent + '%';
      progressBar.setAttribute('aria-valuenow', String(percent));
      showStatus(t('importRunning', percent));
    };

    var showResult = function(status) {
      progress.hidden = true;
      showStatus(t(status.failed ? 'importStopped' : 'importDone', status.importedUsers, status.skippedUsers,
        status.failedUsers, status.importedBookmarks, status.skippedBookmarks));
    };

    var poll = function() {
      call({operation: 'wikiImportStatus'}).then(function(status) {
        if (status.state === 'running') {
          setBusy(true);
          showProgress(status.progress);
          window.setTimeout(poll, POLL_DELAY);
        } else {
          setBusy(false);
          if (status.state === 'finished') {
            showResult(status);
          }
        }
      }, function(error) {
        setBusy(false);
        progress.hidden = true;
        showStatus(t('importFailed') + ' ' + error.message);
      });
    };

    var exportWiki = function() {
      setBusy(true);
      call({operation: 'exportWiki'}).then(function(result) {
        // "xwiki" gives "bookmarks-wiki-xwiki-2026-10-10.json".
        var wiki = String(xm.wiki || '').replace(/[^\w-]+/g, '_');
        common.downloadFile(result.content, 'bookmarks-wiki-' + wiki + '-' + common.getDateForFileName() + '.json');
        notify(t('exportDone', result.exported, result.skipped));
      }).catch(function(error) {
        notify(t('exportFailed') + ' ' + error.message, 'error');
      }).then(function() {
        setBusy(false);
      });
    };

    var showImportForm = function(file) {
      importFile = file;
      importControls = common.showImportForm(importForm, file.name, t, startImport, clearImport);
    };

    var startImport = function(mode) {
      var body = new FormData();
      body.append('operation', 'startWikiImport');
      body.append('mode', mode);
      body.append('file', importFile);
      importControls.close();
      clearImport();
      setBusy(true);
      showProgress(0);
      call(body, true).then(poll, function(error) {
        setBusy(false);
        progress.hidden = true;
        if (error.status === CONFLICT) {
          showStatus(t('importAlreadyRunning'));
          poll();
        } else {
          showStatus(t('importFailed') + ' ' + (error.status === BAD_REQUEST ? t('importInvalid') : error.message));
        }
      });
    };

    exportButton.addEventListener('click', exportWiki);
    importButton.addEventListener('click', function() {
      fileInput.click();
    });
    fileInput.addEventListener('change', function() {
      var file = fileInput.files && fileInput.files[0];
      // Cleared so that choosing the same file again triggers a new change event.
      fileInput.value = '';
      if (!file) {
        return;
      }
      if (maxFileBytes && file.size > maxFileBytes) {
        notify(t('importTooLarge'), 'error');
        return;
      }
      showImportForm(file);
    });

    // An import started earlier, possibly from another page, is followed until it finishes.
    poll();
  };

  document.querySelectorAll('.advanced-bookmarks-admin-tools').forEach(initialize);
});
