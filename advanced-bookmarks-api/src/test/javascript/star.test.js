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
const {SERVICE_URL, installXWiki, createL10n, createResponse, flushPromises} = require('./xwikiDoubles');

const FORM_TOKEN = 'csrf-token';
const PAGE = 'Sandbox.WebHome';

describe('advanced-bookmarks star', () => {
  let xwiki;
  let modules;
  let button;

  // The script listens to the clicks on the document: it is loaded once, so that its listener is registered once.
  beforeAll(() => {
    xwiki = installXWiki();
    modules = loadScripts(['common.js', 'star.js'], {
      'xwiki-meta': {form_token: FORM_TOKEN},
      'xwiki-l10n!xwiki-advanced-bookmarks-star-messages': createL10n()
    });
  });

  beforeEach(() => {
    window.fetch = jest.fn();
    // Same markup as the StarUIX page; the texts are set by the tests through the script only.
    document.body.innerHTML = '<ul><li class="btn-group"><button type="button" class="btn btn-default '
      + 'advanced-bookmarks-star" aria-pressed="false" data-page="' + PAGE + '"><span class="fa fa-star"></span>'
      + '<span class="sr-only">Add</span></button></li></ul>';
    button = document.querySelector('.advanced-bookmarks-star');
  });

  afterEach(() => {
    jest.clearAllMocks();
    document.body.innerHTML = '';
    delete window.fetch;
  });

  const expectState = function(bookmarked) {
    const label = 'translated:' + (bookmarked ? 'star.remove' : 'star.add');
    expect(button.classList.contains('advanced-bookmarks-star-active')).toBe(bookmarked);
    expect(button.getAttribute('aria-pressed')).toBe(String(bookmarked));
    expect(button.getAttribute('title')).toBe(label);
    expect(button.querySelector('.sr-only').textContent).toBe(label);
  };

  it('declares its translation keys, including the error codes of the service', () => {
    const messages = modules.get('xwiki-advanced-bookmarks-star-messages');
    const common = modules.get('xwiki-advanced-bookmarks-common');

    expect(messages.prefix).toBe('advancedbookmarks.');
    expect(messages.keys).toEqual(['star.add', 'star.remove', 'star.added', 'star.removed'].concat(common.errorKeys));
  });

  it('adds the page to the bookmarks with a POST request that carries the CSRF token', async () => {
    window.fetch.mockResolvedValue(createResponse(200, {bookmarked: true}));

    button.click();

    expect(button.disabled).toBe(true);
    const [url, options] = window.fetch.mock.calls[0];
    expect(url).toBe(SERVICE_URL);
    expect(options.method).toBe('POST');
    expect(options.body.get('operation')).toBe('toggle');
    expect(options.body.get('page')).toBe(PAGE);
    expect(options.body.get('form_token')).toBe(FORM_TOKEN);

    await flushPromises();

    expectState(true);
    expect(xwiki.widgets.Notification).toHaveBeenCalledWith('translated:star.added', 'done');
    expect(button.disabled).toBe(false);
  });

  it('removes the page from the bookmarks', async () => {
    button.classList.add('advanced-bookmarks-star-active');
    window.fetch.mockResolvedValue(createResponse(200, {bookmarked: false}));

    button.click();
    await flushPromises();

    expectState(false);
    expect(xwiki.widgets.Notification).toHaveBeenCalledWith('translated:star.removed', 'done');
  });

  it('shows the error and keeps the state when the service fails', async () => {
    window.fetch.mockResolvedValue(createResponse(409, {error: 'conflict'}));

    button.click();
    await flushPromises();

    expect(button.classList.contains('advanced-bookmarks-star-active')).toBe(false);
    expect(button.getAttribute('aria-pressed')).toBe('false');
    expect(button.querySelector('.sr-only').textContent).toBe('Add');
    expect(xwiki.widgets.Notification).toHaveBeenCalledWith('translated:error.conflict', 'error');
    expect(button.disabled).toBe(false);
  });

  it('handles a click on the icon of the star', async () => {
    window.fetch.mockResolvedValue(createResponse(200, {bookmarked: true}));

    button.querySelector('.fa').click();
    await flushPromises();

    expect(window.fetch).toHaveBeenCalledTimes(1);
    expectState(true);
  });

  it('ignores the clicks while a toggle is running', async () => {
    let respond;
    window.fetch.mockReturnValue(new Promise(resolve => {
      respond = resolve;
    }));

    button.click();
    button.querySelector('.fa').click();

    expect(window.fetch).toHaveBeenCalledTimes(1);
    respond(createResponse(200, {bookmarked: true}));
    await flushPromises();
    expect(button.disabled).toBe(false);
  });

  it('ignores the clicks outside the star', () => {
    const event = new MouseEvent('click', {bubbles: true, cancelable: true});

    document.querySelector('ul').dispatchEvent(event);

    expect(window.fetch).not.toHaveBeenCalled();
    expect(event.defaultPrevented).toBe(false);
  });
});
