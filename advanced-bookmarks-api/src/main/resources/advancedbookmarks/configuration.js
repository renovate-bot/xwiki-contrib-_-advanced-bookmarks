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
 * Editor of the excluded pages of the Advanced Bookmarks administration section: pages are chosen in the document tree
 * of XWiki (the picker of the move and copy dialogs) rather than typed, and each selected page is submitted as a hidden
 * field of the administration form.
 *
 * Security: page titles and references are inserted with textContent or as attribute values, never as HTML. The
 * server validates the saved references again.
 */
define('xwiki-advanced-bookmarks-configuration-messages', {
  prefix: 'advancedbookmarks.admin.excludedPages.',
  keys: ['remove']
});
// The tree module returns jQuery with the tree plugin, like in the dialogs of XWiki.
require(['xwiki-tree', 'xwiki-l10n!xwiki-advanced-bookmarks-configuration-messages'], function($, l10n) {
  'use strict';

  var DOCUMENT_NODE_PREFIX = 'document:';

  /**
   * The text of a tree node is HTML. It is converted to plain text by a parser that neither runs scripts nor loads
   * resources, unlike an element of the page.
   */
  var toPlainText = function(html) {
    return new DOMParser().parseFromString(String(html), 'text/html').body.textContent;
  };

  /**
   * @return the reference of the page of a tree node, relative to its wiki, or null for other nodes (wiki, pagination)
   */
  var getLocalReference = function(nodeId) {
    if (typeof nodeId !== 'string' || nodeId.indexOf(DOCUMENT_NODE_PREFIX) !== 0) {
      return null;
    }
    var reference = XWiki.Model.resolve(nodeId.substring(DOCUMENT_NODE_PREFIX.length), XWiki.EntityType.DOCUMENT);
    var wiki = reference.extractReference(XWiki.EntityType.WIKI);
    return XWiki.Model.serialize(wiki ? reference.relativeTo(wiki) : reference);
  };

  var createItem = function(name, reference, title) {
    var item = $('<li class="advanced-bookmarks-excluded-page"></li>');
    $('<input type="hidden">').attr('name', name).val(reference).appendTo(item);
    $('<span class="advanced-bookmarks-excluded-title"></span>').text(title).appendTo(item);
    item.append(' ');
    $('<span class="text-muted"></span>').text(reference).appendTo(item);
    item.append(' ');
    $('<button type="button" class="btn btn-default btn-xs advanced-bookmarks-excluded-remove"></button>')
      .text(l10n.get('remove')).appendTo(item);
    return item;
  };

  var init = function(editor) {
    var name = editor.attr('data-name');
    var list = editor.find('.advanced-bookmarks-excluded-list');
    var emptyMessage = editor.find('.advanced-bookmarks-excluded-empty');
    var modal = editor.find('.modal');
    var treeElement = modal.find('.location-tree');
    var selectButton = modal.find('.modal-footer .btn-primary');

    var getReferences = function() {
      return list.find('input[type="hidden"]').map(function() {
        return this.value;
      }).get();
    };

    var refreshEmptyMessage = function() {
      emptyMessage.prop('hidden', list.children().length > 0);
    };

    editor.find('.advanced-bookmarks-excluded-add').on('click', function(event) {
      event.preventDefault();
      modal.modal();
    });

    list.on('click', '.advanced-bookmarks-excluded-remove', function(event) {
      event.preventDefault();
      $(this).closest('li').remove();
      refreshEmptyMessage();
    });

    // The tree is loaded when it is first shown, as in the dialogs of XWiki, unless the tree script already did it.
    modal.on('shown.bs.modal', function() {
      if (!$.jstree.reference(treeElement)) {
        treeElement.xtree({core: {multiple: true}}).on('changed.jstree', function(event, data) {
          selectButton.prop('disabled', !data.selected.length);
        }).on('dblclick', '.jstree-anchor', function() {
          selectButton.click();
        });
      }
    });

    selectButton.on('click', function() {
      var tree = $.jstree.reference(treeElement);
      modal.modal('hide');
      if (!tree) {
        return;
      }
      var references = getReferences();
      tree.get_selected(true).forEach(function(node) {
        var reference = getLocalReference(node.id);
        if (reference && references.indexOf(reference) < 0) {
          references.push(reference);
          list.append(createItem(name, reference, toPlainText(node.text)));
        }
      });
      tree.deselect_all();
      refreshEmptyMessage();
    });
  };

  $('.advanced-bookmarks-excluded-pages').each(function() {
    init($(this));
  });
});
