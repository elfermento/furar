// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { TabDirective, TabsComponent } from './tabs.component';

@Component({
  imports: [TabsComponent, TabDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-tabs id="seasons" label="Tempadas" [activation]="activation()" [(selected)]="selected">
      @for (season of seasons(); track season) {
        <button type="button" [furarTab]="season">{{ season }}</button>
      }
      <button furarTabsActions id="add" type="button">{{ '+ Tempada' }}</button>
      <p id="content">{{ 'Semana de ' + selected() }}</p>
    </furar-tabs>
  `,
})
class HostComponent {
  readonly seasons = signal(['Inverno', 'Verán', 'Nadal']);
  readonly selected = signal<string | null>('Verán');
  readonly activation = signal<'automatic' | 'manual'>('automatic');
}

describe('furar-tabs', () => {
  async function render() {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return fixture;
  }

  const el = (fixture: ComponentFixture<HostComponent>) => fixture.nativeElement as HTMLElement;
  const tabs = (fixture: ComponentFixture<HostComponent>) => [
    ...el(fixture).querySelectorAll<HTMLButtonElement>('[role="tab"]'),
  ];
  const selected = (fixture: ComponentFixture<HostComponent>) =>
    tabs(fixture).map((tab) => tab.getAttribute('aria-selected'));

  async function press(fixture: ComponentFixture<HostComponent>, key: string) {
    const event = new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true });
    (document.activeElement ?? document.body).dispatchEvent(event);
    await fixture.whenStable();
    return event;
  }

  it('tablist con nome, pestanas e un panel ligado á escollida; accións fóra do tablist', async () => {
    const fixture = await render();
    const list = el(fixture).querySelector('[role="tablist"]')!;
    const panel = el(fixture).querySelector('[role="tabpanel"]')!;
    expect(list.getAttribute('aria-label')).toBe('Tempadas');
    expect(selected(fixture)).toEqual(['false', 'true', 'false']);
    expect(tabs(fixture).map((tab) => tab.getAttribute('tabindex'))).toEqual(['-1', '0', '-1']);
    expect(tabs(fixture).every((tab) => tab.getAttribute('aria-controls') === panel.id)).toBe(true);
    expect(panel.getAttribute('aria-labelledby')).toBe(tabs(fixture)[1]!.id);
    expect(list.contains(el(fixture).querySelector('#add'))).toBe(false);
    expect(panel.contains(el(fixture).querySelector('#content'))).toBe(true);
    await expectNoAxeViolations(el(fixture));
  });

  it('activación automática: as frechas moven e escollen, dando a volta; Inicio e Fin', async () => {
    const fixture = await render();
    const [winter, summer, christmas] = tabs(fixture);
    summer!.focus();
    const right = await press(fixture, 'ArrowRight');
    expect(right.defaultPrevented).toBe(true);
    expect(document.activeElement).toBe(christmas);
    expect(fixture.componentInstance.selected()).toBe('Nadal');
    await press(fixture, 'ArrowRight');
    expect(document.activeElement).toBe(winter);
    await press(fixture, 'ArrowLeft');
    expect(document.activeElement).toBe(christmas);
    await press(fixture, 'Home');
    expect(document.activeElement).toBe(winter);
    expect(fixture.componentInstance.selected()).toBe('Inverno');
    await press(fixture, 'End');
    expect(selected(fixture)).toEqual(['false', 'false', 'true']);
    expect(el(fixture).querySelector('#content')?.textContent).toBe('Semana de Nadal');
    const down = await press(fixture, 'ArrowDown');
    expect(down.defaultPrevented).toBe(false);
  });

  it('activación manual: a frecha só move o foco e escolle o clic (Intro ou Espazo)', async () => {
    const fixture = await render();
    fixture.componentInstance.activation.set('manual');
    await fixture.whenStable();
    const [, summer, christmas] = tabs(fixture);
    summer!.focus();
    await press(fixture, 'ArrowRight');
    expect(document.activeElement).toBe(christmas);
    expect(fixture.componentInstance.selected()).toBe('Verán');
    christmas!.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.selected()).toBe('Nadal');
  });

  it('sen pestana que case, a primeira recibe o tabulador; sen pestanas non hai tablist á vista', async () => {
    const fixture = await render();
    fixture.componentInstance.selected.set(null);
    await fixture.whenStable();
    expect(tabs(fixture).map((tab) => tab.getAttribute('tabindex'))).toEqual(['0', '-1', '-1']);
    expect(el(fixture).querySelector('[role="tabpanel"]')).toBeNull();
    fixture.componentInstance.seasons.set([]);
    await fixture.whenStable();
    expect(el(fixture).querySelector<HTMLElement>('[role="tablist"]')?.hidden).toBe(true);
    await expectNoAxeViolations(el(fixture));
  });
});
