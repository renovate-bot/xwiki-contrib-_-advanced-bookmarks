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
 * Star of the page action bar: adds the current page to the bookmarks of the user, or removes it. The server decides
 * the new state and checks the rights; the button only displays what the server answered.
 */
define('xwiki-advanced-bookmarks-star-messages', ['xwiki-advanced-bookmarks-common'], function(common) {
  return {
    prefix: 'advancedbookmarks.',
    keys: [
      'star.add',
      'star.remove',
      'star.added',
      'star.removed'
    ].concat(common.errorKeys)
  };
});
require(['xwiki-advanced-bookmarks-common', 'xwiki-l10n!xwiki-advanced-bookmarks-star-messages'],
    function(common, l10n) {
  'use strict';

  var ACTIVE_CLASS = 'advanced-bookmarks-star-active';

  var displayState = function(button, bookmarked) {
    var label = l10n.get(bookmarked ? 'star.remove' : 'star.add');
    button.classList.toggle(ACTIVE_CLASS, bookmarked);
    button.setAttribute('aria-pressed', String(bookmarked));
    button.setAttribute('title', label);
    // The label is plain text: never inject it as HTML.
    button.querySelector('.sr-only').textContent = label;
  };

  // Delegated listener, so that the star works whatever the moment the action bar is displayed.
  document.addEventListener('click', function(event) {
    var button = event.target.closest('.advanced-bookmarks-star');
    if (!button) {
      return;
    }
    event.preventDefault();
    if (button.disabled) {
      return;
    }
    // Prevent concurrent toggles from a double click.
    button.disabled = true;
    common.callService(l10n, {operation: 'toggle', page: button.getAttribute('data-page')}, true)
      .then(function(result) {
        var bookmarked = result.bookmarked === true;
        displayState(button, bookmarked);
        common.notify(l10n.get(bookmarked ? 'star.added' : 'star.removed'));
      }, function(error) {
        common.notify(error.message, 'error');
      }).then(function() {
        button.disabled = false;
      });
  });
});
