// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ComboboxItem } from './combobox-search';
import { comboboxGroups, comboboxItems, foldText, matchParts } from './combobox-search';
import type { ComboboxKeyTarget } from './combobox-keyboard';
import { comboboxKeydown } from './combobox-keyboard';

describe('combobox: coincidencias, grupos e teclado', () => {
  it('marca a coincidencia sen distinguir acentos nin maiúsculas', () => {
    expect(foldText('Pádel ÑANDÚ')).toBe('padel nandu');
    expect(matchParts('Pista de pádel 1', 'PAD')).toEqual([
      { text: 'Pista de ', match: false },
      { text: 'pád', match: true },
      { text: 'el 1', match: false },
    ]);
    expect(matchParts('Pádel', 'padel')).toEqual([{ text: 'Pádel', match: true }]);
    expect(matchParts('Fútbol', 'tenis')).toEqual([{ text: 'Fútbol', match: false }]);
    expect(matchParts('Fútbol', '  ')).toEqual([{ text: 'Fútbol', match: false }]);
  });

  it('agrupa as filas seguidas do mesmo grupo e deixa «Crear» nun grupo sen título', () => {
    const items = comboboxItems(
      'l',
      [
        { value: 1, label: 'Pádel', group: 'Deportes' },
        { value: 2, label: 'Pista 1', group: 'Instalacións' },
        { value: 3, label: 'Pista 2', group: 'Instalacións' },
      ],
      true,
    );
    const groups = comboboxGroups('l', items);
    expect(groups?.map((g) => [g.id, g.label, g.items.map((i) => i.index)])).toEqual([
      ['l-g0', 'Deportes', [0]],
      ['l-g1', 'Instalacións', [1, 2]],
      ['l-g2', null, [3]],
    ]);
    // Se algunha suxestión vai sen grupo, lista simple.
    expect(comboboxGroups('l', comboboxItems('l', [{ value: 1, label: 'A' }], false))).toBeNull();
    expect(comboboxGroups('l', [] as ComboboxItem<number>[])).toBeNull();
  });

  it('Alt+arriba pecha; Intro sen activa deixa enviar o formulario', () => {
    const calls: string[] = [];
    const target: ComboboxKeyTarget = {
      isOpen: () => true,
      hasText: () => true,
      count: () => 2,
      active: () => -1,
      reopen: () => calls.push('reopen'),
      close: () => calls.push('close'),
      clear: () => calls.push('clear'),
      setActive: (i) => calls.push(`active ${i}`),
      chooseActive: () => false,
    };
    const press = (key: string, init: KeyboardEventInit = {}) => {
      const event = new KeyboardEvent('keydown', { key, cancelable: true, ...init });
      comboboxKeydown(event, target);
      return event.defaultPrevented;
    };
    expect(press('ArrowUp', { altKey: true })).toBe(true);
    expect(press('Enter')).toBe(false);
    expect(press('ArrowUp')).toBe(true);
    expect(press('a')).toBe(false);
    expect(calls).toEqual(['close', 'active 1']);
  });
});
