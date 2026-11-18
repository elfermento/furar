// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
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

describe('barra inferior do móbil', () => {
  async function render(session = fakeSession({ manager: 'naron' })) {
    TestBed.configureTestingModule({
      providers: [...provideTestApi(session), provideRouter([{ path: '**', component: HostComponent }])],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, el, settle: () => fixture.whenStable() };
  }
  const items = (nav: Element) =>
    [...nav.querySelectorAll(':scope > ul > li > a')].map((a) => [
      text(a) || a.getAttribute('aria-label'),
      a.getAttribute('href'),
    ]);

  it('o xestor: Instalacións · Reservas · [QR] · Indicadores · Concello, sen «Máis»; axe', async () => {
    const { el } = await render();
    const nav = el.querySelector('nav.f-bottomnav')!;
    expect(items(nav)).toEqual([
      ['Instalacións', '/naron/xestion/instalacions'],
      ['Reservas', '/naron/xestion/reservas'],
      ['Validar QR', '/naron/xestion/validar'],
      ['Indicadores', '/naron/xestion/indicadores'],
      ['Concello', '/naron/xestion/concello'],
    ]);
    expect(nav.querySelector('[data-more]')).toBeNull();
    expect(nav.hasAttribute('data-qr')).toBe(true);
    // En castelán, o nome curto só na barra inferior: «Municipio» (o menú do escritorio, «Ayuntamiento»).
    TestBed.inject(TranslocoService).setActiveLang('es');
    await new Promise((resolve) => setTimeout(resolve));
    await TestBed.inject(Router).navigateByUrl('/naron/xestion/reservas');
    expect(items(nav).at(-1)?.[0]).toBe('Municipio');
    TestBed.inject(TranslocoService).setActiveLang('gl');
    // O QR: só icona, co nome no aria-label e no title.
    const qr = nav.querySelector<HTMLAnchorElement>('[data-bottom-qr]')!;
    expect(qr.getAttribute('aria-label')).toBe('Validar QR');
    expect(qr.getAttribute('title')).toBe('Validar QR');
    expect(qr.closest('li')?.classList).toContain('f-bottomnav__qr-slot');
    await expectNoAxeViolations(el);
  });

  it('Recepción: Reservas · [QR] · Incidencias (os seus apartados, o QR no medio)', async () => {
    const { el } = await render(fakeSession({ staff: 'naron' }));
    const nav = el.querySelector('nav.f-bottomnav')!;
    expect(items(nav)).toEqual([
      ['Reservas', '/naron/xestion/reservas'],
      ['Validar QR', '/naron/xestion/validar'],
      ['Incidencias', '/naron/xestion/incidencias'],
    ]);
  });

  it('o QR leva o anel na súa páxina; «Reservas», nas súas páxinas e no calendario; «Concello», nas súas pestanas', async () => {
    const { el, settle } = await render();
    const router = TestBed.inject(Router);
    const current = () =>
      [...el.querySelectorAll('nav.f-bottomnav a[aria-current="page"]')].map((a) => a.getAttribute('href'));
    await router.navigateByUrl('/naron/xestion/validar');
    await settle();
    expect(current()).toEqual(['/naron/xestion/validar']);
    await router.navigateByUrl('/naron/xestion/calendario');
    await settle();
    expect(current()).toEqual(['/naron/xestion/reservas']);
    await router.navigateByUrl('/naron/xestion/concello/campos/novo');
    await settle();
    expect(current()).toEqual(['/naron/xestion/concello']);
  });

  it('a plataforma: sen botón do QR', async () => {
    const { el } = await render(fakeSession({ platform: true }));
    expect(el.querySelector('nav.f-bottomnav [data-bottom-qr]')).toBeNull();
    expect(el.querySelector('nav.f-bottomnav')?.hasAttribute('data-qr')).toBe(false);
  });
});
