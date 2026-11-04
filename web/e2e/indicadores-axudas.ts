// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { readFile } from 'node:fs/promises';
import type { Download, Locator, Page } from '@playwright/test';
import { type DemoUser, expect, loginAs, text } from './fixtures';

/**
 * Axudas dos e2e dos indicadores (D3-10): o panel que pide a propia páxina (as cifras esperadas
 * saen da API, non de números fixos da demo), o tema escuro do concello con `page.route`, abrir
 * a pantalla, ler os CSV descargados, «Exportar» co teclado e `window.print` substituído.
 */

/** Tema escuro dun concello (o mesmo dos tests do tema): fondo #0d1117 case negro, texto case branco e primary claro. */
export const DARK_THEME = {
  primary: '#4493f8',
  onPrimary: '#0a0a0a',
  accent: '#f0b46a',
  background: '#0d1117',
  text: '#f0f6fc',
};

export const CHART_IDS = ['occupancy', 'daily', 'peak', 'cancellations', 'revenue', 'people'] as const;
export const KPI_IDS = ['bookings', 'occupancy', 'reservedHours', 'people', 'paidCents'] as const;
export const KPI_FILES: Record<(typeof KPI_IDS)[number], string> = {
  bookings: 'reservas',
  occupancy: 'ocupacion',
  reservedHours: 'horas',
  people: 'persoas',
  paidCents: 'cobrado',
};

export interface Panel {
  period: { kind: 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR' | 'CUSTOM'; from: string; to: string };
  /** Os tramos da variación: no período en curso, ata hoxe (ADR-0026, revisión do 2026-10-29). */
  comparison: { toDate: boolean; from: string; to: string; comparedFrom: string; comparedTo: string };
  kpis: Record<
    'bookings' | 'people' | 'paidCents',
    {
      value: number;
      previous: number;
      valueToDate: number;
      previousToDate: number;
      evolution: { from: string; partial: boolean }[];
    }
  >;
  occupancyByFacility: { name: string }[];
  dailyOccupancy: { from: string; partial: boolean }[];
}

/** A parte do período no nome dos ficheiros (`indicator-period.ts`). */
export function periodFile(period: Panel['period']): string {
  const year = period.from.slice(0, 4);
  switch (period.kind) {
    case 'MONTH':
      return period.from.slice(0, 7);
    case 'QUARTER':
      return `${year}-t${Math.floor((Number(period.from.slice(5, 7)) - 1) / 3) + 1}`;
    case 'YEAR':
      return year;
    case 'WEEK':
      return `semana-${period.from}`;
    default:
      return `${period.from}_${period.to}`;
  }
}

/** Tema escuro para Narón: a resposta real do concello co tema cambiado. */
export async function darkTheme(page: Page, slug = 'naron'): Promise<void> {
  await page.route(new RegExp(`/api/v1/municipalities/${slug}(\\?.*)?$`), async (route) => {
    const response = await route.fetch();
    const json = (await response.json()) as Record<string, unknown>;
    await route.fulfill({ response, json: { ...json, theme: DARK_THEME } });
  });
}

/**
 * Entra como `user` e abre «Indicadores» desde o menú da xestión; espera o panel e os gráficos e
 * devolve a resposta da API que pintou a páxina (o esperado sae de aí, non de cifras fixas).
 */
export async function openIndicators(
  page: Page,
  lang: 'gl' | 'es',
  user: DemoUser = 'xestor-naron',
  slug = 'naron',
): Promise<Panel> {
  // A resposta lese ao pasar pola ruta: un `waitForResponse` pode quedar sen corpo.
  const panels: Panel[] = [];
  await page.route(/\/api\/v1\/municipalities\/[^/]+\/indicators\?/, async (route) => {
    try {
      const response = await route.fetch();
      if (response.ok()) panels.push((await response.json()) as Panel);
      await route.fulfill({ response });
    } catch {
      // A páxina cancelou a petición (un filtro novo antes de que chegase).
      await route.abort().catch(() => undefined);
    }
  });
  await page.goto(`/${slug}`);
  await loginAs(page, user, lang);
  await page
    .getByRole('navigation', { name: text(lang, 'app.manageNav') })
    .getByRole('link', { name: text(lang, 'nav.indicators') })
    .click();
  await expect(page).toHaveURL(new RegExp(`/${slug}/xestion/indicadores$`));
  await expect(page.getByRole('heading', { level: 1, name: text(lang, 'indicators.title') })).toBeVisible();
  await panelReady(page);
  expect(panels.length, 'resposta do panel').toBeGreaterThan(0);
  return panels.at(-1)!;
}

/** O panel cargado: as cinco cifras, os seis gráficos debuxados e sen `aria-busy`. */
export async function panelReady(page: Page, charts = true): Promise<void> {
  await expect(page.locator('.f-indicators')).not.toHaveAttribute('aria-busy', 'true');
  await expect(page.locator('furar-stat-tile')).toHaveCount(5);
  if (!charts) return;
  await expect(page.locator('furar-chart-card')).toHaveCount(6);
  for (const id of CHART_IDS) {
    await expect(page.locator(`furar-chart-card[data-chart="${id}"] .f-chart__canvas canvas`).first()).toBeAttached();
  }
}

export function tileButton(page: Page, id: string): Locator {
  return page.locator(`furar-stat-tile[data-kpi="${id}"] button`);
}

export function card(page: Page, id: string): Locator {
  return page.locator(`furar-chart-card[data-chart="${id}"]`);
}

/** O CSV descargado: nome, BOM, `;` e o texto (sen BOM). */
export async function readCsv(download: Download): Promise<{ name: string; body: string }> {
  const raw = await readFile((await download.path())!, 'utf8');
  expect(raw.startsWith('﻿'), 'CSV con BOM').toBe(true);
  const body = raw.slice(1);
  expect(body).toContain(';');
  expect(body).toMatch(/\r\n$/);
  return { name: download.suggestedFilename(), body };
}

/** Escolle CSV ou PDF no menú «Exportar» aberto, co teclado. */
export async function chooseExport(page: Page, trigger: Locator, format: 'pdf' | 'csv'): Promise<void> {
  await trigger.focus();
  await page.keyboard.press('Enter');
  const item = page.locator(`[role="menu"] [data-format="${format}"]`);
  await expect(page.locator('[role="menu"] [data-format="pdf"]')).toBeFocused();
  if (format === 'csv') await page.keyboard.press('ArrowDown');
  await expect(item).toBeFocused();
  await page.keyboard.press('Enter');
}

/** `window.print` substituído: anota o alcance, o destino e as imaxes no momento de imprimir. */
export async function stubPrint(page: Page): Promise<void> {
  await page.evaluate(() => {
    const w = window as unknown as { __prints: unknown[] };
    w.__prints = [];
    window.print = () => {
      const root = document.documentElement;
      w.__prints.push({
        scope: root.getAttribute('data-print'),
        targets: [...document.querySelectorAll('[data-print-target]')].map((e) => e.getAttribute('data-chart')),
        images: [...document.querySelectorAll('img.f-chart__print')].map((i) => (i as HTMLImageElement).alt),
      });
    };
  });
}

export interface Printed {
  scope: string | null;
  targets: (string | null)[];
  images: string[];
}

export async function lastPrint(page: Page): Promise<Printed> {
  await expect
    .poll(() => page.evaluate(() => (window as unknown as { __prints: unknown[] }).__prints.length))
    .toBeGreaterThan(0);
  return page.evaluate(() => {
    const prints = (window as unknown as { __prints: Printed[] }).__prints;
    return prints.splice(0)[0]!;
  });
}

/** Remata a impresión (`afterprint`) e comproba que todo volve como estaba. */
export async function finishPrint(page: Page): Promise<void> {
  await page.evaluate(() => window.dispatchEvent(new Event('afterprint')));
  await expect(page.locator('html')).not.toHaveAttribute('data-print', /.*/);
  await expect(page.locator('[data-print-target]')).toHaveCount(0);
  await expect(page.locator('img.f-chart__print')).toHaveCount(0);
}

/** Luminancia relativa (0–1) dunha cor CSS `rgb(…)` ou `#rrggbb`. */
export function luminanceOf(color: string): number {
  const hex = /^#([0-9a-f]{6})$/i.exec(color.trim());
  const parts = hex
    ? [0, 2, 4].map((i) => parseInt(hex[1]!.slice(i, i + 2), 16))
    : (color.match(/\d+(\.\d+)?/g) ?? []).slice(0, 3).map(Number);
  const [r, g, b] = parts.map((v) => {
    const c = v / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!;
}
