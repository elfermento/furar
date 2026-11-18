// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { backend, fakeSession, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../municipality/current-municipality';
import { ManageNav } from './manage-nav';
import { ManageTabsComponent } from './manage-tabs.component';

@Component({
  imports: [ManageTabsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<main><furar-manage-tabs /><h1>{{ "x" }}</h1></main>',
})
class HostComponent {}

const text = (e: Element | null | undefined) => (e?.textContent ?? '').replace(/\s+/g, ' ').trim();

describe('pestanas do apartado da xestión', () => {
  async function render(url: string, session = fakeSession({ manager: 'naron' })) {
    TestBed.configureTestingModule({
      providers: [...provideTestApi(session), provideRouter([{ path: '**', component: HostComponent }])],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const fixture = TestBed.createComponent(HostComponent);
    await TestBed.inject(Router).navigateByUrl(url);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('en «Reservas», a franxa Reservas · Incidencias · Validar QR coa actual marcada; o título, só para lectores; axe', async () => {
    const { el } = await render('/naron/xestion/reservas?q=ana');
    const strip = el.querySelector('[data-manage-tabs]')!;
    expect(strip.querySelector('nav')?.getAttribute('aria-label')).toBe('Reservas');
    const tabs = [...strip.querySelectorAll('a')];
    expect(tabs.map((a) => [text(a), a.getAttribute('href')])).toEqual([
      ['Reservas', '/naron/xestion/reservas'],
      ['Incidencias', '/naron/xestion/incidencias'],
      ['Validar QR', '/naron/xestion/validar'],
      ['Entidades', '/naron/xestion/entidades'],
    ]);
    expect(tabs[0]!.getAttribute('aria-current')).toBe('page');
    // No móbil, «Validar QR» é o botón central e non vai na franxa: quedan Reservas e Incidencias.
    expect(tabs[2]!.hasAttribute('data-qr')).toBe(true);
    expect(strip.hasAttribute('data-mobile-hidden')).toBe(false);
    expect(TestBed.inject(ManageNav).underTabs()).toBe(true);
    await expectNoAxeViolations(el);
  });

  it('o contador: o número á vista vai aria-hidden e o lector le o que conta («Incidencias, 3 incidencias novas»)', async () => {
    const { el, fixture } = await render('/naron/xestion/reservas');
    const tab = el.querySelector('[data-manage-tab="nav.incidents"]')!;
    await vi.waitFor(async () => {
      for (const read of backend().match((r) => r.url.endsWith('/incidents/new-count'))) read.flush({ count: 3 });
      await fixture.whenStable();
      expect(tab.querySelector('[data-count]')).not.toBeNull();
    });
    expect(tab.querySelector('[data-count]')?.getAttribute('aria-hidden')).toBe('true');
    expect(text(tab.querySelector('[data-count]'))).toBe('3');
    expect(text(tab.querySelector('.sr-only'))).toBe(', 3 incidencias novas');
  });

  it('sen franxa nas páxinas debaixo dunha pestana, nos apartados dunha soa e en Recepción', async () => {
    const { el, fixture } = await render('/naron/xestion/reservas/nova');
    expect(el.querySelector('[data-manage-tabs]')).toBeNull();
    expect(TestBed.inject(ManageNav).underTabs()).toBe(false);
    await TestBed.inject(Router).navigateByUrl('/naron/xestion/indicadores');
    await fixture.whenStable();
    expect(el.querySelector('[data-manage-tabs]')).toBeNull();
  });

  it('Recepción: cada pestana é un apartado, sen franxa', async () => {
    const { el } = await render('/naron/xestion/reservas', fakeSession({ staff: 'naron' }));
    expect(el.querySelector('[data-manage-tabs]')).toBeNull();
  });
});
