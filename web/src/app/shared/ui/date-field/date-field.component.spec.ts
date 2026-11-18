// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { withPopoverApi } from '../../../../testing/popover';
import { LanguageService } from '../../../core/i18n/language.service';
import type { IsoDate } from '../calendar/local-date';
import { DateFieldComponent } from './date-field.component';

@Component({
  imports: [ReactiveFormsModule, DateFieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form">
      <furar-date-field id="from" formControlName="from" [label]="'Desde'" [today]="'2026-10-25'" />
      <furar-date-field
        id="to"
        formControlName="to"
        [label]="'Ata'"
        [today]="'2026-10-25'"
        [min]="'2026-10-20'"
        [max]="'2026-11-10'"
      />
      <furar-date-field id="closed" formControlName="closed" [label]="'Pechada'" />
      <button id="after" type="button">{{ 'Seguinte' }}</button>
    </form>
  `,
})
class HostComponent {
  readonly form = new FormGroup({
    from: new FormControl<IsoDate | null>('2026-12-24'),
    to: new FormControl<IsoDate | null>(null),
    closed: new FormControl<IsoDate | null>({ value: '2026-12-24', disabled: true }),
  });
}

/** O campo dentro dun formulario que se pecha con Esc (bloqueos, usos, excepcións). */
@Component({
  imports: [ReactiveFormsModule, DateFieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form" (keydown.escape)="formEscapes = formEscapes + 1">
      <furar-date-field id="from" formControlName="from" [label]="'Desde'" [today]="'2026-10-25'" />
    </form>
  `,
})
class InFormHostComponent {
  formEscapes = 0;
  readonly form = new FormGroup({ from: new FormControl<IsoDate | null>('2026-12-24') });
}

describe('furar-date-field', () => {
  afterEach(() => {
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(lang: 'gl' | 'es' = 'gl') {
    TestBed.configureTestingModule({ providers: [provideTestI18n(lang)] });
    const fixture = TestBed.createComponent(HostComponent);
    await TestBed.inject(LanguageService).use(lang);
    await fixture.whenStable();
    return fixture;
  }

  const button = (fixture: ComponentFixture<HostComponent>, id: string) =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(`#${id} button`)!;
  const dialog = () => document.querySelector<HTMLElement>('[role="dialog"]');
  const active = () => document.activeElement as HTMLElement;

  async function press(fixture: ComponentFixture<HostComponent>, key: string, target: Element = active()) {
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
    target.dispatchEvent(event);
    await fixture.whenStable();
    return event;
  }

  async function open(fixture: ComponentFixture<HostComponent>, id: string) {
    button(fixture, id).click();
    await fixture.whenStable();
  }

  it('o botón amosa a data co formato do idioma e o nome leva o contexto', async () => {
    const fixture = await render();
    const from = button(fixture, 'from');
    expect(from.textContent?.trim()).toBe('24/12/2026');
    expect(from.getAttribute('aria-label')).toBe('Desde, 24/12/2026');
    expect(from.getAttribute('aria-haspopup')).toBe('dialog');
    expect(from.getAttribute('aria-expanded')).toBe('false');
    expect(button(fixture, 'to').getAttribute('aria-label')).toBe('Ata, Escoller data');
    expect(button(fixture, 'closed').disabled).toBe(true);
    await expectNoAxeViolations(fixture.nativeElement);
  });

  it('en castelán o formato tamén é «24/12/2026»', async () => {
    const fixture = await render('es');
    expect(button(fixture, 'from').textContent?.trim()).toBe('24/12/2026');
    expect(button(fixture, 'to').getAttribute('aria-label')).toBe('Ata, Elegir fecha');
  });

  it('abre o diálogo co foco no día escollido e pasa axe', async () => {
    const fixture = await render();
    await open(fixture, 'from');
    expect(button(fixture, 'from').getAttribute('aria-expanded')).toBe('true');
    expect(dialog()?.getAttribute('aria-label')).toBe('Desde: escoller data');
    expect(dialog()?.getAttribute('aria-modal')).toBe('true');
    expect(active().dataset['date']).toBe('2026-12-24');
    await expectNoAxeViolations(dialog()!);
  });

  it('sen valor, o foco vai a hoxe acoutado a min e max', async () => {
    const fixture = await render();
    await open(fixture, 'to');
    expect(active().dataset['date']).toBe('2026-10-25');
    expect(document.querySelector('td[data-date="2026-10-19"]')?.getAttribute('aria-disabled')).toBe('true');
  });

  it('escoller cun teclado garda a data ISO, pecha e devolve o foco ao botón', async () => {
    const fixture = await render();
    const from = button(fixture, 'from');
    await press(fixture, 'ArrowDown', from);
    expect(dialog()).not.toBeNull();
    await press(fixture, 'ArrowRight');
    await press(fixture, 'Enter');
    expect(fixture.componentInstance.form.controls.from.value).toBe('2026-12-25');
    expect(fixture.componentInstance.form.controls.from.touched).toBe(true);
    expect(dialog()).toBeNull();
    expect(active()).toBe(from);
    expect(from.textContent?.trim()).toBe('25/12/2026');
  });

  it('Esc pecha sen cambiar e devolve o foco ao botón', async () => {
    const fixture = await render();
    const from = button(fixture, 'from');
    await open(fixture, 'from');
    await press(fixture, 'ArrowLeft');
    const escape = await press(fixture, 'Escape');
    expect(escape.defaultPrevented).toBe(true);
    expect(dialog()).toBeNull();
    expect(fixture.componentInstance.form.controls.from.value).toBe('2026-12-24');
    expect(active()).toBe(from);
  });

  it('Esc no calendario dentro dun formulario só pecha o calendario, aínda coa capa no formulario (Chrome)', async () => {
    const restore = withPopoverApi();
    try {
      TestBed.configureTestingModule({ providers: [provideTestI18n('gl')] });
      const fixture = TestBed.createComponent(InFormHostComponent);
      await fixture.whenStable();
      const form = (fixture.nativeElement as HTMLElement).querySelector('form')!;
      const trigger = form.querySelector<HTMLButtonElement>('#from button')!;
      trigger.click();
      await fixture.whenStable();
      expect(form.contains(dialog())).toBe(true);
      const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
      active().dispatchEvent(escape);
      await fixture.whenStable();
      expect(escape.defaultPrevented).toBe(true);
      expect(fixture.componentInstance.formEscapes).toBe(0);
      expect(dialog()).toBeNull();
      expect(active()).toBe(trigger);
      expect(fixture.componentInstance.form.controls.from.value).toBe('2026-12-24');
    } finally {
      restore();
    }
  });

  it('o foco non sae do diálogo: Tab dá a volta entre as frechas do mes e o día', async () => {
    const fixture = await render();
    await open(fixture, 'from');
    const tab = await press(fixture, 'Tab');
    expect(tab.defaultPrevented).toBe(true);
    expect(active().getAttribute('aria-label')).toBe('Mes anterior');
    await press(fixture, 'Tab');
    expect(active().getAttribute('aria-label')).toBe('Mes seguinte');
    const back = new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true, cancelable: true });
    active().dispatchEvent(back);
    await press(fixture, 'Tab');
    await press(fixture, 'Tab');
    expect(active().dataset['date']).toBe('2026-12-24');
  });

  it('un clic fóra pecha sen mover o foco ao botón', async () => {
    const fixture = await render();
    await open(fixture, 'from');
    const after = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#after')!;
    after.focus();
    after.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }));
    after.click();
    await fixture.whenStable();
    expect(dialog()).toBeNull();
    expect(active()).toBe(after);
  });

  it('picar un día escolle; o mesmo botón volve pechar', async () => {
    const fixture = await render();
    await open(fixture, 'from');
    document.querySelector<HTMLElement>('td[data-date="2026-12-01"]')!.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.form.controls.from.value).toBe('2026-12-01');
    await open(fixture, 'from');
    expect(dialog()).not.toBeNull();
    await open(fixture, 'from');
    expect(dialog()).toBeNull();
  });

  it('o formulario pode pór e quitar o valor; o que non é ISO queda baleiro', async () => {
    const fixture = await render();
    const control = fixture.componentInstance.form.controls.from;
    control.setValue('2027-01-06');
    await fixture.whenStable();
    expect(button(fixture, 'from').textContent?.trim()).toBe('06/01/2027');
    control.setValue('06/01/2027');
    await fixture.whenStable();
    expect(button(fixture, 'from').getAttribute('data-empty')).toBe('');
    control.disable();
    await fixture.whenStable();
    expect(button(fixture, 'from').disabled).toBe(true);
    // Desactivado e sen data (p. ex. «Ata» dunha tempada sen fin): «Sen data», non «Escoller data».
    expect(button(fixture, 'from').textContent?.trim()).toBe('Sen data');
    expect(button(fixture, 'from').getAttribute('aria-label')).toBe('Desde, Sen data');
  });
});

/** A data de nacemento: escoller ano e mes desde o título, abrindo nos anos; sen datas futuras. */
@Component({
  imports: [ReactiveFormsModule, DateFieldComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-date-field
      id="birth"
      yearSelect
      startView="years"
      min="1900-01-01"
      [max]="'2026-10-25'"
      [today]="'2026-10-25'"
      [label]="'Data de nacemento'"
      [formControl]="birth"
    />
  `,
})
class BirthHostComponent {
  readonly birth = new FormControl<IsoDate | null>(null);
}

describe('furar-date-field con yearSelect', () => {
  afterEach(() => {
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  const active = () => document.activeElement as HTMLElement;

  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(BirthHostComponent);
    await fixture.whenStable();
    return fixture;
  }

  async function press(fixture: ComponentFixture<BirthHostComponent>, key: string) {
    active().dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
    await fixture.whenStable();
  }

  it('abre nos anos (de 1900 a hoxe, sen futuros), co foco no ano de hoxe; axe', async () => {
    const fixture = await render();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#birth button')!.click();
    await fixture.whenStable();
    const years = document.querySelector('[data-calendar-years]')!;
    expect(years.getAttribute('role')).toBe('listbox');
    expect(years.getAttribute('aria-label')).toBe('Ano');
    const options = [...years.querySelectorAll('[role="option"]')];
    expect(options[0].textContent?.trim()).toBe('1900');
    expect(options.at(-1)?.textContent?.trim()).toBe('2026');
    expect(active().dataset['year']).toBe('2026');
    expect(document.querySelector('[data-calendar-view]')?.getAttribute('aria-expanded')).toBe('true');
    await expectNoAxeViolations(document.querySelector<HTMLElement>('[role="dialog"]')!);
  });

  it('só teclado: teclear 1984 vai a ese ano, Intro abre os meses, frechas e Intro escollen o mes, e logo o día', async () => {
    const fixture = await render();
    const trigger = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#birth button')!;
    trigger.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true, cancelable: true }));
    await fixture.whenStable();
    for (const digit of '1984') await press(fixture, digit);
    expect(active().dataset['year']).toBe('1984');
    await press(fixture, 'ArrowUp');
    expect(active().dataset['year']).toBe('1981');
    await press(fixture, 'ArrowDown');
    await press(fixture, 'Enter');
    const months = document.querySelector('[data-calendar-months]')!;
    expect(months.getAttribute('aria-label')).toBe('Mes de 1984');
    expect(months.querySelectorAll('[role="option"]')).toHaveLength(12);
    // O mes de hoxe (outubro); dúas á esquerda e unha arriba: maio.
    expect(active().dataset['month']).toBe('10');
    await press(fixture, 'ArrowLeft');
    await press(fixture, 'ArrowLeft');
    await press(fixture, 'ArrowUp');
    expect(active().dataset['month']).toBe('5');
    await press(fixture, 'Enter');
    expect(active().dataset['date']).toBe('1984-05-25');
    await press(fixture, 'Enter');
    expect(fixture.componentInstance.birth.value).toBe('1984-05-25');
    expect(trigger.textContent?.trim()).toBe('25/05/1984');
  });

  it('con data, abre nos días; o título volve aos anos e os meses futuros non se escollen', async () => {
    const fixture = await render();
    fixture.componentInstance.birth.setValue('2015-03-10');
    await fixture.whenStable();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#birth button')!.click();
    await fixture.whenStable();
    expect(active().dataset['date']).toBe('2015-03-10');
    const title = document.querySelector<HTMLButtonElement>('[data-calendar-view]')!;
    expect(title.getAttribute('aria-expanded')).toBe('false');
    expect(title.getAttribute('aria-label')).toContain('escoller o ano e o mes');
    title.click();
    await fixture.whenStable();
    expect(document.querySelector('[data-year="2015"]')?.getAttribute('aria-selected')).toBe('true');
    document.querySelector<HTMLElement>('[data-year="2026"]')!.click();
    await fixture.whenStable();
    expect(document.querySelector('[data-month="11"]')?.getAttribute('aria-disabled')).toBe('true');
    document.querySelector<HTMLElement>('[data-month="11"]')!.click();
    await fixture.whenStable();
    expect(document.querySelector('[data-calendar-months]')).not.toBeNull();
  });
});
