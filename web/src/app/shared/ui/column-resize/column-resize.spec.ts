// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { LanguageService } from '../../../core/i18n/language.service';
import { ColumnResizeHandleComponent } from './column-resize-handle.component';
import { ColumnResizeDirective } from './column-resize.directive';
import { ColumnWidths, columnWidthsKey } from './column-widths';

const KEY = 'furar.columns.user-1.test';

@Component({
  imports: [ColumnResizeDirective, ColumnResizeHandleComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <table [furarColumnResize]="widths">
      <colgroup>
        @for (column of widths.columns; track column.key) {
          <col [attr.data-col]="column.key" [style.width.px]="widths.width(column.key)" />
        }
      </colgroup>
      <thead>
        <tr>
          <th scope="col">
            Data
            <furar-column-resize [widths]="widths" column="date" label="Data" />
          </th>
          <th scope="col">Acción</th>
        </tr>
      </thead>
    </table>
  `,
})
class HostComponent {
  readonly user = signal<string | null>('user-1');
  readonly widths = new ColumnWidths({
    columns: [
      { key: 'date', width: 120, min: 96 },
      { key: 'action', width: null },
    ],
    storageKey: () => columnWidthsKey(this.user(), 'test'),
    flexMin: 200,
    max: 400,
  });
}

describe('columnas que se ensanchan (table[furarColumnResize], furar-column-resize)', () => {
  afterEach(() => localStorage.removeItem(KEY));

  async function render(): Promise<ComponentFixture<HostComponent>> {
    TestBed.configureTestingModule({ providers: [provideTestI18n('gl')] });
    const fixture = TestBed.createComponent(HostComponent);
    await TestBed.inject(LanguageService).use('gl');
    await fixture.whenStable();
    return fixture;
  }

  const el = (fixture: ComponentFixture<HostComponent>) => fixture.nativeElement as HTMLElement;
  const handle = (fixture: ComponentFixture<HostComponent>) =>
    el(fixture).querySelector<HTMLElement>('[role="separator"]')!;
  const width = (fixture: ComponentFixture<HostComponent>) =>
    el(fixture).querySelector<HTMLElement>('col[data-col="date"]')!.style.width;
  const press = async (fixture: ComponentFixture<HostComponent>, key: string, shiftKey = false) => {
    handle(fixture).dispatchEvent(new KeyboardEvent('keydown', { key, shiftKey, bubbles: true }));
    await fixture.whenStable();
  };

  it('separador enfocable co seu nome e valores; a táboa mide polo menos a suma; axe', async () => {
    const fixture = await render();
    const separator = handle(fixture);
    expect(separator.getAttribute('tabindex')).toBe('0');
    expect(separator.getAttribute('aria-orientation')).toBe('vertical');
    expect(separator.getAttribute('aria-label')).toBe('Ancho da columna «Data»');
    expect(separator.getAttribute('aria-valuenow')).toBe('120');
    expect(separator.getAttribute('aria-valuemin')).toBe('96');
    expect(separator.getAttribute('aria-valuemax')).toBe('400');
    expect(width(fixture)).toBe('120px');
    expect(el(fixture).querySelector('table')!.style.getPropertyValue('--f-columns-min')).toBe('320px');
    await expectNoAxeViolations(el(fixture));
  });

  it('teclado: frechas (con Maiúsculas, máis), Inicio e Fin, sen pasar do mínimo nin do máximo; gárdase', async () => {
    const fixture = await render();
    await press(fixture, 'ArrowRight');
    expect(width(fixture)).toBe('136px');
    await press(fixture, 'ArrowLeft', true);
    expect(width(fixture)).toBe('96px');
    await press(fixture, 'ArrowLeft');
    expect(width(fixture)).toBe('96px');
    await press(fixture, 'End');
    expect(width(fixture)).toBe('400px');
    expect(JSON.parse(localStorage.getItem(KEY)!)).toEqual({ date: 400 });
    await press(fixture, 'Home');
    expect(handle(fixture).getAttribute('aria-valuenow')).toBe('96');
  });

  it('rato: arrastrar cambia o ancho e garda ao soltar; dobre clic volve ao de partida', async () => {
    const fixture = await render();
    const separator = handle(fixture);
    separator.dispatchEvent(new PointerEvent('pointerdown', { button: 0, clientX: 100, pointerId: 1 }));
    separator.dispatchEvent(new PointerEvent('pointermove', { clientX: 150, pointerId: 1 }));
    await fixture.whenStable();
    expect(width(fixture)).toBe('170px');
    expect(localStorage.getItem(KEY)).toBeNull();
    separator.dispatchEvent(new PointerEvent('pointerup', { clientX: 150, pointerId: 1 }));
    expect(JSON.parse(localStorage.getItem(KEY)!)).toEqual({ date: 170 });
    separator.dispatchEvent(new MouseEvent('dblclick'));
    await fixture.whenStable();
    expect(width(fixture)).toBe('120px');
    expect(localStorage.getItem(KEY)).toBeNull();
  });

  it('recupera os anchos gardados da persoa (só columnas coñecidas e números válidos); outra persoa, os seus', async () => {
    localStorage.setItem(KEY, JSON.stringify({ date: 9999, action: 300, other: 50 }));
    const fixture = await render();
    expect(width(fixture)).toBe('400px');
    expect(fixture.componentInstance.widths.changed()).toBe(true);
    fixture.componentInstance.user.set('user-2');
    await fixture.whenStable();
    expect(width(fixture)).toBe('120px');
    expect(fixture.componentInstance.widths.changed()).toBe(false);
    localStorage.setItem(KEY, '{roto');
    fixture.componentInstance.user.set('user-1');
    await fixture.whenStable();
    expect(width(fixture)).toBe('120px');
  });

  it('sen sesión non garda nada; o almacenamento bloqueado non rompe', async () => {
    const fixture = await render();
    fixture.componentInstance.user.set(null);
    await fixture.whenStable();
    await press(fixture, 'ArrowRight');
    expect(width(fixture)).toBe('136px');
    expect(localStorage.getItem(KEY)).toBeNull();
    fixture.componentInstance.user.set('user-1');
    await fixture.whenStable();
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('bloqueado');
    });
    try {
      await press(fixture, 'ArrowRight');
      expect(width(fixture)).toBe('136px');
    } finally {
      setItem.mockRestore();
    }
  });
});
