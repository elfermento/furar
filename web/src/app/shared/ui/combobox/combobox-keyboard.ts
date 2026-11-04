// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { nextActive } from './combobox-search';

/** O que precisa o teclado do combobox do compoñente (sen DOM). */
export interface ComboboxKeyTarget {
  readonly isOpen: () => boolean;
  readonly hasText: () => boolean;
  readonly count: () => number;
  readonly active: () => number;
  readonly reopen: () => void;
  readonly close: () => void;
  readonly clear: () => void;
  readonly setActive: (index: number) => void;
  /** Escolle a fila activa; false se non hai ningunha. */
  readonly chooseActive: () => boolean;
}

/**
 * Teclado do patrón combobox do APG: frecha abaixo/arriba abren e moven (dan a volta); Alt+abaixo
 * abre sen mover e Alt+arriba pecha; Intro escolle a activa (sen activa, o formulario envíase);
 * Esc pecha e, xa pechado, borra o texto; Tab pecha sen escoller.
 */
export function comboboxKeydown(event: KeyboardEvent, target: ComboboxKeyTarget): void {
  switch (event.key) {
    case 'ArrowDown':
    case 'ArrowUp': {
      event.preventDefault();
      if (event.altKey && event.key === 'ArrowUp') {
        target.close();
        return;
      }
      if (!target.isOpen()) {
        target.reopen();
      }
      const count = target.count();
      if (!event.altKey && count > 0) {
        target.setActive(nextActive(event.key === 'ArrowDown', target.active(), count));
      }
      return;
    }
    case 'Enter':
      if (target.isOpen() && target.chooseActive()) {
        event.preventDefault();
      }
      return;
    case 'Escape':
      if (target.isOpen() || target.hasText()) {
        event.preventDefault();
        event.stopPropagation();
        if (target.isOpen()) {
          target.close();
        } else {
          target.clear();
        }
      }
      return;
    case 'Tab':
      target.close();
      return;
    default:
      return;
  }
}
