// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { provideTestI18n } from '../../../testing/i18n';
import { LanguageService } from './language.service';
import { pickLanguage } from './languages';

describe('LanguageService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: provideTestI18n() });
  });

  it('escolle o primeiro idioma soportado de navigator.languages', () => {
    expect(pickLanguage(['en-GB', 'es-ES', 'gl'])).toBe('es');
    expect(pickLanguage(['GL-es'])).toBe('gl');
    expect(pickLanguage(['en', 'fr'])).toBeUndefined();
  });

  it('a preferencia gardada gaña ao navegador; sen nada, galego', () => {
    const service = TestBed.inject(LanguageService);
    const languages = vi.spyOn(navigator, 'languages', 'get');
    languages.mockReturnValue(['fr-FR']);
    expect(service.detect()).toBe('gl');
    languages.mockReturnValue(['es-ES']);
    expect(service.detect()).toBe('es');
    localStorage.setItem('furar.lang', 'gl');
    expect(service.detect()).toBe('gl');
    languages.mockRestore();
  });

  it('cambia Transloco, <html lang> e lembra a escolla', async () => {
    const service = TestBed.inject(LanguageService);
    await service.use('es', true);
    expect(service.lang()).toBe('es');
    expect(TestBed.inject(TranslocoService).getActiveLang()).toBe('es');
    expect(TestBed.inject(DOCUMENT).documentElement.lang).toBe('es');
    expect(localStorage.getItem('furar.lang')).toBe('es');
  });
});
