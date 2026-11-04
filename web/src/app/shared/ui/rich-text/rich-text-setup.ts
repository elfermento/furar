// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// O único ficheiro que importa TipTap (MIT, só o núcleo libre) en tempo de execución: chega só por
// `import()` desde rich-text-editor.component.ts, así que vai nun chunk diferido
// (`rich-text-setup-*.js`, grupo `lazy` do service worker) e non na carga inicial. Só as pezas da
// barra: parágrafo, títulos h2 e h3, negra, cursiva, listas, ligazón (https e mailto), salto de
// liña e desfacer. Sen estilos inxectados (CSP `style-src 'self'`): os estilos van en rich-text.css.
import { Editor } from '@tiptap/core';
import { Bold } from '@tiptap/extension-bold';
import { Document } from '@tiptap/extension-document';
import { HardBreak } from '@tiptap/extension-hard-break';
import { Heading } from '@tiptap/extension-heading';
import { Italic } from '@tiptap/extension-italic';
import { Link } from '@tiptap/extension-link';
import { BulletList } from '@tiptap/extension-list/bullet-list';
import { ListItem } from '@tiptap/extension-list/item';
import { OrderedList } from '@tiptap/extension-list/ordered-list';
import { Paragraph } from '@tiptap/extension-paragraph';
import { Text } from '@tiptap/extension-text';
import { UndoRedo } from '@tiptap/extensions/undo-redo';
import { RICH_TEXT_LINK } from './rich-text-link';

export interface RichTextEditorOptions {
  readonly element: HTMLElement;
  readonly content: string;
  /** Atributos do `contenteditable` (id, role, aria-*, lang, class). */
  readonly attributes: Record<string, string>;
  readonly onChange: (html: string) => void;
  /** Cambiou a selección ou o documento: a barra repinta os estados. */
  readonly onState: () => void;
  readonly onBlur: () => void;
  /** Ctrl/⌘+K: a barra abre o campo da ligazón. */
  readonly onLinkShortcut: () => void;
}

export function createRichTextEditor(options: RichTextEditorOptions): Editor {
  let pastingText = false;
  return new Editor({
    element: options.element,
    content: options.content,
    injectCSS: false,
    extensions: [
      Document,
      Paragraph,
      Text,
      Heading.configure({ levels: [2, 3] }),
      Bold,
      Italic,
      BulletList,
      OrderedList,
      ListItem,
      HardBreak,
      UndoRedo,
      Link.extend({
        addKeyboardShortcuts() {
          return {
            'Mod-k': () => {
              options.onLinkShortcut();
              return true;
            },
          };
        },
      }).configure({
        openOnClick: false,
        autolink: false,
        linkOnPaste: false,
        protocols: [],
        defaultProtocol: 'https',
        isAllowedUri: (url) => RICH_TEXT_LINK.test(url),
        HTMLAttributes: { rel: 'noopener noreferrer', target: null },
      }),
    ],
    editorProps: {
      attributes: options.attributes,
      // Pegar: só o texto. O HTML do portapapeis nin se analiza (unha <img onerror> chegaría a
      // pedirse); o formato chega só pola barra (o servidor saneará igual).
      transformPastedHTML: () => '',
      // Pegar: só o texto. O formato chega só pola barra (o servidor saneará igual).
      handlePaste: (view, event) => {
        if (pastingText) return false;
        const text = event.clipboardData?.getData('text/plain') ?? '';
        pastingText = true;
        try {
          view.pasteText(text, event);
        } finally {
          pastingText = false;
        }
        return true;
      },
      // Soltar: só mover o que xa está dentro, nunca HTML de fóra.
      handleDrop: (_view, _event, _slice, moved) => !moved,
    },
    onUpdate: ({ editor }) => {
      options.onChange(editor.isEmpty ? '' : editor.getHTML());
      options.onState();
    },
    onSelectionUpdate: () => options.onState(),
    onBlur: () => options.onBlur(),
  });
}
