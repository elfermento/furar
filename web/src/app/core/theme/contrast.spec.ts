// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { checkTheme, contrastRatio, fixContrast, fixDirection, fixedColor } from './contrast';

const THEME = { primary: '#0b4f8a', onPrimary: '#ffffff', accent: '#8a4b00', background: '#f4f7fa', text: '#1a1a1a' };

describe('Axustar o contraste do tema', () => {
  it('cambia só a cor do par e só ata chegar ao mínimo', () => {
    const light = { ...THEME, primary: '#3995d4' };
    expect(checkTheme(light).find((c) => c.pair === 'onPrimaryOnPrimary')?.ok).toBe(false);
    expect(fixedColor('onPrimaryOnPrimary')).toBe('primary');
    expect(fixDirection(light, 'onPrimaryOnPrimary')).toBe('darken');
    const fixed = fixContrast(light, 'onPrimaryOnPrimary');
    expect(fixed.onPrimary).toBe('#ffffff');
    expect(fixed.background).toBe(THEME.background);
    const ratio = contrastRatio(fixed.onPrimary, fixed.primary);
    expect(ratio).toBeGreaterThanOrEqual(4.5);
    expect(ratio).toBeLessThan(4.9);
  });

  it('sobre un fondo escuro aclara; sen par, arranxa todos', () => {
    const dark = { ...THEME, background: '#111111', text: '#333333', accent: '#222222', primary: '#202020' };
    expect(fixDirection(dark, 'textOnBackground')).toBe('lighten');
    const fixed = fixContrast(dark);
    expect(checkTheme(fixed).every((c) => c.ok)).toBe(true);
    expect(fixContrast(THEME)).toEqual(THEME);
  });
});
