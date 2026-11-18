// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { TagComponent } from '../tag/tag.component';
import type { StatusCardLine } from './status-card.component';
import { StatusCardComponent } from './status-card.component';

@Component({
  imports: [StatusCardComponent, TagComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-status-card
      [heading]="'Pavillón da Gándara'"
      [amount]="amount()"
      [lines]="lines"
      [cover]="cover()"
      [level]="level()"
      [past]="past()"
      (opened)="opened.set(opened() + 1)"
    >
      <p class="extra">{{ 'Contido proxectado' }}</p>
      <furar-tag cardChips tone="warn">{{ 'Pendente de pago' }}</furar-tag>
      <furar-tag cardChips tone="info">{{ 'Buscando xente' }}</furar-tag>
    </furar-status-card>
  `,
})
class HostComponent {
  readonly amount = signal<string | null>('20,00 €');
  readonly cover = signal<{ thumb: string; width?: number; height?: number } | null>(null);
  readonly level = signal<2 | 3>(2);
  readonly past = signal(false);
  readonly opened = signal(0);
  readonly lines: StatusCardLine[] = [
    { id: 'when', icon: 'calendar-month', parts: ['venres, 20 de novembro', '20:00–21:30'] },
    { id: 'meta', soft: true, parts: ['Fútbol sala', 'Pista enteira'] },
  ];
}

describe('furar-status-card', () => {
  async function render() {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const settle = async () => {
      await fixture.whenStable();
    };
    return { fixture, host: fixture.componentInstance, el, settle };
  }
  const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();

  it('un só botón (o nome), o importe e o chevron arriba, as liñas por anacos e a fila de chips; axe', async () => {
    const { el, host } = await render();
    const card = el.querySelector('furar-status-card')!;
    expect(card.classList).toContain('f-card');
    expect(card.classList).toContain('f-status-card');
    expect(card.hasAttribute('data-past')).toBe(false);
    const controls = [...card.querySelectorAll('button, a')];
    expect(controls).toHaveLength(1);
    expect(card.querySelector('h2 > button')?.textContent?.trim()).toBe('Pavillón da Gándara');
    expect(text(card.querySelector('[data-card-amount]'))).toBe('20,00 €');
    expect(card.querySelector('.f-status-card__top .f-status-card__chevron')).not.toBeNull();
    expect(text(card.querySelector('[data-line="when"]'))).toBe('venres, 20 de novembro · 20:00–21:30');
    expect(card.querySelector('[data-line="when"] furar-icon')).not.toBeNull();
    expect(card.querySelector('[data-line="when"]')?.hasAttribute('data-soft')).toBe(false);
    expect(card.querySelector('[data-line="meta"] furar-icon')).toBeNull();
    expect(card.querySelector('[data-line="meta"]')?.hasAttribute('data-soft')).toBe(true);
    expect([...card.querySelectorAll('[data-line="meta"] .f-status-card__part')].map(text)).toEqual([
      'Fútbol sala',
      'Pista enteira',
    ]);
    // O « · » vai dentro do anaco de antes, pegado ao dato: nunca empeza unha liña.
    expect([...card.querySelectorAll('[data-line="meta"] .f-status-card__seg')].map((n) => n.textContent)).toEqual([
      'Fútbol sala\u00a0·',
      'Pista enteira',
    ]);
    expect(text(card.querySelector('.f-status-card__details .extra'))).toBe('Contido proxectado');
    expect([...card.querySelectorAll('[data-card-chips] furar-tag')].map(text)).toEqual([
      'Pendente de pago',
      'Buscando xente',
    ]);
    const ids = controls[0].getAttribute('aria-describedby')!.split(' ');
    expect(ids.map((id) => text(document.getElementById(id)))).toEqual([
      '20,00 €',
      'venres, 20 de novembro · 20:00–21:30Fútbol sala · Pista enteiraContido proxectadoPendente de pagoBuscando xente',
    ]);
    (controls[0] as HTMLButtonElement).click();
    expect(host.opened()).toBe(1);
    await expectNoAxeViolations(el);
  });

  it('sen importe, con miniatura, pasada e con título h3', async () => {
    const { el, host, fixture } = await render();
    host.amount.set(null);
    host.cover.set({ thumb: '/thumb.jpg', width: 1200, height: 800 });
    host.level.set(3);
    host.past.set(true);
    await fixture.whenStable();
    const card = el.querySelector('furar-status-card')!;
    expect(card.querySelector('[data-card-amount]')).toBeNull();
    expect(card.querySelector('h2')).toBeNull();
    const button = card.querySelector('h3 > button')!;
    expect(button.getAttribute('aria-describedby')?.split(' ')).toHaveLength(1);
    const img = card.querySelector<HTMLImageElement>('[data-card-cover]')!;
    expect([img.getAttribute('src'), img.getAttribute('alt'), img.getAttribute('width')]).toEqual([
      '/thumb.jpg',
      '',
      '1200',
    ]);
    expect(card.getAttribute('data-past')).toBe('true');
    host.cover.set({ thumb: '/other.jpg' });
    await fixture.whenStable();
    expect(card.querySelector('[data-card-cover]')?.hasAttribute('width')).toBe(false);
    (button as HTMLButtonElement).click();
    expect(host.opened()).toBe(1);
  });
});
