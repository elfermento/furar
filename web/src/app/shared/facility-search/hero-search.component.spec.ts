// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { expectNoAxeViolations } from '../../../testing/axe';
import { provideTestI18n } from '../../../testing/i18n';
import { FacilitySearchComponent } from './facility-search.component';
import { HeroSearchComponent } from './hero-search.component';

@Component({
  imports: [FacilitySearchComponent, HeroSearchComponent],
  template: `
    <button type="button" id="open" (click)="hero.open($event)">Buscar</button>
    <furar-hero-search #hero [pinned]="pinned()" [count]="count()">
      <furar-facility-search [facilities]="[]" />
    </furar-hero-search>
  `,
})
class HostComponent {
  readonly pinned = signal(false);
  readonly count = signal(0);
}

describe('furar-hero-search', () => {
  let wide = true;

  beforeEach(() => {
    wide = true;
    vi.stubGlobal('matchMedia', (query: string) => ({
      matches: query.startsWith('(min-width') ? wide : false,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }));
  });
  afterEach(() => vi.unstubAllGlobals());

  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n(), provideRouter([])] });
    const fixture = TestBed.createComponent(HostComponent);
    document.body.appendChild(fixture.nativeElement);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const host = el.querySelector<HTMLElement>('furar-hero-search')!;
    const settle = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
    };
    const scrollTo = async (y: number) => {
      Object.defineProperty(window, 'scrollY', { value: y, configurable: true });
      window.dispatchEvent(new Event('scroll'));
      await settle();
    };
    return { fixture, el, host, settle, scrollTo };
  }

  it('escondida ata «Buscar»: sae co foco no texto e queda', async () => {
    const { el, host, settle } = await render();
    expect(host.hasAttribute('data-open')).toBe(false);
    el.querySelector<HTMLButtonElement>('#open')!.click();
    await settle();
    expect(host.hasAttribute('data-open')).toBe(true);
    await vi.waitFor(() => expect(document.activeElement).toBe(host.querySelector('input[role="combobox"]')));
  });

  it('con filtros xa está fóra e queda aínda que se quiten', async () => {
    const { fixture, host, settle } = await render();
    fixture.componentInstance.pinned.set(true);
    await settle();
    expect(host.hasAttribute('data-open')).toBe(true);
    fixture.componentInstance.pinned.set(false);
    await settle();
    expect(host.hasAttribute('data-open')).toBe(true);
  });

  it('escritorio: baixar recóllea na pílula co número; subir non a abre; picar a pílula si', async () => {
    const { fixture, host, settle, scrollTo } = await render();
    fixture.componentInstance.pinned.set(true);
    fixture.componentInstance.count.set(2);
    await settle();
    (document.activeElement as HTMLElement | null)?.blur();
    await scrollTo(400);
    expect(host.hasAttribute('data-collapsed')).toBe(true);
    const pill = host.querySelector<HTMLButtonElement>('[data-search-pill]')!;
    expect(pill.getAttribute('aria-label')).toBe('Buscar, filtros postos: 2');
    expect(pill.querySelector('.f-search__count')?.textContent?.trim()).toBe('2');
    await expectNoAxeViolations(host);
    await scrollTo(100);
    expect(host.hasAttribute('data-collapsed')).toBe(true);
    pill.click();
    await settle();
    expect(host.hasAttribute('data-collapsed')).toBe(false);
    expect(host.querySelector('[data-search-pill]')).toBeNull();
    await vi.waitFor(() => expect(document.activeElement).toBe(host.querySelector('input[role="combobox"]')));
    // Co foco no texto, baixar non a recolle.
    await scrollTo(800);
    expect(host.hasAttribute('data-collapsed')).toBe(false);
  });

  it('no móbil non se recolle ao baixar', async () => {
    wide = false;
    const { fixture, host, settle, scrollTo } = await render();
    fixture.componentInstance.pinned.set(true);
    await settle();
    await scrollTo(600);
    expect(host.hasAttribute('data-collapsed')).toBe(false);
  });
});
