// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { IconComponent, loadLazySymbols } from './icon.component';
import { LAZY_SYMBOLS } from './lazy-symbols';
import { SYMBOLS } from './material-symbols';

describe('furar-icon', () => {
  function render(name: string) {
    const fixture = TestBed.createComponent(IconComponent);
    fixture.componentRef.setInput('name', name);
    fixture.detectChanges();
    const path = () => (fixture.nativeElement as HTMLElement).querySelector('path')?.getAttribute('d') ?? '';
    return { fixture, path };
  }

  it('as do marco van na carga inicial e píntanse ao momento', () => {
    expect(render('home').path()).toBe(SYMBOLS.home);
  });

  it('o resto chega co seu chunk (lazy-symbols) e a icona píntase ao chegar', async () => {
    const { fixture, path } = render('calendar');
    await loadLazySymbols();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(path()).toBe(LAZY_SYMBOLS.calendar);
  });

  it('o marco leva só as iconas que pinta ao abrir a páxina', () => {
    expect(Object.keys(SYMBOLS).sort()).toEqual(['grid', 'home', 'more', 'shield', 'user']);
    for (const name of Object.keys(SYMBOLS)) expect(name in LAZY_SYMBOLS).toBe(false);
  });
});
