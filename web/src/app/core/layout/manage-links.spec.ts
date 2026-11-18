// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { signal } from '@angular/core';
import type { ManageCounter, ManageSectionConfig } from './manage-links';
import { MANAGE_SECTIONS, currentSection, currentTab, manageSections } from './manage-links';

describe('destinos da xestión', () => {
  const paths = (sections: ReturnType<typeof manageSections>) =>
    sections.map((section) => [section.key, section.tabs.map((tab) => tab.path.join('/'))]);

  it('xestor: Instalacións (Instalacións · Eventos) · Reservas (Reservas · Incidencias · Validar QR · Entidades) · Indicadores · Concello', () => {
    expect(paths(manageSections('naron', 'manager'))).toEqual([
      ['nav.facilities', ['//naron/xestion/instalacions', '//naron/xestion/eventos']],
      [
        'nav.bookings',
        [
          '//naron/xestion/reservas',
          '//naron/xestion/incidencias',
          '//naron/xestion/validar',
          '//naron/xestion/entidades',
        ],
      ],
      ['nav.indicators', ['//naron/xestion/indicadores']],
      ['nav.municipality', ['//naron/xestion/concello']],
    ]);
  });

  it('Recepción: cada pestana é un apartado directo (Reservas · Incidencias · Validar QR), sen pestanas', () => {
    expect(paths(manageSections('naron', 'staff'))).toEqual([
      ['nav.bookings', ['//naron/xestion/reservas']],
      ['nav.incidents', ['//naron/xestion/incidencias']],
      ['nav.validate', ['//naron/xestion/validar']],
    ]);
  });

  it('plataforma: Instalacións e «Concello» (ADR-0028, revisión do 2026-11-03), sen Reservas nin Indicadores', () => {
    expect(paths(manageSections('naron', 'platform'))).toEqual([
      ['nav.facilities', ['//naron/xestion/instalacions']],
      ['nav.municipality', ['//naron/xestion/concello']],
    ]);
  });

  it('engadir unha pestana é unha liña: vai no seu apartado, cos permisos e o contador que leve', () => {
    class Counter implements ManageCounter {
      readonly count = signal<number | null>(3);
    }
    const config: ManageSectionConfig[] = MANAGE_SECTIONS.map((section) =>
      section.key === 'nav.bookings'
        ? {
            ...section,
            tabs: [
              section.tabs[0]!,
              { key: 'nav.more', icon: 'warn', path: 'incidencias', counter: Counter },
              section.tabs.find((tab) => tab.qr)!,
            ],
          }
        : section,
    );
    const manager = manageSections('naron', 'manager', config);
    expect(manager[1]!.tabs.map((tab) => tab.path.at(-1))).toEqual(['reservas', 'incidencias', 'validar']);
    expect(manager[1]!.tabs[1]!.counter).toBe(Counter);
    // Recepción: Reservas · Incidencias · Validar QR, apartados directos.
    expect(manageSections('naron', 'staff', config).map((section) => section.path.at(-1))).toEqual([
      'reservas',
      'incidencias',
      'validar',
    ]);
  });

  it('o apartado actual (tamén nas páxinas debaixo e nas de `also`) e a pestana (só a ruta exacta)', () => {
    const sections = manageSections('naron', 'manager');
    expect(currentSection(sections, '/naron/xestion/reservas/nova')?.key).toBe('nav.bookings');
    expect(currentSection(sections, '/naron/xestion/calendario?data=2026-11-11')?.key).toBe('nav.bookings');
    expect(currentSection(sections, '/naron/xestion/concello/persoal')?.key).toBe('nav.municipality');
    expect(currentSection(sections, '/naron/reservas')).toBeNull();
    expect(currentTab(sections, '/naron/xestion/validar?x=1')?.qr).toBe(true);
    expect(currentTab(sections, '/naron/xestion/reservas/nova')).toBeNull();
  });
});
