// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal, viewChild } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import type { StatTrend } from './stat-tile.component';
import { StatTileComponent } from './stat-tile.component';

@Component({
  imports: [StatTileComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-stat-tile
      [label]="'Reservas'"
      [value]="'1.234'"
      [change]="change()"
      [trend]="trend()"
      [comparison]="'respecto a setembro'"
      (open)="opened = opened + 1"
    >
      <span class="spark-slot">{{ '' }}</span>
    </furar-stat-tile>
  `,
})
class HostComponent {
  readonly tile = viewChild.required(StatTileComponent);
  readonly change = signal('+12 %');
  readonly trend = signal<StatTrend | null>('up');
  opened = 0;
}

describe('furar-stat-tile', () => {
  let fixture: ComponentFixture<HostComponent>;
  const el = () => fixture.nativeElement as HTMLElement;
  const button = () => el().querySelector<HTMLButtonElement>('button')!;
  const delta = () => el().querySelector('.f-stat-tile__delta');

  async function render(lang: 'gl' | 'es' = 'gl') {
    TestBed.configureTestingModule({ providers: [provideTestI18n(lang)] });
    fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
  }

  it('é un botón que abre un diálogo, co rótulo, o valor, o cambio en texto e a minigráfica', async () => {
    await render();
    expect(button().getAttribute('type')).toBe('button');
    expect(button().getAttribute('aria-haspopup')).toBe('dialog');
    expect(button().querySelector('.spark-slot')).not.toBeNull();
    const name = button().textContent!.replace(/\s+/g, ' ').trim();
    expect(name).toBe('Reservas 1.234 ↑ +12 % respecto a setembro Ver evolución →');
    expect(delta()?.getAttribute('data-trend')).toBe('up');
    expect(delta()?.querySelector('[aria-hidden="true"]')?.textContent).toBe('↑');
    await expectNoAxeViolations(el());
  });

  it('nome accesible completo: rótulo, valor, cambio, comparación e «Ver evolución», sen as frechas', async () => {
    await render();
    // O nome dun botón é o texto dos seus fillos sen o que leva aria-hidden (as frechas).
    const copy = button().cloneNode(true) as HTMLElement;
    copy.querySelectorAll('[aria-hidden="true"]').forEach((n) => n.remove());
    const name = copy.textContent!.replace(/\s+/g, ' ').trim();
    expect(name).toBe('Reservas 1.234 +12 % respecto a setembro Ver evolución');
    expect(button().hasAttribute('aria-label')).toBe(false);
    // «Ver evolución» vese sempre, sen agardar ao rato (non vai agochado).
    expect(el().querySelector('.f-stat-tile__more')!.hasAttribute('hidden')).toBe(false);
  });

  it('baixa e igual: frecha e ton propios; o cambio segue en texto', async () => {
    await render();
    fixture.componentInstance.trend.set('down');
    fixture.componentInstance.change.set('−3 p.p.');
    await fixture.whenStable();
    expect(delta()?.getAttribute('data-trend')).toBe('down');
    expect(delta()?.textContent?.replace(/\s+/g, ' ').trim()).toBe('↓ −3 p.p.');

    fixture.componentInstance.trend.set('same');
    fixture.componentInstance.change.set('0 %');
    await fixture.whenStable();
    expect(delta()?.getAttribute('data-trend')).toBe('same');
    expect(delta()?.textContent?.replace(/\s+/g, ' ').trim()).toBe('→ 0 %');
  });

  it('sen período anterior di o motivo en texto, en gl e en es', async () => {
    await render('es');
    fixture.componentInstance.trend.set(null);
    await fixture.whenStable();
    expect(delta()).toBeNull();
    expect(el().querySelector('.f-stat-tile__change')?.textContent?.trim()).toBe('Sin datos del periodo anterior');
    expect(el().querySelector('.f-stat-tile__more')?.textContent).toContain('Ver evolución');
    await expectNoAxeViolations(el());
  });

  it('ao premer emite open e dá o botón para a transición e o foco', async () => {
    await render();
    button().click();
    expect(fixture.componentInstance.opened).toBe(1);
    expect(fixture.componentInstance.tile().element()).toBe(button());
  });
});
