// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { checkTheme, contrastRatio, isHexColor, luminance } from './contrast';
import { ThemeService } from './theme.service';

describe('contraste e tema', () => {
  it('calcula o contraste WCAG (negro/branco = 21:1)', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 1);
    expect(contrastRatio('#ffffff', '#ffffff')).toBeCloseTo(1, 5);
    expect(isHexColor('#12abEF')).toBe(true);
    expect(isHexColor('12abef')).toBe(false);
    expect(() => luminance('azul')).toThrow();
  });

  it('marca os pares que non chegan ao mínimo (texto 4,5:1, primary 3:1)', () => {
    const checks = checkTheme({
      primary: '#777777',
      onPrimary: '#ffffff',
      accent: '#8a4b00',
      background: '#ffffff',
      text: '#1a1a1a',
    });
    expect(checks.find((c) => c.pair === 'onPrimaryOnPrimary')?.ok).toBe(false);
    expect(checks.find((c) => c.pair === 'primaryOnBackground')?.ok).toBe(true);
    expect(checks.find((c) => c.pair === 'textOnBackground')?.ok).toBe(true);
    expect(checkTheme({ primary: 'x', onPrimary: '', accent: '', background: '', text: '' })).toEqual([]);
  });

  it('aplica e quita as variables CSS en <html>', async () => {
    const service = TestBed.inject(ThemeService);
    const meta = document.createElement('meta');
    meta.name = 'theme-color';
    document.head.append(meta);
    await service.apply({
      primary: '#112233',
      onPrimary: '#ffffff',
      accent: '#000000',
      background: '#fafafa',
      text: '#111111',
    });
    expect(document.documentElement.style.getPropertyValue('--furar-primary')).toBe('#112233');
    expect(meta.getAttribute('content')).toBe('#112233');
    // Cores derivadas con contraste comprobado (ADR-0021).
    const style = document.documentElement.style;
    expect(style.getPropertyValue('--f-primary')).toBe('#112233');
    expect(style.getPropertyValue('--f-surface')).toBe('#fafafa');
    expect(style.getPropertyValue('--f-tint')).not.toBe('');
    expect(style.getPropertyValue('color-scheme')).toBe('light');
    // O esquema queda para o tema de Keycloak (mesma orixe).
    expect(localStorage.getItem('furar.colorScheme')).toBe('light');
    service.reset();
    expect(localStorage.getItem('furar.colorScheme')).toBeNull();
    expect(style.getPropertyValue('--furar-primary')).toBe('');
    expect(style.getPropertyValue('--f-tint')).toBe('');
    // Un tema incompleto pon as súas variables pero non deriva (quedan as de tokens.css).
    await service.apply({ primary: '#112233', onPrimary: '', accent: '', background: '', text: '' });
    expect(style.getPropertyValue('--furar-primary')).toBe('#112233');
    expect(style.getPropertyValue('--f-primary')).toBe('');
    service.reset();
    meta.remove();
  });

  it('un tema que chega tarde non pisa o seguinte nin un reset (chunk das cores derivadas)', async () => {
    const service = TestBed.inject(ThemeService);
    const style = document.documentElement.style;
    const dark = {
      primary: '#7ab8ff',
      onPrimary: '#000000',
      accent: '#ffcc66',
      background: '#16181d',
      text: '#f2f2f2',
    };
    const light = {
      primary: '#112233',
      onPrimary: '#ffffff',
      accent: '#000000',
      background: '#fafafa',
      text: '#111111',
    };
    const first = service.apply(dark);
    const second = service.apply(light);
    await Promise.all([first, second]);
    expect(style.getPropertyValue('--f-surface')).toBe('#fafafa');
    expect(localStorage.getItem('furar.colorScheme')).toBe('light');
    await service.apply(dark);
    expect(localStorage.getItem('furar.colorScheme')).toBe('dark');
    const late = service.apply(dark);
    service.reset();
    await late;
    expect(style.getPropertyValue('--f-surface')).toBe('');
    expect(style.getPropertyValue('--furar-primary')).toBe('');
  });
});
