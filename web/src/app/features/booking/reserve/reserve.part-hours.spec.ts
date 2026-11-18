// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, fakeSession, flushMatching, provideTestApi } from '../../../../testing/api';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import {
  AVAILABILITY_URL,
  BLOCK_URL,
  DAY,
  FACILITY_URL,
  FIELDS_URL,
  NOTES,
  NOTES_URL,
  NOT_BLOCKED,
  availability,
  busy,
  facility,
  slot,
} from './reserve.fixture';
import { ReservePage } from './reserve.page';

/** A instalación co campo enteiro (de hora en hora) como uso principal e Fútbol 7 por metades. */
function wholeFirst(sports = false) {
  const [parts, whole] = facility().uses;
  return facility({
    uses: [
      { ...whole, slotMinutes: 60, sports: sports ? ['football'] : [] },
      { ...parts, sports: sports ? ['football_7'] : [] },
    ],
  });
}

describe('Páxina de reservar: horas con só unha parte libre', () => {
  afterEach(() => {
    flushMatching((r) => r.url.endsWith('/me/organizations'), []);
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  const forUse = (useId: string) => (r: { url: string; params: { get(name: string): string | null } }) =>
    r.url === AVAILABILITY_URL && r.params.get('useId') === useId;
  const query = () => new URLSearchParams(TestBed.inject(Router).url.split('?')[1] ?? '');

  async function render() {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(fakeSession({})),
        provideRouter(
          [{ path: 'naron/instalacions/:id/reservar', component: ReservePage }],
          withComponentInputBinding(),
        ),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create(`/naron/instalacions/f1/reservar?data=${DAY}`);
    const el = harness.routeNativeElement!;
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    (await expectRequest(NOTES_URL)).flush(NOTES);
    (await expectRequest(BLOCK_URL)).flush(NOT_BLOCKED);
    flushMatching((r) => r.url === FIELDS_URL, []);
    (await expectRequest(FACILITY_URL)).flush(wholeFirst());
    // Enteiro: 18:00 libre; 19:00 e 20:00 ocupadas.
    (await expectRequest(forUse('u2'))).flush({ ...availability([slot(1080, 1)]), busy: [busy(1140), busy(1200)] });
    // Por metades: ás 19:00 só queda a sur; ás 20:00, ningunha.
    (await expectRequest(forUse('u1'))).flush(availability([slot(1080, 1, ['p1', 'p2']), slot(1140, 1, ['p2'])]));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-reserve-field="time"]')).not.toBeNull();
    });
    el.querySelector<HTMLButtonElement>('[data-reserve-field="time"] .f-field-popover__trigger')!.click();
    await settle();
    return { el, settle };
  }

  it('«Só unha parte» onde o enteiro está ocupado e queda unha metade; ao tocala, pasa a esa metade', async () => {
    const { el, settle } = await render();
    const hours = [...document.querySelectorAll<HTMLButtonElement>('.f-reserve__slot')];
    expect(
      hours.map((hour) => [hour.querySelector('b')?.textContent, hour.querySelector('small')?.textContent?.trim()]),
    ).toEqual([
      ['18:00', undefined],
      ['19:00', 'Só unha parte'],
      ['20:00', 'Ocupada'],
    ]);
    expect(hours[1].hasAttribute('data-busy')).toBe(false);
    expect(hours[2].hasAttribute('data-busy')).toBe(true);

    hours[1].click();
    (await expectRequest(forUse('u1'))).flush(availability([slot(1080, 1, ['p1', 'p2']), slot(1140, 1, ['p2'])]));
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-reserve-field="time"] .f-field-popover__value')?.textContent?.trim()).toBe(
        '19:00–20:00',
      );
    });
    expect(query().get('uso')).toBe('u1');
    expect(query().get('hora')).toBe('19:00');
    expect(query().get('parte')).toBe('p2');
    expect(el.querySelector('[data-reserve-field="what"]')?.textContent).toContain('Fútbol 7');
    expect(el.querySelector('[data-reserve-field="part"]')?.textContent).toContain('Metade sur');
  });

  it('vén dunha busca por deporte (`?deporte=`): abre o primeiro uso del e non suxire os doutro deporte', async () => {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(fakeSession({})),
        provideRouter(
          [{ path: 'naron/instalacions/:id/reservar', component: ReservePage }],
          withComponentInputBinding(),
        ),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create(
      `/naron/instalacions/f1/reservar?data=${DAY}&hora=19:00&deporte=football_7`,
    );
    const el = harness.routeNativeElement!;
    (await expectRequest(NOTES_URL)).flush(NOTES);
    (await expectRequest(BLOCK_URL)).flush(NOT_BLOCKED);
    flushMatching((r) => r.url === FIELDS_URL, []);
    (await expectRequest(FACILITY_URL)).flush(wholeFirst(true));
    // Só se mira o uso de fútbol 7 (o enteiro é de fútbol).
    const halves = availability([slot(1080, 1, ['p1', 'p2']), slot(1140, 1, ['p2'])]);
    (await expectRequest(forUse('u1'))).flush(halves);
    (await expectRequest(forUse('u1'))).flush(halves);
    await vi.waitFor(async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
      expect(el.querySelector('[data-reserve-field="time"] .f-field-popover__value')?.textContent?.trim()).toBe(
        '19:00–20:00',
      );
    });
    expect(el.querySelector('[data-reserve-field="what"]')?.textContent).toContain('Fútbol 7');
    expect(query().get('deporte')).toBe('football_7');
  });
});
