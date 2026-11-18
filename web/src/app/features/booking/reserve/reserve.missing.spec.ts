// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter, withComponentInputBinding } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { backend, expectRequest, fakeSession, provideTestApi } from '../../../../testing/api';
import { municipality } from '../../../../testing/fixtures';
import { flushFields, flushNoOrganizations } from '../../../../testing/reserve';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { AVAILABILITY_URL, DAY, FACILITY_URL, NOTES, NOTES_URL, availability, facility } from './reserve.fixture';
import { ReservePage } from './reserve.page';

/** «Continuar» nunca se desactiva: sen hora, abre directamente a Hora. */
describe('Páxina de reservar: «Continuar» sen hora', () => {
  afterEach(() => {
    flushNoOrganizations();
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  const url = `/naron/instalacions/f1/reservar?uso=u1&data=${DAY}`;
  const field = (el: HTMLElement, name: string) =>
    el.querySelector<HTMLButtonElement>(`[data-reserve-field="${name}"] .f-field-popover__trigger`)!;
  const dialog = () => document.querySelector<HTMLElement>('[role="dialog"]');

  async function render() {
    const session = { ...fakeSession({}), authenticated: () => false } as never;
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(session),
        provideRouter(
          [{ path: 'naron/instalacions/:id/reservar', component: ReservePage }],
          withComponentInputBinding(),
        ),
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const harness = await RouterTestingHarness.create(url);
    const el = harness.routeNativeElement!;
    const settle = async () => {
      await harness.fixture.whenStable();
      harness.detectChanges();
    };
    (await expectRequest(NOTES_URL)).flush(NOTES);
    flushFields([]);
    (await expectRequest(FACILITY_URL)).flush(facility());
    (await expectRequest((r) => r.url === AVAILABILITY_URL)).flush(availability());
    await vi.waitFor(async () => {
      await settle();
      expect(el.querySelector('[data-reserve-go]')).not.toBeNull();
    });
    return { el, settle };
  }

  it('activo; premido, abre a Hora co foco dentro, sen erro, sen navegar nin pedir nada', async () => {
    const { el, settle } = await render();
    const go = el.querySelector<HTMLButtonElement>('[data-reserve-go]')!;
    expect(go.disabled).toBe(false);
    expect(go.getAttribute('aria-disabled')).toBeNull();
    go.click();
    await settle();
    expect(field(el, 'time').getAttribute('aria-expanded')).toBe('true');
    expect(dialog()?.contains(document.activeElement)).toBe(true);
    expect(dialog()?.querySelector('[data-reserve-slot]')).not.toBeNull();
    expect(field(el, 'time').getAttribute('aria-invalid')).toBeNull();
    expect(TestBed.inject(Router).url).toBe(url);
    // Escoller a hora pasa ao campo seguinte, coma sempre: a Hora xa ten valor.
    dialog()!.querySelector<HTMLButtonElement>('[data-reserve-slot]')!.click();
    await settle();
    expect(field(el, 'time').querySelector('.f-field-popover__value')?.textContent?.trim()).not.toBe('Escoller');
  });
});
