// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { fakeSession, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../municipality/current-municipality';
import { BottomNavComponent } from './bottom-nav.component';

@Component({
  imports: [BottomNavComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<main><h1>{{ "x" }}</h1><button type="button" data-outside>fóra</button></main><furar-bottom-nav />',
})
class HostComponent {}

const text = (e: Element | null | undefined) => (e?.textContent ?? '').replace(/\s+/g, ' ').trim();

describe('barra inferior do móbil: catro sitios', () => {
  async function render() {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(fakeSession({ manager: 'naron' })),
        provideRouter([{ path: '**', component: HostComponent }]),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, el, settle: () => fixture.whenStable() };
  }

  it('o xestor: Instalacións · Reservas · Indicadores · Concello, sen «Máis»; axe', async () => {
    const { el } = await render();
    const nav = el.querySelector('nav.f-bottomnav')!;
    expect([...nav.querySelectorAll(':scope > ul > li > a')].map((a) => [text(a), a.getAttribute('href')])).toEqual([
      ['Instalacións', '/naron/xestion/instalacions'],
      ['Reservas', '/naron/xestion/reservas'],
      ['Indicadores', '/naron/xestion/indicadores'],
      ['Concello', '/naron/xestion/concello'],
    ]);
    expect(nav.querySelector('[data-more]')).toBeNull();
    await expectNoAxeViolations(el);
  });

  it('«Concello» vai activa en calquera das súas pestanas', async () => {
    const { el, settle } = await render();
    await TestBed.inject(Router).navigateByUrl('/naron/xestion/concello/campos/novo');
    await settle();
    const current = el.querySelector('nav.f-bottomnav a[aria-current="page"]');
    expect(current?.getAttribute('href')).toBe('/naron/xestion/concello');
  });
});
