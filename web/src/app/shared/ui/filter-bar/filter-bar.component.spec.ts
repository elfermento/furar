// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { FieldPopoverComponent } from '../field-popover/field-popover.component';
import { FilterChipDirective } from '../filter-chip/filter-chip.directive';
import { SegmentedDirective } from '../segmented/segmented.directive';
import { SelectComponent } from '../select/select.component';
import { FilterBarComponent, FilterFieldComponent } from './filter-bar.component';

@Component({
  imports: [
    FieldPopoverComponent,
    FilterBarComponent,
    FilterChipDirective,
    FilterFieldComponent,
    SegmentedDirective,
    SelectComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-filter-bar [label]="'Filtros do rexistro'">
      <furar-filter-field #view [label]="'Vista'">
        <div furarSegmented role="group" [attr.aria-labelledby]="view.labelId">
          <button type="button" aria-pressed="true">{{ 'Día' }}</button>
          <button type="button" aria-pressed="false">{{ 'Semana' }}</button>
        </div>
      </furar-filter-field>
      <furar-filter-field [label]="'Período'" controlId="period">
        <furar-field-popover look="filter" triggerId="period" [label]="'Período'" [placeholder]="'Último ano'">
          <button type="button">{{ 'Aplicar' }}</button>
        </furar-field-popover>
      </furar-filter-field>
      <furar-filter-field [label]="'Área'" controlId="area">
        <furar-select selectId="area" [label]="'Área'" [options]="areas" [value]="''" />
      </furar-filter-field>
      <button type="button" furarFilterChip="danger" [(pressed)]="failed">{{ 'Só fallidos' }}</button>
    </furar-filter-bar>
  `,
})
class HostComponent {
  protected readonly areas = [
    { value: '', label: 'Todas' },
    { value: 'staff', label: 'Persoal' },
  ];
  readonly failed = signal(false);
}

describe('FilterBarComponent', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('é un grupo co seu nome e cada control leva o rótulo visible no nome (2.5.3)', async () => {
    const { el } = await render();
    const bar = el.querySelector('furar-filter-bar')!;
    expect(bar.getAttribute('role')).toBe('group');
    expect(bar.getAttribute('aria-label')).toBe('Filtros do rexistro');

    const labels = [...el.querySelectorAll('.f-filter-field__label')].map((l) => l.textContent?.trim());
    expect(labels).toEqual(['Vista', 'Período', 'Área']);

    // Grupo segmentado: aria-labelledby ao rótulo (un texto, non un <label>).
    const view = el.querySelector<HTMLElement>('[furarSegmented]')!;
    const viewLabel = el.querySelector(`#${view.getAttribute('aria-labelledby')}`)!;
    expect(viewLabel.tagName).toBe('SPAN');
    expect(viewLabel.textContent?.trim()).toBe('Vista');

    // Campo con panel e despregable: <label for> ao botón e nome «rótulo, valor».
    const period = el.querySelector<HTMLButtonElement>('#period')!;
    expect(el.querySelector('label[for="period"]')?.textContent?.trim()).toBe('Período');
    expect(period.getAttribute('aria-label')).toBe('Período, Último ano');
    expect(period.getAttribute('aria-haspopup')).toBe('dialog');
    expect(period.querySelector('.f-field-popover__label')).toBeNull();
    const area = el.querySelector<HTMLButtonElement>('#area')!;
    expect(el.querySelector('label[for="area"]')?.textContent?.trim()).toBe('Área');
    expect(area.getAttribute('aria-label')).toBe('Área, Todas');
  });

  it('picar no rótulo leva ao control; o chip segue sendo un botón con aria-pressed', async () => {
    const { fixture, el } = await render();
    el.querySelector<HTMLLabelElement>('label[for="area"]')!.click();
    await fixture.whenStable();
    expect(el.querySelector('#area')?.getAttribute('aria-expanded')).toBe('true');

    const chip = el.querySelector<HTMLButtonElement>('.f-filter-chip')!;
    chip.click();
    await fixture.whenStable();
    expect(chip.getAttribute('aria-pressed')).toBe('true');
    expect(fixture.componentInstance.failed()).toBe(true);
  });

  it('pasa axe', async () => {
    const { el } = await render();
    await expectNoAxeViolations(el);
  });
});
