// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { provideTestApi } from '../../../../testing/api';
import { PublicFacilityCardComponent } from './public-facility-card.component';
import type { PublicFacility } from './public-facility.store';

function slot(date: string, start: number, end: number) {
  const at = (minute: number) => new Date(new Date(`${date}T00:00:00Z`).getTime() + minute * 60_000).toISOString();
  return { startMinute: start, endMinute: end, startsAt: at(start), endsAt: at(end) };
}

const DATES = ['2026-10-26', '2026-10-27', '2026-10-28', '2026-10-29', '2026-10-30', '2026-10-31', '2026-11-01'];

function facility(hours: (date: string, i: number) => ReturnType<typeof slot>[], extra: Partial<PublicFacility> = {}) {
  return {
    id: 'f1',
    name: 'Pista',
    address: { street: 'Rúa Buxo', postalCode: '15572', locality: 'Narón', latitude: 43.5, longitude: -8.2 },
    temporarilyClosed: false,
    images: [],
    uses: [],
    amenities: [],
    openingHours: DATES.map((date, i) => ({ date, slots: hours(date, i) })),
    ...extra,
  } as PublicFacility;
}

describe('Tarxeta da ficha pública: horario e contacto', () => {
  function render(value: PublicFacility, now: string) {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    const fixture = TestBed.createComponent(PublicFacilityCardComponent);
    fixture.componentRef.setInput('facility', value);
    fixture.componentRef.setInput('now', new Date(now));
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    return {
      el,
      now: () => el.querySelector('[data-public-now]')?.textContent?.replace(/\s+/g, ' ').trim(),
      terms: () => [...el.querySelectorAll('dt')].map((dt) => dt.textContent?.trim()),
    };
  }

  it('aberto agora: pecha ás …; os días xúntanse («Sábado e domingo»)', () => {
    const { now, terms, el } = render(
      facility((date, i) => (i < 5 ? [slot(date, 540, 1320)] : [slot(date, 600, 840)])),
      '2026-10-26T10:00:00Z',
    );
    expect(now()).toBe('Aberto agora Pecha ás 22:00');
    expect(el.querySelector('[data-public-now]')?.hasAttribute('data-open')).toBe(true);
    expect(terms()).toEqual(['Hoxe, luns', 'Martes a venres', 'Sábado e domingo']);
    // Sen teléfono nin correo, sen bloque de contacto.
    expect(el.textContent).not.toContain('Contacto');
  });

  it('pechado agora: abre hoxe, mañá ou outro día; ou sen máis', () => {
    const later = (date: string, i: number) => (i === 0 ? [slot(date, 960, 1200)] : []);
    expect(render(facility(later), '2026-10-26T08:00:00Z').now()).toBe('Pechado agora Abre hoxe ás 16:00');
    TestBed.resetTestingModule();
    const tomorrow = (date: string, i: number) => (i === 1 ? [slot(date, 540, 600)] : []);
    expect(render(facility(tomorrow), '2026-10-26T08:00:00Z').now()).toBe('Pechado agora Abre mañá ás 9:00');
    TestBed.resetTestingModule();
    const thursday = (date: string, i: number) => (i === 3 ? [slot(date, 540, 600)] : []);
    expect(render(facility(thursday), '2026-10-26T08:00:00Z').now()).toBe('Pechado agora Abre o xoves ás 9:00');
    TestBed.resetTestingModule();
    expect(
      render(
        facility(() => []),
        '2026-10-26T08:00:00Z',
      ).now(),
    ).toBe('Pechado agora');
  });
});
