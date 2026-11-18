// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { SegmentedRadioDirective, SegmentedOptionDirective } from '../segmented/segmented-radio.directive';
import { SegmentedDirective } from '../segmented/segmented.directive';
import { FieldLineComponent } from './field-line.component';

@Component({
  imports: [FieldLineComponent, SegmentedDirective, SegmentedRadioDirective, SegmentedOptionDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-field-line #line label="Idioma por defecto" tip="O da web se a persoa non escolle outro.">
      <div furarSegmented mode="radio" [attr.aria-labelledby]="line.labelId()" [(value)]="lang">
        <button type="button" furarSegmentedOption="gl">Galego</button>
        <button type="button" furarSegmentedOption="es">Castelán</button>
      </div>
    </furar-field-line>
    <furar-field-line labelId="pay" label="Pago" required><button type="button">Pendente</button></furar-field-line>
  `,
})
class HostComponent {
  readonly lang = signal<string | null>('gl');
}

describe('furar-field-line', () => {
  it('`wrap="start"`: se o control baixa á liña seguinte, vai á esquerda (data-wrap)', async () => {
    @Component({
      imports: [FieldLineComponent],
      changeDetection: ChangeDetectionStrategy.OnPush,
      template: `<furar-field-line label="Tipo de reserva" wrap="start"
        ><button type="button">Unha vez</button></furar-field-line
      >`,
    })
    class StartHost {}
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(StartHost);
    await fixture.whenStable();
    const line = (fixture.nativeElement as HTMLElement).querySelector('furar-field-line')!;
    expect(line.getAttribute('data-wrap')).toBe('start');
  });

  it('texto á esquerda co seu id, o «?» tras el e o control proxectado á dereita, nomeado polo texto', async () => {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const [first, second] = [...el.querySelectorAll<HTMLElement>('furar-field-line')];
    expect(first.classList).toContain('f-field-line');
    const text = first.querySelector<HTMLElement>('.f-field-line__label')!;
    expect(text.textContent?.trim()).toBe('Idioma por defecto');
    const group = first.querySelector<HTMLElement>('.f-field-line__control [role="radiogroup"]')!;
    expect(group.getAttribute('aria-labelledby')).toBe(text.id);
    expect(first.querySelector('.f-field-line__text furar-help-tip')).not.toBeNull();
    expect(second.querySelector('.f-field-line__label')!.id).toBe('pay');
    expect(second.querySelector('furar-required-mark')).not.toBeNull();
    // Por defecto, se baixa, á dereita: sen marca; con `wrap="start"`, á esquerda.
    expect(first.hasAttribute('data-wrap')).toBe(false);
    await expectNoAxeViolations(el);
  });
});
