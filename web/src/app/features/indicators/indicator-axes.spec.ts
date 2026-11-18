// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { hBars } from '../../shared/ui/chart/chart-options';
import { chartTokensFrom } from '../../shared/ui/chart/chart-tokens';
import { DEFAULT_THEME, deriveTokens } from '../../core/theme/theme-tokens';
import { barsHeight, nameLines, namesAbove } from './indicator-axes';

const tokens = chartTokensFrom(deriveTokens(DEFAULT_THEME));
const LONG = 'Complexo Deportivo Municipal San Diego · Pista polideportiva (1/3)';
const names = ['Piscina', LONG, 'Polideportivo Municipal de Novo Mesoiro'];
/** Unha letra = 10 px (o espazo tamén). */
const tens = (text: string) => text.length * 10;
const line = Math.round(tokens.fontSize * 1.3);

describe('Nomes enriba das barras deitadas (ocupación e ingresos por instalación)', () => {
  it('conta as liñas partindo por palabras co ancho medido', () => {
    expect(nameLines('Piscina', 200, tens)).toBe(1);
    // «Pista de» (80) · «pádel 2 de O» (120) · «Alto» (40), en 120 px.
    expect(nameLines('Pista de pádel 2 de O Alto', 120, tens)).toBe(3);
    // Unha palabra máis longa ca a liña pártese: 210 px en 100.
    expect(nameLines('Supercalifragilístico', 100, tens)).toBe(3);
  });

  it('o nome vai nunha serie baleira enriba da barra; sen liña do eixo nin tooltip do nome', () => {
    const option = namesAbove(
      hBars(tokens, { categories: names, series: { name: 'Ocupación', values: [10, 20, 30] }, max: 100 }),
      340,
      tokens,
    );
    const axis = option.yAxis as { inverse: boolean; axisLine: { show: boolean }; axisLabel: { show: boolean } };
    expect(axis.inverse).toBe(true);
    expect(axis.axisLine.show).toBe(false);
    expect(axis.axisLabel.show).toBe(false);
    expect(option.grid).toEqual(expect.objectContaining({ left: 0, right: 56, top: 4 }));
    const [label, bar] = option.series as {
      barWidth: number;
      data: unknown[];
      tooltip?: { show: boolean };
      label: Record<string, unknown> & { formatter: (p: { name: string }) => string; position: number[] };
    }[];
    expect(label!.tooltip?.show).toBe(false);
    expect(label!.data).toEqual([0, 0, 0]);
    expect(label!.label.formatter({ name: LONG })).toBe(LONG);
    expect(label!.label).toEqual(expect.objectContaining({ width: 284, overflow: 'break', verticalAlign: 'bottom' }));
    // O oco do nome remata onde empeza a barra.
    expect(label!.label.position).toEqual([0, label!.barWidth]);
    expect((label!.barWidth - 4) % line).toBe(0);
    expect(bar!.barWidth).toBe(20);
  });

  it('cada fila, xusta: as liñas do nome máis longo, a barra e 12 px; máis alta no móbil', () => {
    const height = barsHeight(names);
    const narrow = height(340, tokens);
    const wide = height(1100, tokens);
    expect(wide).toBe(4 + names.length * (line + 4 + 20 + 12) + 32);
    expect(narrow).toBeGreaterThan(wide);
    expect((narrow - 36) % names.length).toBe(0);
    expect(((narrow - 36) / names.length - 36) % line).toBe(0);
  });
});
