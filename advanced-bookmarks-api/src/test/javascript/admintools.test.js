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
const {loadScripts} = require('./amdLoader');
const {installXWiki, createL10n, createResponse} = require('./xwikiDoubles');

const NONE = {state: 'none'};
const FINISHED = {state: 'finished', progress: 100, importedUsers: 3, skippedUsers: 1, failedUsers: 0,
  importedBookmarks: 12, skippedBookmarks: 2, failed: false};

describe('advanced-bookmarks administration tools', () => {
  let xwiki;
  let modules;
  let container;
  // The responses of the service, by operation: each one is a list consumed in order, the last one being repeated.
  let responses;

  const respond = function(operation, ...operationResponses) {
    responses[operation] = operationResponses;
  };

  const routeFetch = function(url, options) {
    const parameters = options.method === 'POST' ? options.body : new URL(url, 'http://localhost').searchParams;
    const operationResponses = responses[parameters.get('operation')];
    const response = operationResponses.length > 1 ? operationResponses.shift() : operationResponses[0];
    return Promise.resolve(response);
  };

  const getCalls = function(operation) {
    return window.fetch.mock.calls.filter(([url, options]) => {
      const parameters = options.method === 'POST' ? options.body : new URL(url, 'http://localhost').searchParams;
      return parameters.get('operation') === operation;
    });
  };

  // Lets the pending service calls complete, without running the timers that are not due yet.
  const settle = function() {
    return jest.advanceTimersByTimeAsync(0);
  };

  const load = async function(maxFileBytes, wiki = 'my wiki') {
    container = document.createElement('div');
    container.className = 'advanced-bookmarks-admin-tools';
    if (maxFileBytes !== undefined) {
      container.setAttribute('data-max-file-bytes', maxFileBytes);
    }
    document.body.appendChild(container);
    modules = loadScripts(['common.js', 'admintools.js'], {
      'xwiki-meta': {form_token: 'csrf-token', wiki: wiki},
      'xwiki-l10n!xwiki-advanced-bookmarks-admin-messages': createL10n()
    });
    await settle();
  };

  const getButton = function(text) {
    return Array.from(container.querySelectorAll('button')).find(button => button.textContent === text);
  };

  const getExportButton = () => getButton('translated:admin.tools.export');
  const getImportButton = () => getButton('translated:admin.tools.import');
  const getStatusText = () => container.querySelector('[aria-live]').textContent;
  const getProgress = () => container.querySelector('.progress');
  const getImportForm = () => container.querySelector('.advanced-bookmarks-admin-import');

  const chooseFile = function(file) {
    const fileInput = container.querySelector('input[type="file"]');
    Object.defineProperty(fileInput, 'files', {value: file ? [file] : [], configurable: true});
    fileInput.dispatchEvent(new Event('change'));
  };

  const createFile = function(size) {
    return new File(['x'.repeat(size)], 'wiki.json', {type: 'application/json'});
  };

  beforeEach(() => {
    jest.useFakeTimers();
    xwiki = installXWiki();
    responses = {wikiImportStatus: [createResponse(200, NONE)]};
    window.fetch = jest.fn(routeFetch);
  });

  afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
    jest.restoreAllMocks();
    document.body.innerHTML = '';
    delete window.fetch;
  });

  it('declares its translation keys, including the error codes of the service', async () => {
    await load();

    const messages = modules.get('xwiki-advanced-bookmarks-admin-messages');
    expect(messages.prefix).toBe('advancedbookmarks.');
    expect(messages.keys).toContain('admin.tools.importReplaceWarning');
    expect(messages.keys).toEqual(expect.arrayContaining(modules.get('xwiki-advanced-bookmarks-common').errorKeys));
  });

  it('displays the tools, without status when no import was run', async () => {
    await load();

    expect(getExportButton().disabled).toBe(false);
    expect(getImportButton().disabled).toBe(false);
    expect(getImportForm().hidden).toBe(true);
    expect(getProgress().hidden).toBe(true);
    expect(getStatusText()).toBe('');
    expect(getCalls('wikiImportStatus')).toHaveLength(1);
  });

  describe('export', () => {
    it('downloads the export of the wiki', async () => {
      respond('exportWiki', createResponse(200, {content: '{"users":[]}', exported: 3, skipped: 1}));
      await load();
      const common = modules.get('xwiki-advanced-bookmarks-common');
      const downloadFile = jest.spyOn(common, 'downloadFile').mockImplementation(() => {});
      jest.spyOn(common, 'getDateForFileName').mockReturnValue('2026-10-12');

      getExportButton().click();

      expect(getExportButton().disabled).toBe(true);
      expect(getImportButton().disabled).toBe(true);
      await settle();
      expect(downloadFile).toHaveBeenCalledWith('{"users":[]}', 'bookmarks-wiki-my_wiki-2026-10-12.json');
      expect(xwiki.widgets.Notification).toHaveBeenCalledWith('translated:admin.tools.exportDone:3:1', 'done');
      expect(getExportButton().disabled).toBe(false);
    });

    it('names the export file without the wiki when the page does not give it', async () => {
      respond('exportWiki', createResponse(200, {content: '{}', exported: 0, skipped: 0}));
      await load(undefined, null);
      const common = modules.get('xwiki-advanced-bookmarks-common');
      const downloadFile = jest.spyOn(common, 'downloadFile').mockImplementation(() => {});
      jest.spyOn(common, 'getDateForFileName').mockReturnValue('2026-10-12');

      getExportButton().click();
      await settle();

      expect(downloadFile).toHaveBeenCalledWith('{}', 'bookmarks-wiki--2026-10-12.json');
    });

    it('reports a failed export', async () => {
      respond('exportWiki', createResponse(403, {error: 'forbidden'}));
      await load();

      getExportButton().click();
      await settle();

      expect(xwiki.widgets.Notification).toHaveBeenCalledWith(
        'translated:admin.tools.exportFailed translated:error.forbidden', 'error');
      expect(getExportButton().disabled).toBe(false);
    });
  });

  describe('choice of the import file', () => {
    it('opens the file chooser of the browser', async () => {
      await load();
      const click = jest.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => {});

      getImportButton().click();

      expect(click).toHaveBeenCalledTimes(1);
      expect(click.mock.contexts[0]).toBe(container.querySelector('input[type="file"]'));
    });

    it('refuses a file larger than the size given by the page', async () => {
      await load('100');

      chooseFile(createFile(101));

      expect(xwiki.widgets.Notification).toHaveBeenCalledWith('translated:admin.tools.importTooLarge', 'error');
      expect(getImportForm().hidden).toBe(true);
    });

    it('accepts a file of the size given by the page', async () => {
      await load('100');

      chooseFile(createFile(100));

      expect(xwiki.widgets.Notification).not.toHaveBeenCalled();
      expect(getImportForm().hidden).toBe(false);
      expect(getImportForm().querySelector('p').textContent).toBe('translated:admin.tools.importFile:wiki.json');
    });

    it.each([
      ['missing', undefined],
      ['empty', ''],
      ['not a number', 'abc'],
      ['negative', '-1'],
      ['zero', '0'],
      ['not an integer', '1.5']
    ])('leaves the size check to the server when the size given by the page is %s', async (name, maxFileBytes) => {
      await load(maxFileBytes);

      chooseFile(createFile(200));

      expect(xwiki.widgets.Notification).not.toHaveBeenCalled();
      expect(getImportForm().hidden).toBe(false);
    });

    it('ignores a change without file', async () => {
      await load('100');

      chooseFile(null);

      expect(getImportForm().hidden).toBe(true);
      expect(xwiki.widgets.Notification).not.toHaveBeenCalled();
    });

    it('closes the form when the user cancels', async () => {
      await load();
      chooseFile(createFile(10));

      getButton('translated:admin.tools.cancel').click();

      expect(getImportForm().hidden).toBe(true);
      expect(getCalls('startWikiImport')).toHaveLength(0);
    });
  });

  describe('import', () => {
    const startImport = async function(mode) {
      chooseFile(createFile(10));
      const radio = getImportForm().querySelector('input[value="' + mode + '"]');
      radio.checked = true;
      radio.dispatchEvent(new Event('change', {bubbles: true}));
      getButton('translated:admin.tools.importConfirm').click();
    };

    it('sends the file with the chosen mode, then follows the import until it finishes', async () => {
      respond('startWikiImport', createResponse(200, {users: 4}));
      await load();
      respond('wikiImportStatus', createResponse(200, {state: 'running', progress: 40}), createResponse(200, FINISHED));

      await startImport('replace');

      expect(getImportForm().hidden).toBe(true);
      expect(getProgress().hidden).toBe(false);
      expect(getStatusText()).toBe('translated:admin.tools.importRunning:0');
      expect(getImportButton().disabled).toBe(true);
      const body = getCalls('startWikiImport')[0][1].body;
      expect(body.get('mode')).toBe('replace');
      expect(body.get('file').name).toBe('wiki.json');
      expect(body.get('form_token')).toBe('csrf-token');

      await settle();
      const progressBar = container.querySelector('.progress-bar');
      expect(progressBar.style.width).toBe('40%');
      expect(progressBar.getAttribute('aria-valuenow')).toBe('40');
      expect(getStatusText()).toBe('translated:admin.tools.importRunning:40');

      await jest.advanceTimersByTimeAsync(1000);
      expect(getProgress().hidden).toBe(true);
      expect(getStatusText()).toBe('translated:admin.tools.importDone:3:1:0:12:2');
      expect(getImportButton().disabled).toBe(false);
      expect(getCalls('wikiImportStatus')).toHaveLength(3);
    });

    it('follows the import already running when another one is started', async () => {
      respond('startWikiImport', createResponse(409, {error: 'conflict'}));
      await load();
      respond('wikiImportStatus', createResponse(200, {state: 'running', progress: 70}));

      await startImport('merge');
      expect(getCalls('startWikiImport')[0][1].body.get('mode')).toBe('merge');
      await settle();

      expect(getProgress().hidden).toBe(false);
      expect(getStatusText()).toBe('translated:admin.tools.importRunning:70');
      expect(getImportButton().disabled).toBe(true);
    });

    it('reports an invalid file', async () => {
      respond('startWikiImport', createResponse(400, {error: 'invalid'}));
      await load();

      await startImport('merge');
      await settle();

      expect(getProgress().hidden).toBe(true);
      expect(getStatusText()).toBe('translated:admin.tools.importFailed translated:admin.tools.importInvalid');
      expect(getImportButton().disabled).toBe(false);
    });

    it('reports the other errors with the message of the service', async () => {
      respond('startWikiImport', createResponse(503, {error: 'unavailable'}));
      await load();

      await startImport('merge');
      await settle();

      expect(getStatusText()).toBe('translated:admin.tools.importFailed translated:error.unavailable');
    });
  });

  describe('status of the last import', () => {
    it('follows an import started elsewhere', async () => {
      respond('wikiImportStatus', createResponse(200, {state: 'running', progress: 25}));

      await load();

      expect(getExportButton().disabled).toBe(true);
      expect(getStatusText()).toBe('translated:admin.tools.importRunning:25');
    });

    it('displays the result of the last import', async () => {
      respond('wikiImportStatus', createResponse(200, FINISHED));

      await load();

      expect(getStatusText()).toBe('translated:admin.tools.importDone:3:1:0:12:2');
    });

    it('displays an import stopped by an error', async () => {
      respond('wikiImportStatus', createResponse(200, Object.assign({}, FINISHED, {failed: true})));

      await load();

      expect(getStatusText()).toBe('translated:admin.tools.importStopped:3:1:0:12:2');
    });

    it('reports a status that cannot be read', async () => {
      respond('wikiImportStatus', createResponse(503, {error: 'unavailable'}));

      await load();

      expect(getProgress().hidden).toBe(true);
      expect(getStatusText()).toBe('translated:admin.tools.importFailed translated:error.unavailable');
      expect(getExportButton().disabled).toBe(false);
    });
  });
});
