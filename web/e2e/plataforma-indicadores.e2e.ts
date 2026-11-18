// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { APIRequestContext } from '@playwright/test';
import { demoEmail, expect, loginAs, navName, test, text, userMenu } from './fixtures';
import { chooseExport, readCsv } from './indicadores-axudas';
import { checkStep, hermetic } from './instalacions-axudas';
import { bearer } from './persoal-axudas';
import type { StaffMember } from './persoal-axudas';

const JOIN_SLUG = 'ferrol';

/** O persoal de Ferrol (as axudas de persoal-axudas.ts son de Narón). */
async function ferrolStaff(request: APIRequestContext, headers: Record<string, string>): Promise<StaffMember[]> {
  const response = await request.get(`/api/v1/municipalities/${JOIN_SLUG}/staff`, { headers });
  expect(response.status()).toBe(200);
  return (await response.json()) as StaffMember[];
}

/** Desactiva unha pertenza de Ferrol. */
async function deactivateInFerrol(
  request: APIRequestContext,
  headers: Record<string, string>,
  member: StaffMember,
): Promise<void> {
  const response = await request.post(`/api/v1/municipalities/${JOIN_SLUG}/staff/${member.id}/deactivate`, {
    headers: { ...headers, 'If-Match': `"${member.version}"` },
  });
  expect([200, 204], `desactivar ${member.email}: ${await response.text()}`).toContain(response.status());
}

/**
 * Indicadores da plataforma e «Engadirme como xestor» (ADR-0026 e ADR-0028,
 * revisións do 2026-11-11). A plataforma entra desde o menú da conta, ve catro cifras sumadas e
 * unha fila por concello sen ligazóns, ordena desde a cabeceira (no móbil, «Ordenar») e descarga o
 * CSV. Despois faise xestora de Ferrol desde «Persoal», co rastro e o menú da xestión completa; a
 * limpeza desactiva esa pertenza. En Ferrol e non en Narón: unha pertenza, aínda desactivada, fai
 * que as invitacións que despois faga a plataforma nese concello saian como de persoal, e as probas
 * do persoal de Narón esperan as da plataforma.
 *
 * Axe en cada paso (en gl-mobile, 320 px sen scroll horizontal); sen peticións fóra de localhost.
 */
test.describe('plataforma: indicadores e engadirse como xestor', () => {
  test('indicadores da plataforma: menú, cifras, táboa sen ligazóns, orde e CSV', async ({ page, request, lang }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    const mobile = test.info().project.name.includes('mobile');

    // Só a plataforma: o xestor dun concello non ve os doutros.
    const manager = await bearer(request, 'xestor-naron');
    expect((await request.get('/api/v1/indicators', { headers: manager })).status()).toBe(403);

    await page.goto('/');
    await loginAs(page, 'admin-plataforma', lang);
    await userMenu(page).click();
    const entry = page.getByRole('menuitem', { name: text(lang, 'session.platformIndicators') });
    // Xusto antes do rexistro da plataforma.
    const items = page.locator('.f-usermenu__panel [data-item]');
    const ids = await items.evaluateAll((all) => all.map((e) => e.getAttribute('data-item')));
    expect(ids.indexOf('platform-indicators') + 1).toBe(ids.indexOf('platform-activity'));
    await entry.click();
    await expect(page).toHaveURL(/\/plataforma\/indicadores$/);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'platformIndicators.title') })).toBeVisible();
    await expect(page.locator('furar-stat-tile')).toHaveCount(4);
    await expect(page.locator('[data-kpi="people"]')).toHaveCount(0);
    // O período co chip de datas de toda a app, non un segmentado.
    await expect(page.locator('[data-period-filter]')).toBeVisible();
    await expect(page.locator('[data-platform-municipalities] a')).toHaveCount(0);

    if (mobile) {
      const cards = page.locator('[data-platform-cards] [data-municipality]');
      await expect(cards.first()).toBeVisible();
      await expect(page.locator('[data-platform-table]')).toBeHidden();
      await page.locator('[data-platform-sort] .f-field-popover__trigger').click();
      await page.locator('[data-column-filter] [data-option="name"]').click();
      // A lista reordénase despois do clic: agárdase a que quede por nome.
      await expect
        .poll(async () => {
          const names = await cards.locator('.f-pindicators__name').allTextContents();
          return names.join('|') === [...names].sort((a, b) => a.localeCompare(b, lang)).join('|');
        })
        .toBe(true);
    } else {
      const rows = page.locator('[data-platform-table] tbody tr');
      await expect(rows.first()).toBeVisible();
      await expect(page.locator('[data-column="bookings"]')).toHaveAttribute('aria-sort', 'descending');
      await page.locator('[data-column="name"] .f-field-popover__trigger').click();
      await page
        .locator(`[data-column-filter="${text(lang, 'platformIndicators.column.name')}"] [data-option="asc"]`)
        .click();
      await expect(page.locator('[data-column="name"]')).toHaveAttribute('aria-sort', 'ascending');
      const names = await rows.locator('th').allTextContents();
      expect(names.map((n) => n.trim())).toEqual(
        [...names.map((n) => n.trim())].sort((a, b) => a.localeCompare(b, lang)),
      );
      // As cifras, á dereita (`text-align: end`, en galego e castelán de esquerda a dereita).
      await expect(rows.first().locator('td').first()).toHaveCSS('text-align', 'end');
    }
    await checkStep(page);

    const downloading = page.waitForEvent('download');
    await chooseExport(page, page.locator('.f-pindicators__filters furar-export-menu button').first(), 'csv');
    const csv = await readCsv(await downloading);
    expect(csv.name).toMatch(/^indicadores-plataforma-.+\.csv$/);
    expect(csv.body).toContain(text(lang, 'platformIndicators.column.people'));
    expect(csv.body).not.toMatch(/@example\.org/);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('engadirme como xestor: confirmación, rastro e xestión completa', async ({ page, request, lang }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    const platform = await bearer(request, 'admin-plataforma');
    const email = demoEmail('admin-plataforma');
    const mine = (list: StaffMember[]) =>
      list.find((m) => m.email === email && m.status === 'ACTIVE' && m.role === 'MANAGER');
    // Por se quedou dunha execución anterior.
    const left = mine(await ferrolStaff(request, platform));
    if (left) await deactivateInFerrol(request, platform, left);
    try {
      await page.goto(`/${JOIN_SLUG}`);
      await loginAs(page, 'admin-plataforma', lang);
      await page.goto(`/${JOIN_SLUG}/xestion/concello/persoal`);
      const join = page.locator('[data-join]');
      await expect(join).toHaveText(text(lang, 'staff.members.join.open'));
      await expect(page.locator('[data-invite]')).toBeVisible();
      await join.click();
      const confirm = page.getByRole('alertdialog');
      const ofName = 'de Ferrol (demo)';
      await expect(confirm.locator('h2')).toHaveText(text(lang, 'staff.members.join.title', { ofName }));
      await expect(confirm).toContainText(text(lang, 'staff.members.join.sees', { ofName }));
      await expect(confirm).toContainText(text(lang, 'staff.members.join.logged'));
      await checkStep(page);
      await confirm.locator('[data-confirm-accept]').click();
      await expect(page.locator('furar-toast')).toContainText(text(lang, 'staff.members.done.joined', { ofName }));
      await expect(page.locator('[data-join]')).toHaveCount(0);
      await expect(page.locator('[data-group="current"] [data-email]', { hasText: email }).first()).toBeVisible();
      // Agora ve as reservas e os indicadores do concello.
      const nav = page.getByRole('navigation', { name: text(lang, 'app.manageNav') }).first();
      await expect(nav.getByRole('link', { name: navName(lang, 'nav.bookings') })).toBeVisible();
      await checkStep(page);
      // Rastro no rexistro da plataforma.
      const log = await request.get('/api/v1/audit-events', { headers: platform });
      expect(JSON.stringify(await log.json())).toContain('staff.join');
      expect(outside, 'peticións fóra de localhost').toEqual([]);
    } finally {
      const joined = mine(await ferrolStaff(request, platform));
      if (joined) await deactivateInFerrol(request, platform, joined);
    }
  });
});
