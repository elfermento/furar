// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ScrollHide } from './scroll-hide';

describe('barra que se esconde ao baixar', () => {
  it('escóndese ao baixar 8 px e volve ao subir 8 px, sen pestanexar cun tremor', () => {
    const bar = new ScrollHide();
    bar.update(5);
    expect(bar.hidden()).toBe(false);
    bar.update(13);
    expect(bar.hidden()).toBe(true);
    bar.update(13);
    expect(bar.hidden()).toBe(true);
    // Un tremor de poucos píxeles cara arriba non a trae; seguir subindo, si.
    bar.update(9);
    expect(bar.hidden()).toBe(true);
    bar.update(5);
    expect(bar.hidden()).toBe(false);
    // E ao revés: baixar pouco non a leva.
    bar.update(10);
    expect(bar.hidden()).toBe(false);
    bar.update(400);
    expect(bar.hidden()).toBe(true);
    bar.update(395);
    expect(bar.hidden()).toBe(true);
    bar.update(390);
    expect(bar.hidden()).toBe(false);
  });

  it('arriba de todo (ata o alto da barra) vese sempre', () => {
    const bar = new ScrollHide();
    bar.update(60, { top: 72 });
    expect(bar.hidden()).toBe(false);
    bar.update(300, { top: 72 });
    expect(bar.hidden()).toBe(true);
    bar.update(500, { top: 72 });
    bar.update(0, { top: 72 });
    expect(bar.hidden()).toBe(false);
  });

  it('cun menú aberto non se esconde', () => {
    const bar = new ScrollHide();
    bar.update(300, { pinned: true });
    bar.update(600, { pinned: true });
    expect(bar.hidden()).toBe(false);
    bar.update(700);
    expect(bar.hidden()).toBe(true);
  });

  it('co foco dentro vese sempre e, ao saír, segue á vista ata baixar outra vez', () => {
    const bar = new ScrollHide();
    bar.update(300);
    bar.update(500);
    expect(bar.hidden()).toBe(true);
    bar.focus(true);
    expect(bar.hidden()).toBe(false);
    bar.update(800);
    expect(bar.hidden()).toBe(false);
    bar.focus(false);
    expect(bar.hidden()).toBe(true);
    bar.update(700);
    expect(bar.hidden()).toBe(false);
  });
});
