// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { expectNoAxeViolations } from '../../testing/axe';
import { provideDeploymentSettings } from '../../testing/deployment';
import { provideTestI18n } from '../../testing/i18n';
import { routes } from '../app.routes';
import { LanguageService } from '../core/i18n/language.service';
import { CHART_ENGINE } from '../shared/ui/chart/chart-engine';
import { CatalogPage } from './catalog.page';

describe('CatalogPage (só desenvolvemento)', () => {
  async function render(lang: 'gl' | 'es' = 'gl') {
    TestBed.configureTestingModule({
      // jsdom non ten canvas: o motor dos gráficos non chega e furar-chart queda baleiro (o seu spec próbao).
      providers: [
        provideTestI18n(lang),
        provideRouter([]),
        provideDeploymentSettings(),
        { provide: CHART_ENGINE, useValue: () => new Promise(() => undefined) },
      ],
    });
    const fixture = TestBed.createComponent(CatalogPage);
    await TestBed.inject(LanguageService).use(lang);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  it('a ruta existe en desenvolvemento, antes da do concello (non a colle :municipality)', () => {
    const paths = routes.map((route) => route.path);
    expect(paths).toContain('catalogo-ui');
    expect(paths.indexOf('catalogo-ui')).toBeLessThan(paths.indexOf(':municipality'));
  });

  it('pinta todas as pezas de shared/ui con textos traducidos e pasa axe', async () => {
    const { el } = await render();
    expect(el.querySelector('h1')?.textContent).toContain('Catálogo de compoñentes');
    for (const selector of [
      '.f-button[data-variant="primary"]',
      '.f-button[aria-busy="true"]',
      '.f-input[aria-invalid="true"]',
      '.f-fieldset legend',
      'furar-callout[data-tone="warn"]',
      'ul.f-row-list a.f-row__link',
      'furar-status-state[role="status"]',
      'furar-tag[data-tone="danger"]',
      'furar-card[data-tone="tint"]',
      'furar-time-picker button[aria-haspopup="listbox"]',
      'furar-select button[aria-haspopup="listbox"]',
      'furar-select button[aria-invalid="true"]',
      'furar-select button:disabled',
      'furar-date-field button[aria-haspopup="dialog"]',
      'furar-calendar table[role="grid"]',
      'furar-map.f-map--editable [role="group"]',
      'furar-tag[data-tone="muted"]',
      'furar-tag[data-look="soft"]',
      'furar-chip-menu button[aria-haspopup="menu"]',
      'furar-switch button[role="switch"][aria-checked="true"]',
      'button[data-open-dialog]',
      'button[data-open-affected]',
      '.f-segmented[role="radiogroup"] [role="radio"][aria-checked="true"]',
      'furar-tabs [role="tablist"] [role="tab"][aria-selected="true"]',
      'furar-combobox input[role="combobox"]',
      'furar-media-card h3 a',
      'button.f-tile',
      'furar-bilingual-field textarea[lang]',
      'button[data-open-type-confirm]',
      'furar-status-state[data-mode="skeleton"][data-shape="card"][role="status"]',
      'furar-edit-section section[aria-labelledby] form',
      'furar-edit-field button.f-edit-field__trigger',
      'furar-carousel[data-reorderable] ul[aria-label] li.f-carousel__item',
      'furar-delete-button button[data-variant="danger"][data-round="true"][aria-label]',
      'furar-user-menu button[aria-haspopup="menu"] .f-usermenu__avatar',
      'button.f-filter-chip:not([data-tone])[aria-pressed="true"]',
      'button.f-filter-chip[data-tone="warn"][aria-pressed="true"]',
      'button.f-filter-chip[data-tone="muted"][aria-pressed="false"]',
      'furar-field-popover button[aria-haspopup="dialog"][aria-expanded="false"]',
      'furar-weekday-picker[role="group"] [role="checkbox"][aria-checked="true"]',
      'furar-qr svg[role="img"][aria-label*="FUR-7K2Q"]',
      'furar-value-list dl [data-value="day"] dd',
      '#graficos furar-chart[data-size="spark"] .f-chart__canvas[aria-hidden="true"]',
      '#indicadores furar-stat-tile button[aria-haspopup="dialog"] .f-stat-tile__delta[data-trend="down"]',
      '#indicadores furar-chart-card figure[role="img"][aria-label] furar-chart',
      '#indicadores furar-chart-card button[aria-pressed="false"][aria-controls]',
      '#indicadores furar-export-menu button[aria-haspopup="menu"]',
      '#indicadores furar-data-table table caption',
    ]) {
      expect(el.querySelector(selector), selector).not.toBeNull();
    }
    expect(el.textContent).not.toContain('catalog.');
    await expectNoAxeViolations(el);
  }, 20_000); // axe sobre o catálogo enteiro pasa dos 5 s cando a suite corre en paralelo

  it('en castelán, coas pezas do dominio 2, tamén pasa axe', async () => {
    const { el } = await render('es');
    expect(el.querySelector('h1')?.textContent).toContain('Catálogo de componentes');
    expect(el.querySelector('furar-weekday-picker [role="checkbox"]')?.getAttribute('aria-label')).toBe('lunes');
    expect(el.querySelector('furar-qr svg')?.getAttribute('aria-label')).toBe('Código QR de la reserva FUR-7K2Q');
    expect(el.textContent).not.toContain('catalog.');
    await expectNoAxeViolations(el);
  }, 20_000);

  it('os temas de proba aplícanse por variables e quítanse ao saír', async () => {
    const { fixture, el } = await render();
    const style = document.documentElement.style;
    const dark = [...el.querySelectorAll<HTMLButtonElement>('[role="group"] button')].find((b) =>
      b.textContent?.includes('Fondo escuro'),
    );
    dark?.click();
    await fixture.whenStable();
    expect(dark?.getAttribute('aria-pressed')).toBe('true');
    // As cores derivadas chegan co seu chunk (theme.service.ts).
    await vi.waitFor(() => expect(style.getPropertyValue('--f-page')).toBe('#0d1117'));
    expect(style.getPropertyValue('color-scheme')).toBe('dark');
    fixture.destroy();
    expect(style.getPropertyValue('--f-surface')).toBe('');
  });
});
