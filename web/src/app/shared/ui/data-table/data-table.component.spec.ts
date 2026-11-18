// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import type { DataTableColumn, DataTableRow } from './data-table.component';
import { DataTableComponent } from './data-table.component';

@Component({
  imports: [DataTableComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-data-table
    [captionHidden]="hidden()"
    [caption]="'Ocupación por instalación'"
    [columns]="columns"
    [rows]="rows()"
  />`,
})
class HostComponent {
  readonly hidden = signal(false);
  readonly columns: DataTableColumn[] = [{ label: 'Instalación' }, { label: 'Ocupación', numeric: true }];
  readonly rows = signal<DataTableRow[]>([
    ['Pavillón', '78 %'],
    ['Piscina', '54 %'],
  ]);
}

/** jsdom non mide: o ancho do contido e o da caixa ponse a man. */
function setWidths(element: HTMLElement, scroll: number, client: number): void {
  Object.defineProperty(element, 'scrollWidth', { configurable: true, get: () => scroll });
  Object.defineProperty(element, 'clientWidth', { configurable: true, get: () => client });
}

describe('furar-data-table', () => {
  let fixture: ComponentFixture<HostComponent>;
  const el = () => fixture.nativeElement as HTMLElement;
  const scroller = () => el().querySelector<HTMLElement>('.f-data-table__scroll')!;

  beforeEach(async () => {
    fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
  });

  it('caption, th scope de columna e de fila, e cifras á dereita', async () => {
    const table = el().querySelector('table')!;
    expect(table.caption?.textContent?.trim()).toBe('Ocupación por instalación');
    expect([...table.querySelectorAll('thead th')].map((th) => th.getAttribute('scope'))).toEqual(['col', 'col']);
    expect([...table.querySelectorAll('tbody th')].map((th) => [th.getAttribute('scope'), th.textContent])).toEqual([
      ['row', 'Pavillón'],
      ['row', 'Piscina'],
    ]);
    expect(table.querySelector('thead th:nth-child(2)')?.classList).toContain('f-data-table__num');
    expect([...table.querySelectorAll('tbody td.f-data-table__num')].map((td) => td.textContent)).toEqual([
      '78 %',
      '54 %',
    ]);
    expect(table.querySelector('tbody th')?.classList).not.toContain('f-data-table__num');
    await expectNoAxeViolations(el());
  });

  it('captionHidden: o caption que repite o título queda só para os lectores (segue sendo o nome)', async () => {
    const caption = () => el().querySelector('caption')!;
    expect(caption().classList.contains('sr-only')).toBe(false);
    fixture.componentInstance.hidden.set(true);
    await fixture.whenStable();
    expect(caption().classList.contains('sr-only')).toBe(true);
    expect(caption().textContent?.trim()).toBe('Ocupación por instalación');
    expect(caption().hasAttribute('aria-hidden')).toBe(false);
  });

  it('se cabe, sen rexión nin parada de tabulador', () => {
    expect(scroller().getAttribute('role')).toBeNull();
    expect(scroller().getAttribute('tabindex')).toBeNull();
  });

  it('se non cabe, rexión con nome (o caption) e enfocable para movela co teclado', async () => {
    setWidths(scroller(), 900, 288);
    fixture.componentInstance.rows.update((rows) => [...rows, ['Campo de fútbol', '31 %']]);
    await fixture.whenStable();
    const caption = el().querySelector('caption')!;
    expect(scroller().getAttribute('role')).toBe('region');
    expect(scroller().getAttribute('tabindex')).toBe('0');
    expect(scroller().getAttribute('aria-labelledby')).toBe(caption.id);
    await expectNoAxeViolations(el());

    setWidths(scroller(), 288, 288);
    fixture.componentInstance.rows.update((rows) => rows.slice(0, 1));
    await fixture.whenStable();
    expect(scroller().getAttribute('role')).toBeNull();
  });
});
