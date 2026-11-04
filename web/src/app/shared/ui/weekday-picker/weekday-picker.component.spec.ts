// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { LanguageService } from '../../../core/i18n/language.service';
import type { WeekdayMask } from './weekday-picker.component';
import { ALL_WEEKDAYS, MONDAY_TO_FRIDAY, WEEKEND, WeekdayPickerComponent } from './weekday-picker.component';

@Component({
  imports: [ReactiveFormsModule, WeekdayPickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-weekday-picker [formControl]="days" [label]="'Días'" />`,
})
class HostComponent {
  readonly days = new FormControl<WeekdayMask>(MONDAY_TO_FRIDAY, { nonNullable: true });
}

describe('furar-weekday-picker', () => {
  async function render(lang: 'gl' | 'es' = 'gl') {
    TestBed.configureTestingModule({ providers: [provideTestI18n(lang)] });
    const fixture = TestBed.createComponent(HostComponent);
    await TestBed.inject(LanguageService).use(lang);
    await fixture.whenStable();
    return fixture;
  }

  const el = (fixture: ComponentFixture<HostComponent>) => fixture.nativeElement as HTMLElement;
  const days = (fixture: ComponentFixture<HostComponent>) => [
    ...el(fixture).querySelectorAll<HTMLButtonElement>('[role="checkbox"]'),
  ];
  const shortcuts = (fixture: ComponentFixture<HostComponent>) => [
    ...el(fixture).querySelectorAll<HTMLButtonElement>('.f-weekdays__shortcut'),
  ];
  const checked = (fixture: ComponentFixture<HostComponent>) =>
    days(fixture).map((day) => day.getAttribute('aria-checked') === 'true');

  it('L–D por Intl no idioma activo, cada día co nome enteiro', async () => {
    const gl = await render('gl');
    expect(days(gl).map((day) => day.textContent?.trim())).toEqual(['L', 'M', 'M', 'X', 'V', 'S', 'D']);
    expect(days(gl)[0]?.getAttribute('aria-label')).toBe('luns');
    const picker = el(gl).querySelector('furar-weekday-picker')!;
    expect(picker.getAttribute('role')).toBe('group');
    expect(picker.getAttribute('aria-label')).toBe('Días');
    await TestBed.inject(LanguageService).use('es');
    await gl.whenStable();
    expect(days(gl).map((day) => day.textContent?.trim())).toEqual(['L', 'M', 'X', 'J', 'V', 'S', 'D']);
    expect(shortcuts(gl).map((button) => button.textContent?.trim())).toEqual([
      'Todos',
      'Lunes a viernes',
      'Fines de semana',
    ]);
  });

  it('pulsar un día cambia a máscara do control e o seu aria-checked', async () => {
    const fixture = await render();
    expect(checked(fixture)).toEqual([true, true, true, true, true, false, false]);
    days(fixture)[6]?.click();
    days(fixture)[0]?.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.days.value).toBe(0b1011110);
    expect(checked(fixture)).toEqual([false, true, true, true, true, false, true]);
    expect(fixture.componentInstance.days.touched).toBe(true);
  });

  it('os atallos poñen a máscara e marcan o que coincide (aria-pressed)', async () => {
    const fixture = await render();
    const pressed = () => shortcuts(fixture).map((button) => button.getAttribute('aria-pressed'));
    expect(shortcuts(fixture).map((button) => button.textContent?.trim())).toEqual([
      'Todos',
      'Luns a venres',
      'Fins de semana',
    ]);
    expect(pressed()).toEqual(['false', 'true', 'false']);
    shortcuts(fixture)[2]?.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.days.value).toBe(WEEKEND);
    expect(pressed()).toEqual(['false', 'false', 'true']);
    shortcuts(fixture)[0]?.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.days.value).toBe(ALL_WEEKDAYS);
    days(fixture)[3]?.click();
    await fixture.whenStable();
    expect(pressed()).toEqual(['false', 'false', 'false']);
  });

  it('desactivado co formulario e con valores fóra de rango limpos', async () => {
    const fixture = await render();
    fixture.componentInstance.days.setValue(0b11111111);
    fixture.componentInstance.days.disable();
    await fixture.whenStable();
    expect(checked(fixture).every(Boolean)).toBe(true);
    expect(days(fixture).every((day) => day.disabled)).toBe(true);
    expect(shortcuts(fixture).every((button) => button.disabled)).toBe(true);
  });

  it.each(['gl', 'es'] as const)('pasa axe (%s)', async (lang) => {
    const fixture = await render(lang);
    await expectNoAxeViolations(el(fixture));
  });
});
