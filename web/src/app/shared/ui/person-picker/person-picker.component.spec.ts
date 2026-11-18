// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import type { PersonOption } from './person-picker.component';
import { PersonPickerComponent } from './person-picker.component';

@Component({
  imports: [PersonPickerComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <p id="who">{{ 'Para quen' }}</p>
    <furar-person-picker labelledBy="who" [options]="options" [(selected)]="selected" />
  `,
})
class Host {
  readonly options: PersonOption[] = [
    { id: 'self', label: 'Eu', name: 'Marta Souto' },
    { id: 'd1', label: 'Lúa Souto Pena', detail: 'Ao meu cargo · 2015' },
    { id: 'd2', label: 'Brais Souto Pena', blocked: 'Non entra: é para nacidos entre 2014 e 2016' },
  ];
  readonly selected = signal<string | null>('d1');
}

describe('PersonPickerComponent: escoller unha persoa', () => {
  function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const options = () => [...el.querySelectorAll<HTMLElement>('[role="option"]')];
    return { fixture, el, options };
  }

  it('lista de opcións co seu nome; iniciais decorativas; a escollida marcada; a que non entra, atenuada co motivo', async () => {
    const { el, options } = render();
    const list = el.querySelector('[role="listbox"]')!;
    expect(list.getAttribute('aria-labelledby')).toBe('who');
    const [me, lua, brais] = options();
    expect(me.querySelector('.f-person-picker__avatar')?.textContent?.trim()).toBe('MS');
    expect(me.querySelector('.f-person-picker__avatar')?.getAttribute('aria-hidden')).toBe('true');
    expect(lua.getAttribute('aria-selected')).toBe('true');
    expect(lua.querySelector('small')?.textContent).toBe('Ao meu cargo · 2015');
    expect(brais.getAttribute('aria-disabled')).toBe('true');
    expect(brais.querySelector('small')?.textContent).toBe('Non entra: é para nacidos entre 2014 e 2016');
    expect(brais.querySelector('.f-person-picker__mark')).toBeNull();
    // Unha soa parada de tabulador: a escollida.
    expect(options().map((o) => o.getAttribute('tabindex'))).toEqual(['-1', '0', '-1']);
    await expectNoAxeViolations(el);
  });

  it('clic, Intro e Espazo escollen; a que non entra non; as frechas, Inicio e Fin moven o foco', async () => {
    const { fixture, options } = render();
    const host = fixture.componentInstance;
    options()[0].click();
    fixture.detectChanges();
    expect(host.selected()).toBe('self');
    options()[2].click();
    fixture.detectChanges();
    expect(host.selected()).toBe('self');

    options()[0].focus();
    options()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(options()[1]);
    options()[1].dispatchEvent(new KeyboardEvent('keydown', { key: ' ', bubbles: true }));
    fixture.detectChanges();
    expect(host.selected()).toBe('d1');
    options()[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'End', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(options()[2]);
    options()[2].dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    fixture.detectChanges();
    expect(host.selected()).toBe('d1');
    options()[2].dispatchEvent(new KeyboardEvent('keydown', { key: 'Home', bubbles: true }));
    fixture.detectChanges();
    expect(document.activeElement).toBe(options()[0]);
  });
});
