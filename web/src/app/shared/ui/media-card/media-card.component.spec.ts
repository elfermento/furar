// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { MediaCardComponent } from './media-card.component';

@Component({
  imports: [MediaCardComponent],
  template: `
    <ul>
      <li>
        <furar-media-card src="/x.jpg" [alt]="alt()" heading="Pista de pádel 1" place="Rúa Buxo, 16" [link]="link()">
          <p data-extra>Fútbol</p>
          <button cardActions type="button">Activa</button>
        </furar-media-card>
      </li>
    </ul>
  `,
})
class HostComponent {
  readonly alt = signal('Pista vista desde arriba');
  readonly link = signal<string[] | null>(['/naron', 'instalacions', '1']);
}

describe('MediaCardComponent', () => {
  function render() {
    TestBed.configureTestingModule({ imports: [HostComponent], providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('estira a ligazón do título e deixa as accións fóra dela', async () => {
    const fixture = render();
    const el = fixture.nativeElement as HTMLElement;
    const link = el.querySelector<HTMLAnchorElement>('h3 a');
    expect(link?.textContent?.trim()).toBe('Pista de pádel 1');
    expect(link?.getAttribute('href')).toBe('/naron/instalacions/1');
    expect(el.querySelector('furar-media-card')?.hasAttribute('data-link')).toBe(true);
    expect(link?.contains(el.querySelector('button'))).toBe(false);
    expect(el.querySelector('.f-media-card__place')?.textContent).toContain('Rúa Buxo, 16');
    expect(el.querySelector('.f-media-card__place furar-icon')?.getAttribute('aria-hidden')).toBe('true');
    expect(el.querySelector('img')?.getAttribute('alt')).toBe('Pista vista desde arriba');
    expect(el.querySelector('[data-extra]')).not.toBeNull();
    await expectNoAxeViolations(el);
  });

  it('sen ligazón o título é texto', () => {
    const fixture = render();
    fixture.componentInstance.link.set(null);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('h3 a')).toBeNull();
    expect(el.querySelector('h3')?.textContent?.trim()).toBe('Pista de pádel 1');
    expect(el.querySelector('furar-media-card')?.hasAttribute('data-link')).toBe(false);
  });
});
