// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IconName } from '../icon/icons';

export type RichTextCommand = 'h2' | 'h3' | 'bold' | 'italic' | 'bulletList' | 'orderedList' | 'link';
export type RichTextState = Readonly<Record<Exclude<RichTextCommand, 'link'>, boolean>>;
export const NO_FORMAT: RichTextState = {
  h2: false,
  h3: false,
  bold: false,
  italic: false,
  bulletList: false,
  orderedList: false,
};

/**
 * Un botón da barra. O atallo vese no tooltip (`ui.richText.keys.<orde>` fóra de Mac, onde
 * «Maiús» se traduce; en Mac, os símbolos) e vai en `aria-keyshortcuts` (`aria`).
 */
export interface RichTextTool {
  readonly cmd: RichTextCommand;
  /** Icona; os títulos levan unha «T» grande ou pequena (rich-text.css). */
  readonly icon?: IconName;
  readonly level?: 2 | 3;
  readonly mac: string;
  readonly aria: { readonly mac: string; readonly pc: string };
  /** Separador antes do botón. */
  readonly group?: boolean;
  /** Cara a onde abre o tooltip para non saír da barra. */
  readonly tip?: 'start' | 'end';
}

/** Os mesmos atallos que trae TipTap (Mod-Alt-2, Mod-b…); Mod-k engádeo rich-text-setup.ts. */
export const RICH_TEXT_TOOLS: readonly RichTextTool[] = [
  { cmd: 'h2', level: 2, mac: '⌥⌘2', aria: { mac: 'Meta+Alt+2', pc: 'Control+Alt+2' }, tip: 'start' },
  { cmd: 'h3', level: 3, mac: '⌥⌘3', aria: { mac: 'Meta+Alt+3', pc: 'Control+Alt+3' }, tip: 'start' },
  { cmd: 'bold', icon: 'bold', mac: '⌘B', aria: { mac: 'Meta+B', pc: 'Control+B' }, group: true },
  { cmd: 'italic', icon: 'italic', mac: '⌘I', aria: { mac: 'Meta+I', pc: 'Control+I' } },
  {
    cmd: 'bulletList',
    icon: 'list-bulleted',
    mac: '⇧⌘8',
    aria: { mac: 'Meta+Shift+8', pc: 'Control+Shift+8' },
    group: true,
    tip: 'end',
  },
  {
    cmd: 'orderedList',
    icon: 'list-numbered',
    mac: '⇧⌘7',
    aria: { mac: 'Meta+Shift+7', pc: 'Control+Shift+7' },
    tip: 'end',
  },
  { cmd: 'link', icon: 'link', mac: '⌘K', aria: { mac: 'Meta+K', pc: 'Control+K' }, group: true, tip: 'end' },
];
