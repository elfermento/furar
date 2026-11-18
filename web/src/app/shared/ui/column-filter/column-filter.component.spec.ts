// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { LanguageService } from '../../../core/i18n/language.service';
import type { ColumnFilterOption } from './column-filter.component';
import { ColumnFilterComponent } from './column-filter.component';

@Component({
  imports: [ColumnFilterComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <table>
      <thead>
        <tr>
          <th scope="col">
            <furar-column-filter
              searchable
              [label]="'Acción'"
              [options]="options"
              [selected]="selected()"
              [doneLabel]="'Ver 3 accións'"
              (toggled)="toggled.push($event)"
              (cleared)="selected.set([])"
            >
              <p id="extra">{{ 'Máis' }}</p>
            </furar-column-filter>
          </th>
        </tr>
      </thead>
    </table>
  `,
})
class HostComponent {
  readonly options: ColumnFilterOption[] = [
    { value: 'cancel', label: 'Cancelar unha reserva', count: 2 },
    { value: 'publish', label: 'Publicar unha instalación', count: 0 },
    { value: 'sign-in', label: 'Entrar na xestión', hint: 'Recepción' },
    { value: 'MANAGER', label: 'Xestión', tone: 'info' },
  ];
  readonly selected = signal<readonly string[]>([]);
  readonly toggled: string[] = [];
}

describe('furar-column-filter', () => {
  let narrow = false;
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    narrow = false;
    window.matchMedia = ((query: string) => ({ matches: narrow, media: query })) as unknown as typeof window.matchMedia;
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(): Promise<ComponentFixture<HostComponent>> {
    TestBed.configureTestingModule({ providers: [provideTestI18n('gl')] });
    const fixture = TestBed.createComponent(HostComponent);
    await TestBed.inject(LanguageService).use('gl');
    await fixture.whenStable();
    return fixture;
  }

  const trigger = (fixture: ComponentFixture<HostComponent>) =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.f-field-popover__trigger')!;
  const panel = () => document.querySelector<HTMLElement>('[role="dialog"]');
  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();

  it('a cabeceira é un botón co nome da columna que abre un panel coas opcións e as contas; axe', async () => {
    const fixture = await render();
    const button = trigger(fixture);
    expect(text(button)).toBe('Acción');
    expect(button.getAttribute('aria-haspopup')).toBe('dialog');
    expect(button.getAttribute('aria-label')).toBeNull();
    button.click();
    await fixture.whenStable();
    expect(panel()?.getAttribute('aria-label')).toBe('Acción');
    const options = [...panel()!.querySelectorAll('[data-option]')];
    expect(options.map(text)).toEqual([
      'Cancelar unha reserva 2',
      'Publicar unha instalación 0',
      'Entrar na xestión Recepción',
      'Xestión',
    ]);
    // Co ton, o texto vai no mesmo chip co que se pinta nas filas.
    const tag = panel()!.querySelector('[data-option="MANAGER"] furar-tag');
    expect(tag?.getAttribute('data-tone')).toBe('info');
    expect(tag?.getAttribute('data-look')).toBe('solid');
    expect(panel()!.querySelector('[data-option="cancel"] furar-tag')).toBeNull();
    expect(options.every((o) => o.getAttribute('aria-pressed') === 'false')).toBe(true);
    // Sen filtro, sen «Quitar»; o contido proxectado vai despois da lista.
    expect(panel()!.querySelector('[data-column-clear]')).toBeNull();
    expect(panel()!.querySelector('#extra')).not.toBeNull();
    await expectNoAxeViolations(fixture.nativeElement as HTMLElement);
    await expectNoAxeViolations(panel()!);
  });

  it('a busca filtra as opcións sen maiúsculas nin acentos, tamén polo texto secundario; escoller emite o valor', async () => {
    const fixture = await render();
    trigger(fixture).click();
    await fixture.whenStable();
    const search = panel()!.querySelector<HTMLInputElement>('[data-column-search]')!;
    expect(search.closest('label')?.textContent).toContain('Buscar en «Acción»');
    search.value = 'INSTALACION';
    search.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect([...panel()!.querySelectorAll('[data-option]')].map((o) => o.getAttribute('data-option'))).toEqual([
      'publish',
    ]);
    search.value = 'recepcion';
    search.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect([...panel()!.querySelectorAll('[data-option]')].map((o) => o.getAttribute('data-option'))).toEqual([
      'sign-in',
    ]);
    expect(text(panel()!.querySelector('[data-option="sign-in"] .f-column-filter__hint'))).toBe('Recepción');
    search.value = 'xyz';
    search.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(text(panel()!.querySelector('.f-column-filter__empty'))).toBe('Non hai opcións.');
    search.value = '';
    search.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    panel()!.querySelector<HTMLButtonElement>('[data-option="cancel"]')!.click();
    expect(fixture.componentInstance.toggled).toEqual(['cancel']);
  });

  it('con filtro: punto e «con filtro» na cabeceira, a opción marcada e «Quitar»', async () => {
    const fixture = await render();
    fixture.componentInstance.selected.set(['publish']);
    await fixture.whenStable();
    const button = trigger(fixture);
    expect(button.querySelector('.f-field-popover__dot')).not.toBeNull();
    expect(button.getAttribute('aria-label')).toBe('Acción, con filtro');
    button.click();
    await fixture.whenStable();
    expect(panel()!.querySelector('[data-option="publish"]')?.getAttribute('aria-pressed')).toBe('true');
    panel()!.querySelector<HTMLButtonElement>('[data-column-clear]')!.click();
    await fixture.whenStable();
    expect(button.querySelector('.f-field-popover__dot')).toBeNull();
  });

  it('no móbil é unha folla desde abaixo co botón que a pecha', async () => {
    narrow = true;
    const fixture = await render();
    trigger(fixture).click();
    await fixture.whenStable();
    expect(panel()?.getAttribute('data-sheet')).toBe('true');
    const done = [...panel()!.querySelectorAll('button')].find((b) => text(b) === 'Ver 3 accións')!;
    done.click();
    await fixture.whenStable();
    expect(panel()).toBeNull();
    expect(document.activeElement).toBe(trigger(fixture));
  });
});
