// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { SLUG_BASE, seasonFacility } from '../../../../testing/seasons';
import { pickOption, selectText } from '../../../../testing/select';
import { SeasonFormComponent } from './season-form.component';
import type { DraftErrors, SeasonDraft } from './seasons';
import { EMPTY_DRAFT } from './seasons';

@Component({
  selector: 'furar-test-host',
  imports: [SeasonFormComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-season-form slug="naron" note [(draft)]="draft" [errors]="errors()" />`,
})
class HostComponent {
  readonly draft = signal<SeasonDraft>(EMPTY_DRAFT);
  readonly errors = signal<DraftErrors>({});
}

describe('Os campos dunha tempada', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-12T10:00:00Z'));
  });
  afterEach(() => {
    vi.useRealTimers();
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  const settle = async (fixture: { whenStable: () => Promise<unknown>; detectChanges: () => void }) => {
    await new Promise((resolve) => setTimeout(resolve));
    await fixture.whenStable();
    fixture.detectChanges();
  };
  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();

  async function render(
    facilities: object[] | 'error' = [
      { id: 'f1', name: 'Pavillón' },
      { id: 'f2', name: 'Campo' },
    ],
  ) {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const list = await expectRequest(`${SLUG_BASE}/public/facilities`);
    if (facilities === 'error') flushProblem(list, 500, 'common.unexpected');
    else list.flush(facilities);
    await settle(fixture);
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, el, host: fixture.componentInstance, set: () => settle(fixture) };
  }

  it('instalación → que (sen os usos por prazas; cun só uso, xa escollido) → parte; a nota; axe', async () => {
    const { fixture, el, host, set } = await render();
    expect(el.querySelector('[data-season-organization]')).toBeNull();
    await pickOption(el.querySelector('[data-season-facility]')!, 'Pavillón', set);
    (await expectRequest(`${SLUG_BASE}/public/facilities/f1`)).flush(seasonFacility());
    await settle(fixture);
    // Só «Baloncesto» vale (Ximnasia vai por prazas): xa escollido, e pide a parte.
    expect(host.draft().useId).toBe('u1');
    expect(selectText(el.querySelector('[data-season-use]')!)).toBe('Baloncesto');
    await pickOption(el.querySelector('[data-season-part]')!, 'Pista central', set);
    expect(host.draft().partId).toBe('p1');
    const note = el.querySelector<HTMLTextAreaElement>('[data-season-note]')!;
    note.value = 'Infantil';
    note.dispatchEvent(new Event('input'));
    expect(host.draft().note).toBe('Infantil');
    await expectNoAxeViolations(el);

    // Outra instalación: o uso e a parte empezan de novo; se a súa ficha falla, sen usos.
    await pickOption(el.querySelector('[data-season-facility]')!, 'Campo', set);
    expect(host.draft()).toMatchObject({ facilityId: 'f2', useId: null, partId: null });
    flushProblem(await expectRequest(`${SLUG_BASE}/public/facilities/f2`), 404, 'facility.not-found');
    await settle(fixture);
    expect(el.querySelector('[data-season-part]')).toBeNull();
  });

  it('os días no seu panel; as datas co calendario de rango e «Aplicar» (un día só tamén vale)', async () => {
    const { fixture, el, host } = await render();
    el.querySelector<HTMLButtonElement>('[data-season-days] .f-field-popover__trigger')!.click();
    await settle(fixture);
    const workdays = [...document.querySelectorAll<HTMLButtonElement>('.f-weekdays__shortcut')][1]!;
    workdays.click();
    await settle(fixture);
    expect(host.draft().weekdays).toBe(0b0011111);
    expect(text(el.querySelector('[data-season-days] .f-field-popover__value'))).toBe(
      'Luns, martes, mércores, xoves e venres',
    );
    el.querySelector<HTMLButtonElement>('[data-season-days] .f-field-popover__trigger')!.click();
    await settle(fixture);

    el.querySelector<HTMLButtonElement>('[data-season-dates] .f-field-popover__trigger')!.click();
    await settle(fixture);
    document.querySelector<HTMLButtonElement>('[data-date="2026-11-16"]')!.click();
    await settle(fixture);
    document.querySelector<HTMLButtonElement>('[data-date="2026-11-30"]')!.click();
    await settle(fixture);
    document.querySelector<HTMLButtonElement>('[data-season-apply]')!.click();
    await settle(fixture);
    expect(host.draft()).toMatchObject({ firstDate: '2026-11-16', lastDate: '2026-11-30' });
    expect(text(el.querySelector('[data-season-dates] .f-field-popover__value'))).toBe('Do 16 nov. ao 30 nov.');

    el.querySelector<HTMLButtonElement>('[data-season-dates] .f-field-popover__trigger')!.click();
    await settle(fixture);
    document.querySelector<HTMLButtonElement>('[data-date="2026-11-20"]')!.click();
    await settle(fixture);
    document.querySelector<HTMLButtonElement>('[data-season-apply]')!.click();
    await settle(fixture);
    expect(host.draft()).toMatchObject({ firstDate: '2026-11-20', lastDate: '2026-11-20' });
  });

  it('«Aplicar» as datas sen ningún día segue activo: aviso enriba, o panel aberto e o foco no calendario', async () => {
    const { fixture, el } = await render();
    el.querySelector<HTMLButtonElement>('[data-season-dates] .f-field-popover__trigger')!.click();
    await settle(fixture);
    const apply = document.querySelector<HTMLButtonElement>('[data-season-apply]')!;
    expect(apply.disabled).toBe(false);
    apply.click();
    await settle(fixture);
    expect(text(document.querySelector('[data-season-pick-first]'))).toBe('Escolle o primeiro día.');
    expect(document.querySelector('[data-season-apply]')).not.toBeNull();
    expect(document.querySelector('furar-calendar')?.contains(document.activeElement)).toBe(true);
    document.querySelector<HTMLButtonElement>('[data-date="2026-11-20"]')!.click();
    await settle(fixture);
    expect(document.querySelector('[data-season-pick-first]')).toBeNull();
  });

  /** Escolle unha hora da lista aberta co teclado (Intro na opción). */
  const choose = (time: string) =>
    [...document.querySelectorAll<HTMLElement>('[role="option"]')]
      .find((option) => text(option) === time)!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true }));

  it('as horas: un campo «Horas» co panel «Desde – Ata» (de 15 en 15); o valor, «19:00–20:30»', async () => {
    const { fixture, el, host } = await render();
    expect(el.querySelector('[data-season-start], [data-season-end]')).toBeNull();
    const trigger = el.querySelector<HTMLButtonElement>('[data-season-hours] .f-field-popover__trigger')!;
    expect(text(trigger)).toContain('Horas');
    trigger.click();
    await settle(fixture);
    const from = document.querySelector<HTMLButtonElement>('[data-season-start] button')!;
    const to = document.querySelector<HTMLButtonElement>('[data-season-end] button')!;
    expect(from.getAttribute('aria-label')).toContain('Desde');
    expect(to.getAttribute('aria-label')).toContain('Ata');
    from.click();
    await settle(fixture);
    choose('19:00');
    await settle(fixture);
    expect(host.draft().startMinute).toBe(1140);
    expect(text(el.querySelector('[data-season-hours] .f-field-popover__value'))).toBe('19:00–');
    to.click();
    await settle(fixture);
    choose('20:30');
    await settle(fixture);
    expect(host.draft()).toMatchObject({ startMinute: 1140, endMinute: 1230 });
    expect(text(el.querySelector('[data-season-hours] .f-field-popover__value'))).toBe('19:00–20:30');
    await expectNoAxeViolations(document.body);
  });

  it('o que vén de fóra (unha solicitude) pasa aos controis; os erros debaixo de cada campo', async () => {
    const { fixture, el, host } = await render('error');
    host.draft.set({ ...EMPTY_DRAFT, weekdays: 0b10000, startMinute: 1080, endMinute: 1170 });
    host.errors.set({
      facility: { key: 'errors.validation.required', params: {} },
      weekdays: { key: 'errors.booking.series-weekdays-required', params: {} },
      hours: { key: 'errors.booking.series-time-invalid', params: {} },
      dates: { key: 'errors.booking.series-range-invalid', params: {} },
      note: { key: 'errors.customfield.too-long', params: { max: 500 } },
    });
    await settle(fixture);
    expect(text(el.querySelector('[data-season-hours] .f-field-popover__value'))).toBe('18:00–19:30');
    expect(el.querySelector('[data-season-hours] .f-field-popover__trigger')?.getAttribute('aria-invalid')).toBe(
      'true',
    );
    expect(text(el.querySelector('[data-season-days] .f-field-popover__value'))).toBe('Venres');
    expect(el.querySelector('[id$="-facility-error"]')).not.toBeNull();
    expect(text(el)).toContain('Escolle polo menos un día da semana.');
    expect(text(el)).toContain('As horas van de 15 en 15 minutos');
    expect(text(el)).toContain('As datas van desde hoxe e como moito un ano.');
    expect(el.querySelector('[data-season-note]')?.getAttribute('aria-invalid')).toBe('true');
    await expectNoAxeViolations(el);
  });
});
