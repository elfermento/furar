// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { TileDirective } from './tile.directive';

@Component({
  imports: [TileDirective],
  template: `
    <ul class="f-tile-grid" aria-label="Usos">
      <li><button furarTile type="button">Fútbol 11</button></li>
    </ul>
  `,
})
class HostComponent {}

describe('TileDirective', () => {
  it('é un botón nativo co aspecto de tarxeta', async () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const button = (fixture.nativeElement as HTMLElement).querySelector('button')!;
    expect(button.classList).toContain('f-tile');
    expect(button.textContent).toBe('Fútbol 11');
    await expectNoAxeViolations(fixture.nativeElement);
  });
});
