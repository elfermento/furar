// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { SegmentedOptionDirective, SegmentedRadioDirective } from './segmented-radio.directive';
import { SegmentedDirective } from './segmented.directive';

@Component({
  imports: [ReactiveFormsModule, SegmentedDirective, SegmentedRadioDirective, SegmentedOptionDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form [formGroup]="form">
      <div id="mode" furarSegmented mode="radio" formControlName="mode" aria-label="Se aluga">
        <button type="button" furarSegmentedOption="WHOLE">{{ 'Enteira' }}</button>
        <button type="button" furarSegmentedOption="PARTS">{{ 'Por partes' }}</button>
        <button type="button" furarSegmentedOption="SEATS">{{ 'Por prazas' }}</button>
      </div>
      <div id="minutes" furarSegmented mode="radio" formControlName="minutes" aria-label="Dura">
        <button type="button" [furarSegmentedOption]="30">{{ '30' }}</button>
        <button type="button" [furarSegmentedOption]="60" disabled>{{ '60' }}</button>
        <button type="button" [furarSegmentedOption]="90">{{ '90' }}</button>
      </div>
    </form>
    <div id="loose" furarSegmented mode="radio" aria-label="Solto" [(value)]="loose">
      <button type="button" furarSegmentedOption="a">{{ 'A' }}</button>
      <button type="button" furarSegmentedOption="b">{{ 'B' }}</button>
    </div>
    <button id="after" type="button">{{ 'Seguinte' }}</button>
  `,
})
class HostComponent {
  readonly form = new FormGroup({
    mode: new FormControl('PARTS', { nonNullable: true }),
    minutes: new FormControl<number | null>(null),
  });
  readonly loose = signal<string | null>('b');
}

describe('[furarSegmented] modo radio', () => {
  async function render() {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return fixture;
  }

  const group = (fixture: ComponentFixture<HostComponent>, id: string) =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>(`#${id}`)!;
  const radios = (fixture: ComponentFixture<HostComponent>, id: string) => [
    ...group(fixture, id).querySelectorAll<HTMLButtonElement>('[role="radio"]'),
  ];
  const checked = (fixture: ComponentFixture<HostComponent>, id: string) =>
    radios(fixture, id).map((radio) => radio.getAttribute('aria-checked'));
  const tabbable = (fixture: ComponentFixture<HostComponent>, id: string) =>
    radios(fixture, id).map((radio) => radio.getAttribute('tabindex'));

  async function press(fixture: ComponentFixture<HostComponent>, key: string) {
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
    (document.activeElement ?? document.body).dispatchEvent(event);
    await fixture.whenStable();
    return event;
  }

  it('é un radiogroup co aspecto do segmentado e unha soa parada de tabulador, na escollida', async () => {
    const fixture = await render();
    const mode = group(fixture, 'mode');
    expect(mode.getAttribute('role')).toBe('radiogroup');
    expect(mode.classList).toContain('f-segmented');
    expect(checked(fixture, 'mode')).toEqual(['false', 'true', 'false']);
    expect(tabbable(fixture, 'mode')).toEqual(['-1', '0', '-1']);
    await expectNoAxeViolations(fixture.nativeElement as HTMLElement);
  });

  it('sen valor, a parada de tabulador é a primeira opción dispoñible', async () => {
    const fixture = await render();
    expect(checked(fixture, 'minutes')).toEqual(['false', 'false', 'false']);
    expect(tabbable(fixture, 'minutes')).toEqual(['0', '-1', '-1']);
  });

  it('as frechas moven o foco e escollen, dando a volta; Inicio e Fin', async () => {
    const fixture = await render();
    const [whole, parts, seats] = radios(fixture, 'mode');
    parts!.focus();
    const right = await press(fixture, 'ArrowRight');
    expect(right.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(seats);
    expect(fixture.componentInstance.form.controls.mode.value).toBe('SEATS');
    await press(fixture, 'ArrowDown');
    expect(document.activeElement).toBe(whole);
    expect(fixture.componentInstance.form.controls.mode.value).toBe('WHOLE');
    await press(fixture, 'ArrowLeft');
    expect(document.activeElement).toBe(seats);
    await press(fixture, 'ArrowUp');
    expect(document.activeElement).toBe(parts);
    await press(fixture, 'Home');
    expect(document.activeElement).toBe(whole);
    await press(fixture, 'End');
    expect(document.activeElement).toBe(seats);
    expect(checked(fixture, 'mode')).toEqual(['false', 'false', 'true']);
    expect(tabbable(fixture, 'mode')).toEqual(['-1', '-1', '0']);
    const other = await press(fixture, 'a');
    expect(other.defaultPrevented).toBe(false);
  });

  it('as frechas saltan as opcións desactivadas', async () => {
    const fixture = await render();
    const [thirty, sixty, ninety] = radios(fixture, 'minutes');
    expect(sixty!.disabled).toBe(true);
    thirty!.focus();
    await press(fixture, 'ArrowRight');
    expect(document.activeElement).toBe(ninety);
    expect(fixture.componentInstance.form.controls.minutes.value).toBe(90);
  });

  it('o clic escolle e avisa ao formulario; tocado só ao saír do grupo', async () => {
    const fixture = await render();
    const control = fixture.componentInstance.form.controls.mode;
    const [whole, parts] = radios(fixture, 'mode');
    whole!.click();
    await fixture.whenStable();
    expect(control.value).toBe('WHOLE');
    expect(control.dirty).toBe(true);
    whole!.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: parts }));
    expect(control.touched).toBe(false);
    const after = (fixture.nativeElement as HTMLElement).querySelector('#after');
    whole!.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: after }));
    expect(control.touched).toBe(true);
  });

  it('o valor que pon o formulario reflíctese e desactivar o control desactiva todo', async () => {
    const fixture = await render();
    const control = fixture.componentInstance.form.controls.mode;
    control.setValue('SEATS');
    await fixture.whenStable();
    expect(checked(fixture, 'mode')).toEqual(['false', 'false', 'true']);
    control.disable();
    await fixture.whenStable();
    expect(group(fixture, 'mode').getAttribute('aria-disabled')).toBe('true');
    expect(radios(fixture, 'mode').every((radio) => radio.disabled)).toBe(true);
    radios(fixture, 'mode')[0]!.click();
    expect(control.value).toBe('SEATS');
  });

  it('sen formulario funciona con [(value)]', async () => {
    const fixture = await render();
    const [a] = radios(fixture, 'loose');
    expect(checked(fixture, 'loose')).toEqual(['false', 'true']);
    a!.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.loose()).toBe('a');
    expect(checked(fixture, 'loose')).toEqual(['true', 'false']);
  });
});
