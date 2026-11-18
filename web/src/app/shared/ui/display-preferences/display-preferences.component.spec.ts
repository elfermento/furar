// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { SessionService } from '../../../core/auth/session.service';
import { DISPLAY_PREFERENCES_REMOTE } from '../../../core/preferences/display-preferences';
import { DisplayPreferencesComponent } from './display-preferences.component';

@Component({
  imports: [DisplayPreferencesComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<p><button type="button" data-outside>Fóra</button></p><furar-display-preferences />',
})
class HostComponent {}

describe('DisplayPreferencesComponent', () => {
  const authenticated = signal(false);

  async function render(remote: unknown = null) {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideTestI18n(),
        { provide: SessionService, useValue: { authenticated } },
        { provide: DISPLAY_PREFERENCES_REMOTE, useValue: remote },
      ],
    });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.autoDetectChanges();
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    document.body.append(el);
    const trigger = el.querySelector<HTMLButtonElement>('.f-display-prefs__trigger')!;
    const open = async () => {
      trigger.click();
      await vi.waitFor(async () => {
        await fixture.whenStable();
        expect(el.querySelector('[role="radiogroup"]')).not.toBeNull();
      });
    };
    return { fixture, el, trigger, open };
  }

  afterEach(() => {
    localStorage.clear();
    authenticated.set(false);
  });

  it('botón co nome e o title «Accesibilidade» que abre o panel non modal, sen iconas dentro', async () => {
    const { el, trigger, open } = await render();
    expect(trigger.getAttribute('aria-label')).toBe('Accesibilidade');
    expect(trigger.getAttribute('title')).toBe('Accesibilidade');
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
    await open();
    expect(trigger.getAttribute('aria-expanded')).toBe('true');
    const panel = el.querySelector<HTMLElement>('[role="dialog"]')!;
    expect(panel.getAttribute('aria-label')).toBe('Preferencias de accesibilidade');
    expect(trigger.getAttribute('aria-controls')).toBe(panel.id);
    expect(panel.querySelector('furar-icon')).toBeNull();
    expect(panel.textContent).toContain('Gárdase neste navegador.');
    const sizes = [...panel.querySelectorAll<HTMLButtonElement>('[role="radio"]')];
    expect(sizes.map((b) => [b.textContent?.trim(), b.getAttribute('aria-checked')])).toEqual([
      ['Normal', 'true'],
      ['Grande', 'false'],
      ['Moi grande', 'false'],
    ]);
    // O foco vai ao tamaño escollido.
    await vi.waitFor(() => expect(document.activeElement).toBe(sizes[0]));
    const switches = [...panel.querySelectorAll<HTMLButtonElement>('[role="switch"]')];
    expect(switches.map((s) => s.labels?.[0]?.textContent?.trim())).toEqual(['Alto contraste', 'Menos movemento']);
    await expectNoAxeViolations(el);
  });

  it('cada axuste aplícase ao momento en <html>', async () => {
    const { el, fixture, open } = await render();
    await open();
    el.querySelector<HTMLButtonElement>('[data-size="x-large"]')!.click();
    expect(document.documentElement.getAttribute('data-text-size')).toBe('x-large');
    el.querySelector<HTMLButtonElement>('[data-pref="contrast"] [role="switch"]')!.click();
    el.querySelector<HTMLButtonElement>('[data-pref="motion"] [role="switch"]')!.click();
    await fixture.whenStable();
    expect(document.documentElement.getAttribute('data-contrast')).toBe('more');
    expect(document.documentElement.getAttribute('data-motion')).toBe('reduce');
    expect(el.querySelector('[data-pref="contrast"] [role="switch"]')?.getAttribute('aria-checked')).toBe('true');
    expect(JSON.parse(localStorage.getItem('furar.display') ?? '{}')).toEqual({
      textSize: 'x-large',
      highContrast: true,
      reducedMotion: true,
      origin: 'device',
    });
  });

  it('Esc pecha e devolve o foco ao botón; o clic fóra tamén pecha', async () => {
    const { el, fixture, trigger, open } = await render();
    await open();
    const panel = el.querySelector<HTMLElement>('[role="dialog"]')!;
    panel
      .querySelector<HTMLElement>('[role="radio"]')!
      .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await fixture.whenStable();
    expect(el.querySelector('[role="dialog"]')).toBeNull();
    expect(document.activeElement).toBe(trigger);
    await open();
    el.querySelector('[data-outside]')!.dispatchEvent(new Event('pointerdown', { bubbles: true }));
    await fixture.whenStable();
    expect(el.querySelector('[role="dialog"]')).toBeNull();
    expect(trigger.getAttribute('aria-expanded')).toBe('false');
  });

  it('con conta e porto remoto, di que se garda na conta', async () => {
    authenticated.set(true);
    const remote = { load: vi.fn().mockResolvedValue(null), save: vi.fn().mockResolvedValue(undefined) };
    const { el, open } = await render(remote);
    await open();
    expect(el.querySelector('[role="dialog"]')?.textContent).toContain('Gárdase na túa conta.');
  });
});
