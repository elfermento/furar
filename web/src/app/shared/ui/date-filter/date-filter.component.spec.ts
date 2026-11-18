// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import type { IsoDate } from '../calendar/local-date';
import { DateFilterComponent } from './date-filter.component';

/** O chip dunha soa data dentro dunha capa (a folla de filtros da portada común). */
@Component({
  imports: [DateFilterComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div role="group" aria-label="Folla" (keydown.escape)="escaped.set(true)">
    <furar-date-filter single [(from)]="date" [today]="today" [min]="today" [format]="text" />
  </div>`,
})
class SingleHostComponent {
  readonly date = signal<IsoDate | null>(null);
  readonly today = '2026-11-11';
  readonly text = (date: IsoDate) => `día ${date.slice(8)}`;
  readonly escaped = signal(false);
}

/** As clases e o texto do chip, para comparar que é o mesmo nos dous modos. */
function chipShape(el: HTMLElement): string {
  const shape = (node: Element): string =>
    `${node.tagName.toLowerCase()}.${[...node.classList].join('.')}[${[...node.children].map(shape).join(',')}]`;
  return shape(el.querySelector('.f-mdate__row')!);
}

describe('furar-date-filter', () => {
  async function render(inputs: Record<string, unknown>) {
    TestBed.configureTestingModule({ providers: [...provideTestApi()] });
    const fixture = TestBed.createComponent(DateFilterComponent);
    fixture.componentRef.setInput('today', '2026-11-11');
    for (const [key, value] of Object.entries(inputs)) fixture.componentRef.setInput(key, value);
    fixture.detectChanges();
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('sen atallos propios, os da xestión de reservas', async () => {
    const { fixture, el } = await render({});
    el.querySelector<HTMLButtonElement>('[data-date-add]')?.click();
    await fixture.whenStable();
    expect([...el.querySelectorAll('[data-preset]')].map((b) => b.getAttribute('data-preset'))).toEqual([
      'today',
      'tomorrow',
      'week',
    ]);
  });

  it('cos atallos de entrada e os días fóra de min e max desactivados; chip co aspa dentro; axe', async () => {
    const { fixture, el } = await render({
      from: '2026-10-01',
      to: '2026-10-31',
      min: '2026-10-05',
      max: '2026-11-10',
      presets: [
        { id: 'yesterday', label: 'openData.dates.preset.yesterday', from: '2026-11-10', to: '2026-11-10' },
        { id: 'lastMonth', label: 'openData.dates.preset.lastMonth', from: '2026-10-01', to: '2026-10-31' },
      ],
    });
    const chip = el.querySelector('[data-date-chip]');
    expect(chip?.textContent).toContain('01/10');
    expect(chip?.querySelector('[data-date-clear]')).not.toBeNull();
    chip?.querySelector<HTMLButtonElement>('.f-mdate__chip-open')?.click();
    await fixture.whenStable();
    const presets = [...el.querySelectorAll<HTMLButtonElement>('[data-preset]')];
    expect(presets.map((b) => b.getAttribute('data-preset'))).toEqual(['yesterday', 'lastMonth']);
    expect(presets[1].getAttribute('aria-pressed')).toBe('true');
    presets[0].click();
    await fixture.whenStable();
    expect(fixture.componentInstance.from()).toBe('2026-11-10');
    expect(fixture.componentInstance.to()).toBe('2026-11-10');
    const disabled = [
      ...el.querySelectorAll<HTMLButtonElement>('.f-calendar__day[disabled], .f-calendar__day[aria-disabled="true"]'),
    ];
    expect(disabled.length).toBeGreaterThan(0);
    await expectNoAxeViolations(el);
  });

  it('sen data, o chip «Escoller data» (o mesmo no rango e nunha data)', async () => {
    const { el } = await render({});
    const add = el.querySelector<HTMLButtonElement>('[data-date-add]')!;
    expect(add.textContent?.trim()).toBe('Escoller data');
    expect(add.classList).toContain('f-mdate__add');
    const single = TestBed.createComponent(SingleHostComponent);
    await single.whenStable();
    expect(chipShape(single.nativeElement as HTMLElement)).toBe(chipShape(el));
  });

  it('con data, o mesmo chip co aspa dentro no rango e nunha data', async () => {
    const { el } = await render({ from: '2026-11-14', to: '2026-11-14' });
    const single = TestBed.createComponent(SingleHostComponent);
    single.componentInstance.date.set('2026-11-14');
    await single.whenStable();
    const singleEl = single.nativeElement as HTMLElement;
    expect(chipShape(singleEl)).toBe(chipShape(el));
    expect(chipShape(el)).toContain('span.f-mdate__chip[button.f-mdate__chip-open');
    expect(el.querySelector('[data-date-chip]')?.textContent).toContain('O 14/11');
    expect(singleEl.querySelector('[data-date-chip]')?.textContent).toContain('día 14');
  });

  describe('single (unha data)', () => {
    async function renderSingle() {
      TestBed.configureTestingModule({ providers: [...provideTestApi()] });
      const fixture = TestBed.createComponent(SingleHostComponent);
      await fixture.whenStable();
      const el = fixture.nativeElement as HTMLElement;
      return { fixture, el, settle: () => fixture.whenStable() };
    }

    it('desprega só o calendario, sen días antes de min nin atallos; axe', async () => {
      const { el, settle } = await renderSingle();
      const add = el.querySelector<HTMLButtonElement>('[data-date-add]')!;
      expect(add.getAttribute('aria-expanded')).toBe('false');
      add.click();
      await settle();
      expect(add.getAttribute('aria-expanded')).toBe('true');
      const panel = el.querySelector('[data-date-panel]')!;
      expect(add.getAttribute('aria-controls')).toBe(panel.id);
      expect(panel.querySelector('[data-preset]')).toBeNull();
      expect(panel.querySelector('[data-date-apply]')).toBeNull();
      expect(panel.querySelector('[data-date="2026-11-10"]')?.getAttribute('aria-disabled')).toBe('true');
      await expectNoAxeViolations(el);
    });

    it('escoller un día pecha; a ✕ do chip quítao e o foco volve ao chip', async () => {
      const { fixture, el, settle } = await renderSingle();
      el.querySelector<HTMLButtonElement>('[data-date-add]')!.click();
      await settle();
      el.querySelector<HTMLElement>('[data-date="2026-11-14"]')!.click();
      await settle();
      expect(fixture.componentInstance.date()).toBe('2026-11-14');
      expect(el.querySelector('[data-date-panel]')).toBeNull();
      const chip = el.querySelector('[data-date-chip]')!;
      const clear = chip.querySelector<HTMLButtonElement>('[data-date-clear]')!;
      expect(clear.getAttribute('aria-label')).toBe('Quitar a data');
      await vi.waitFor(() => expect(document.activeElement).toBe(chip.querySelector('.f-mdate__chip-open')));
      clear.click();
      await settle();
      expect(fixture.componentInstance.date()).toBeNull();
      await vi.waitFor(() => expect(document.activeElement).toBe(el.querySelector('[data-date-add]')));
    });

    it('Esc pecha só o calendario (non a capa onde vai) e o foco volve ao chip', async () => {
      const { fixture, el, settle } = await renderSingle();
      const add = el.querySelector<HTMLButtonElement>('[data-date-add]')!;
      add.click();
      await settle();
      el.querySelector('[data-date-panel]')!.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true }),
      );
      await settle();
      expect(el.querySelector('[data-date-panel]')).toBeNull();
      expect(fixture.componentInstance.escaped()).toBe(false);
      await vi.waitFor(() => expect(document.activeElement).toBe(add));
    });

    it('Esc co foco aínda no chip tamén pecha; co panel pechado, Esc segue cara á capa', async () => {
      const { fixture, el, settle } = await renderSingle();
      const add = el.querySelector<HTMLButtonElement>('[data-date-add]')!;
      const esc = () => new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
      add.click();
      await settle();
      add.dispatchEvent(esc());
      await settle();
      expect(el.querySelector('[data-date-panel]')).toBeNull();
      expect(fixture.componentInstance.escaped()).toBe(false);
      add.dispatchEvent(esc());
      await settle();
      expect(fixture.componentInstance.escaped()).toBe(true);
    });
  });
});
