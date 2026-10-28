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
const {SERVICE_URL, installXWiki, createL10n, createResponse} = require('./xwikiDoubles');

const FORM_TOKEN = 'csrf-token';

describe('xwiki-advanced-bookmarks-common', () => {
  let xwiki;
  let common;
  let l10n;

  const loadCommon = function() {
    return loadScripts(['common.js'], {'xwiki-meta': {form_token: FORM_TOKEN}}).get('xwiki-advanced-bookmarks-common');
  };

  // Returns the rejection error of a promise that must fail.
  const rejectionOf = function(promise) {
    return promise.then(() => {
      throw new Error('The promise was expected to be rejected.');
    }, error => error);
  };

  beforeEach(() => {
    xwiki = installXWiki();
    common = loadCommon();
    l10n = createL10n();
    window.fetch = jest.fn();
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.restoreAllMocks();
    document.body.innerHTML = '';
    delete window.fetch;
  });

  describe('createElement', () => {
    it('inserts the text as text, never as HTML', () => {
      const element = common.createElement('span', {text: '<b>bold</b>'});

      expect(element.tagName).toBe('SPAN');
      expect(element.textContent).toBe('<b>bold</b>');
      expect(element.children).toHaveLength(0);
    });

    it('sets the class, the properties and the attributes', () => {
      const element = common.createElement('input', {
        className: 'bookmarks-check',
        type: 'checkbox',
        checked: true,
        'data-id': 'folder-1',
        'aria-hidden': true,
        draggable: 'true'
      });

      expect(element.className).toBe('bookmarks-check');
      expect(element.type).toBe('checkbox');
      expect(element.checked).toBe(true);
      expect(element.getAttribute('data-id')).toBe('folder-1');
      expect(element.getAttribute('aria-hidden')).toBe('');
      expect(element.getAttribute('draggable')).toBe('true');
    });

    it('ignores the false, null and undefined values', () => {
      const element = common.createElement('button', {disabled: false, title: null, 'data-page': undefined});

      expect(element.disabled).toBe(false);
      expect(element.hasAttribute('disabled')).toBe(false);
      expect(element.hasAttribute('title')).toBe(false);
      expect(element.hasAttribute('data-page')).toBe(false);
    });

    it('appends the children, strings as text, and skips the missing ones', () => {
      const child = document.createElement('em');

      const element = common.createElement('p', null, ['<i>text</i>', null, child, undefined]);

      expect(element.childNodes).toHaveLength(2);
      expect(element.childNodes[0].nodeType).toBe(Node.TEXT_NODE);
      expect(element.childNodes[0].textContent).toBe('<i>text</i>');
      expect(element.childNodes[1]).toBe(child);
    });

    it('accepts no attributes and no children', () => {
      const element = common.createElement('div');

      expect(element.attributes).toHaveLength(0);
      expect(element.childNodes).toHaveLength(0);
    });
  });

  describe('notify', () => {
    it('shows a success notification by default', () => {
      common.notify('Saved');

      expect(xwiki.widgets.Notification).toHaveBeenCalledWith('Saved', 'done');
    });

    it('shows a notification of the given type', () => {
      common.notify('Failed', 'error');

      expect(xwiki.widgets.Notification).toHaveBeenCalledWith('Failed', 'error');
    });
  });

  describe('callService', () => {
    it('resolves the URL of the service page', () => {
      expect(xwiki.Model.resolve).toHaveBeenCalledWith('AdvancedBookmarks.Code.Service', 'document');
      expect(xwiki.Document.mock.instances[0].getURL).toHaveBeenCalledWith('get');
    });

    it('reads with a GET request whose parameters are in the URL', async () => {
      window.fetch.mockResolvedValue(createResponse(200, {bookmarks: []}));

      const result = await common.callService(l10n, {operation: 'get', page: 'Main Page'});

      expect(result).toEqual({bookmarks: []});
      const [url, options] = window.fetch.mock.calls[0];
      expect(url).toBe(SERVICE_URL + '?operation=get&page=Main+Page');
      expect(options.method).toBeUndefined();
      expect(options.body).toBeUndefined();
      expect(options.credentials).toBe('same-origin');
      expect(options.headers).toEqual({'Accept': 'application/json'});
    });

    it('appends the parameters to a URL that already has a query string', async () => {
      installXWiki(SERVICE_URL + '?outputSyntax=plain');
      common = loadCommon();
      window.fetch.mockResolvedValue(createResponse(200, {}));

      await common.callService(l10n, {operation: 'get'});

      expect(window.fetch.mock.calls[0][0]).toBe(SERVICE_URL + '?outputSyntax=plain&operation=get');
    });

    it('writes with a POST request that carries the CSRF token', async () => {
      window.fetch.mockResolvedValue(createResponse(200, {version: '1'}));

      const result = await common.callService(l10n, {operation: 'toggle', page: 'Main.WebHome'}, true);

      expect(result).toEqual({version: '1'});
      const [url, options] = window.fetch.mock.calls[0];
      expect(url).toBe(SERVICE_URL);
      expect(options.method).toBe('POST');
      expect(options.body).toBeInstanceOf(URLSearchParams);
      expect(options.body.get('operation')).toBe('toggle');
      expect(options.body.get('page')).toBe('Main.WebHome');
      expect(options.body.get('form_token')).toBe(FORM_TOKEN);
    });

    it('sends a FormData as is, with the CSRF token', async () => {
      window.fetch.mockResolvedValue(createResponse(200, {}));
      const formData = new FormData();
      formData.append('operation', 'import');

      await common.callService(l10n, formData, true);

      const body = window.fetch.mock.calls[0][1].body;
      expect(body).toBe(formData);
      expect(body.get('operation')).toBe('import');
      expect(body.get('form_token')).toBe(FORM_TOKEN);
    });

    it('resolves to an empty object when a successful response is not JSON', async () => {
      window.fetch.mockResolvedValue(createResponse(200, new Error('not JSON')));

      await expect(common.callService(l10n, {operation: 'get'})).resolves.toEqual({});
    });

    it('rejects with the translated error code of the service and the HTTP status', async () => {
      window.fetch.mockResolvedValue(createResponse(409, {error: 'conflict'}));

      const error = await rejectionOf(common.callService(l10n, {operation: 'save'}, true));

      expect(error).toBeInstanceOf(Error);
      expect(error.message).toBe('translated:error.conflict');
      expect(error.status).toBe(409);
      expect(l10n.get).toHaveBeenCalledWith('error.conflict');
    });

    it('rejects with the network error when a failed response has no error code', async () => {
      window.fetch.mockResolvedValue(createResponse(502, new Error('not JSON')));

      const error = await rejectionOf(common.callService(l10n, {operation: 'get'}));

      expect(error.message).toBe('translated:error.network');
      expect(error.status).toBe(502);
    });

    it('rejects with the network error when the request fails', async () => {
      window.fetch.mockRejectedValue(new TypeError('Failed to fetch'));

      const error = await rejectionOf(common.callService(l10n, {operation: 'get'}));

      expect(error.message).toBe('translated:error.network');
      expect(error.status).toBeUndefined();
    });
  });

  describe('downloadFile', () => {
    it('saves the content as a JSON file and releases it later', () => {
      jest.useFakeTimers();
      URL.createObjectURL = jest.fn(() => 'blob:bookmarks');
      URL.revokeObjectURL = jest.fn();
      const clickedLinks = [];
      jest.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function() {
        // The link must be in the document when it is clicked, or some browsers ignore it.
        clickedLinks.push({link: this, attached: document.body.contains(this)});
      });

      common.downloadFile('{"folders":[]}', 'bookmarks.json');

      const blob = URL.createObjectURL.mock.calls[0][0];
      expect(blob).toBeInstanceOf(Blob);
      expect(blob.type).toBe('application/json');
      expect(blob.size).toBe('{"folders":[]}'.length);
      expect(clickedLinks).toHaveLength(1);
      const {link, attached} = clickedLinks[0];
      expect(attached).toBe(true);
      expect(link.getAttribute('href')).toBe('blob:bookmarks');
      expect(link.getAttribute('download')).toBe('bookmarks.json');
      expect(document.body.contains(link)).toBe(false);
      expect(URL.revokeObjectURL).not.toHaveBeenCalled();

      jest.advanceTimersByTime(60000);

      expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:bookmarks');
    });
  });

  describe('showImportForm', () => {
    let container;
    let onConfirm;
    let onCancel;
    let controls;

    // Translation function of a calling script: the key, followed by its parameters.
    const translate = (key, ...parameters) => [key].concat(parameters).join(':');

    const getButton = function(text) {
      return Array.from(container.querySelectorAll('button')).find(button => button.textContent === text);
    };

    const getRadio = function(mode) {
      return container.querySelector('input[value="' + mode + '"]');
    };

    const choose = function(mode) {
      getRadio(mode).checked = true;
      getRadio(mode).dispatchEvent(new Event('change', {bubbles: true}));
    };

    beforeEach(() => {
      container = document.createElement('div');
      container.hidden = true;
      container.textContent = 'previous form';
      document.body.appendChild(container);
      onConfirm = jest.fn();
      onCancel = jest.fn();
      controls = common.showImportForm(container, '<b>bookmarks</b>.json', translate, onConfirm, onCancel);
    });

    it('shows the file name, the modes, the warning and the buttons, as text', () => {
      expect(container.hidden).toBe(false);
      expect(container.textContent).not.toContain('previous form');
      expect(container.querySelector('p').textContent).toBe('importFile:<b>bookmarks</b>.json');
      expect(container.querySelector('b')).toBeNull();
      const labels = Array.from(container.querySelectorAll('.radio label')).map(label => label.textContent);
      expect(labels).toEqual([' importMerge', ' importReplace']);
      const warning = container.querySelector('.advanced-bookmarks-import-warning');
      expect(warning.textContent).toBe('importReplaceWarning');
      expect(warning.hidden).toBe(true);
      expect(getButton('importConfirm').className).toBe('btn btn-primary btn-sm');
      expect(getButton('cancel').className).toBe('btn btn-default btn-sm');
    });

    it('selects and focuses the merge mode', () => {
      expect(getRadio('merge').checked).toBe(true);
      expect(getRadio('replace').checked).toBe(false);
      expect(document.activeElement).toBe(getRadio('merge'));
    });

    it('shows the warning only for the replace mode', () => {
      const warning = container.querySelector('.advanced-bookmarks-import-warning');

      choose('replace');
      expect(warning.hidden).toBe(false);

      choose('merge');
      expect(warning.hidden).toBe(true);
    });

    it('confirms with the chosen mode, leaving the form open', () => {
      getButton('importConfirm').click();
      expect(onConfirm).toHaveBeenLastCalledWith('merge');

      choose('replace');
      getButton('importConfirm').click();
      expect(onConfirm).toHaveBeenLastCalledWith('replace');

      expect(container.hidden).toBe(false);
      expect(onCancel).not.toHaveBeenCalled();
    });

    it('closes the form when the user cancels', () => {
      getButton('cancel').click();

      expect(container.hidden).toBe(true);
      expect(container.childNodes).toHaveLength(0);
      expect(onCancel).toHaveBeenCalledTimes(1);
      expect(onConfirm).not.toHaveBeenCalled();
    });

    it('enables and disables the form', () => {
      const elements = Array.from(container.querySelectorAll('input, button'));
      expect(elements).toHaveLength(4);

      controls.setEnabled(false);
      elements.forEach(element => expect(element.disabled).toBe(true));

      controls.setEnabled(true);
      elements.forEach(element => expect(element.disabled).toBe(false));
    });

    it('closes the form without calling the cancel callback', () => {
      controls.close();

      expect(container.hidden).toBe(true);
      expect(container.childNodes).toHaveLength(0);
      expect(onCancel).not.toHaveBeenCalled();
    });

    it('gives each form its own group of radio buttons', () => {
      const other = document.createElement('div');
      document.body.appendChild(other);

      common.showImportForm(other, 'other.json', translate, jest.fn(), jest.fn());

      const name = getRadio('merge').name;
      expect(getRadio('replace').name).toBe(name);
      expect(other.querySelector('input[value="merge"]').name).not.toBe(name);
      // Choosing a mode in one form does not change the other one.
      choose('replace');
      expect(other.querySelector('input[value="merge"]').checked).toBe(true);
    });
  });

  describe('getDateForFileName', () => {
    it('returns the current date in ISO format', () => {
      jest.useFakeTimers().setSystemTime(new Date('2026-10-10T12:30:00Z'));

      expect(common.getDateForFileName()).toBe('2026-10-10');
    });
  });

  describe('errorKeys', () => {
    it('lists the translation keys of the error codes, including the network error', () => {
      expect(common.errorKeys).toContain('error.network');
      common.errorKeys.forEach(key => expect(key).toMatch(/^error\.[a-zA-Z]+$/));
    });
  });
});
