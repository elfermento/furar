// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { provideRouter } from '@angular/router';
import { provideTestI18n } from '../../../testing/i18n';
import type { ManageCounter, ManageSection, ManageTab } from './manage-links';
import { ManageNav } from './manage-nav';

@Injectable({ providedIn: 'root' })
class Incidents implements ManageCounter {
  readonly count = signal<number | null>(3);
}
@Injectable({ providedIn: 'root' })
class Requests implements ManageCounter {
  readonly count = signal<number | null>(1);
}

const tab = (key: string, counter: ManageTab['counter'], counterText: string | null): ManageTab => ({
  key,
  icon: 'warn',
  path: [key],
  paths: [[key]],
  counter,
  counterText,
  qr: false,
});

describe('ManageNav: o que di cada contador ao lector de pantalla', () => {
  const incidents = tab('nav.incidents', Incidents, 'incidents');
  const requests = tab('nav.organizations', Requests, 'seriesRequests');
  const plain = tab('nav.bookings', null, null);
  const section: ManageSection = { key: 'nav.bookings', icon: 'warn', path: ['x'], tabs: [plain, incidents, requests] };

  function nav() {
    TestBed.configureTestingModule({ providers: [provideTestI18n(), provideRouter([])] });
    return TestBed.inject(ManageNav);
  }

  it('o número co que conta e o plural: «3 incidencias novas», «1 solicitude de tempada pendente»', () => {
    const manage = nav();
    expect(manage.countText(incidents)).toBe('3 incidencias novas');
    expect(manage.countText(requests)).toBe('1 solicitude de tempada pendente');
    expect(manage.countText(plain)).toBe('');
    TestBed.inject(Incidents).count.set(1);
    expect(manage.countText(incidents)).toBe('1 incidencia nova');
  });

  it('o apartado xunta os das súas pestanas («… e …»); en castelán, «… y …»; sen número, nada', () => {
    const manage = nav();
    expect(manage.sectionCount(section)).toBe(4);
    expect(manage.sectionCountText(section)).toBe('3 incidencias novas e 1 solicitude de tempada pendente');
    TestBed.inject(TranslocoService).setActiveLang('es');
    expect(manage.sectionCountText(section)).toBe('3 incidencias nuevas y 1 solicitud de temporada pendiente');
    TestBed.inject(Incidents).count.set(0);
    TestBed.inject(Requests).count.set(null);
    expect(manage.sectionCountText(section)).toBe('');
  });
});
