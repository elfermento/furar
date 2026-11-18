// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { APIRequestContext } from '@playwright/test';
import {
  demoTokenForm,
  expect,
  expectNoA11yViolations,
  expectNoHorizontalScroll,
  loginAs,
  managerHeaders,
  test,
  text,
} from './fixtures';

/**
 * Actividades con horario, do lado da veciñanza (ADR-0041): a actividade sae na portada co horario
 * e o chip das prazas; na ficha, coa idade e o prezo co período, apúntase unha persoa a cargo (a
 * única praza), a actividade énchese e outra persoa a cargo entra na lista de espera; en «As miñas
 * reservas», as dúas tarxetas. Con axe en cada pantalla. Na demo, Narón; a actividade, as persoas a
 * cargo e o que queda bórranse ao rematar.
 */

const BASE = '/api/v1/municipalities/naron/events';
const DEPENDENTS = '/api/v1/me/dependents';

async function neighbourHeaders(request: APIRequestContext): Promise<Record<string, string>> {
  const token = await request.post('/auth/realms/furar/protocol/openid-connect/token', {
    form: demoTokenForm('vecino-naron'),
  });
  expect(token.ok(), 'token de furar-e2e').toBe(true);
  const { access_token: accessToken } = (await token.json()) as { access_token: string };
  return { Authorization: `Bearer ${accessToken}` };
}

function isoDay(days: number): string {
  return new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10);
}

test.describe('actividades con horario (veciñanza)', () => {
  test('ver a actividade, apuntar a unha persoa a cargo, enchela e entrar na lista de espera', async ({
    page,
    request,
    lang,
  }, info) => {
    const name = `E2E ${info.project.name} actividade ${Date.now()}`;
    const manager = await managerHeaders(request, 'naron');
    const neighbour = await neighbourHeaders(request);
    const created = await request.post(`${BASE}/manage`, {
      headers: manager,
      data: {
        kind: 'SCHEDULED',
        nameGl: name,
        nameEs: name,
        place: 'Praza do concello',
        capacity: 1,
        priceCents: 2500,
        pricePeriod: 'QUARTER',
        birthYearFrom: 2012,
        birthYearTo: 2018,
        published: true,
        approval: false,
        schedule: {
          weekdays: [1, 3],
          startMinute: 17 * 60,
          endMinute: 18 * 60,
          firstDate: isoDay(7),
          lastDate: isoDay(70),
        },
      },
    });
    expect(created.status(), 'alta da actividade').toBe(201);
    const id = ((await created.json()) as { id: string }).id;
    const people: string[] = [];
    for (const person of [`${name} Lúa`, `${name} Brais`]) {
      const saved = await request.post(DEPENDENTS, {
        headers: neighbour,
        data: { name: person, birthYear: 2015, inMyCare: true },
      });
      expect(saved.ok(), 'persoa a cargo').toBe(true);
      people.push(((await saved.json()) as { id: string }).id);
    }
    const [lua, brais] = people;

    try {
      await page.goto('/naron');
      await loginAs(page, 'vecino-naron', lang);
      await page.goto('/naron');
      const section = page.locator('[data-home-events]');
      await expect(section.getByRole('heading', { name: text(lang, 'event.public.upcoming') })).toBeVisible();
      const card = section.locator(`[data-event-card="${id}"]`);
      await expect(card.locator('[data-card-period]')).toBeVisible();
      await expect(card.locator('[data-seats]')).toHaveText(text(lang, 'ui.seats.lastOne'));
      await card.locator('a').click();

      await expect(page).toHaveURL(new RegExp(`/naron/eventos/${id}$`));
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(name);
      await expect(page.locator('[data-event-age]')).toHaveText(
        text(lang, 'event.public.age.between', { from: 2012, to: 2018 }),
      );
      await expect(page.locator('[data-event-price]')).toContainText('25,00');
      // «Eu» e cada persoa ao cargo de vecino-naron (as da demo e as dúas desta proba).
      const dependents = (await (await request.get(DEPENDENTS, { headers: neighbour })).json()) as unknown[];
      const who = page.locator('[data-event-who]');
      await expect(who.getByRole('option')).toHaveCount(dependents.length + 1);
      await expectNoA11yViolations(page);
      await expectNoHorizontalScroll(page);

      // Apuntar a Lúa: a única praza.
      await who.locator(`[data-person="${lua}"]`).click();
      await expect(who.locator(`[data-person="${lua}"]`)).toHaveAttribute('aria-selected', 'true');
      await page.locator('[data-event-join]').click();
      const joined = page.locator(`[data-band="joined"][data-dependent="${lua}"]`);
      await expect(joined).toContainText(text(lang, 'event.band.joined'));
      await expect(joined.locator('[data-event-leave]')).toHaveText(text(lang, 'event.band.leaveOther'));

      // Completa: a banda «Non quedan prazas» e Brais entra na lista de espera.
      const full = page.locator('[data-band="full"]');
      await expect(full).toContainText(text(lang, 'event.band.full'));
      await expect(page.locator('[data-event-seats] [data-seats="full"]')).toHaveText(text(lang, 'ui.seats.full'));
      await full.locator(`[data-person="${brais}"]`).click();
      await full.locator('[data-event-wait]').click();
      const waiting = page.locator(`[data-band="waiting"][data-dependent="${brais}"]`);
      await expect(waiting).toContainText(text(lang, 'event.band.waitingAt', { position: '1.ª' }));
      await expectNoA11yViolations(page);

      // As miñas reservas: a da praza e a da lista.
      await page.goto('/naron/reservas');
      const mine = page.locator(`[data-my-event="${id}"]`);
      await expect(mine).toHaveCount(2);
      await expect(page.locator(`[data-my-event="${id}"][data-dependent="${brais}"] [data-chip="WAITING"]`)).toHaveText(
        text(lang, 'event.mine.waitingAt', { position: '1.ª' }),
      );
      await expect(
        page.locator(`[data-my-event="${id}"][data-dependent="${lua}"] [data-chip="CONFIRMED"]`),
      ).toBeVisible();
      await expectNoA11yViolations(page);
      await expectNoHorizontalScroll(page);
    } finally {
      const list = (await (await request.get(`${BASE}/manage`, { headers: manager })).json()) as {
        id: string;
        version: number;
      }[];
      const event = list.find((e) => e.id === id);
      if (event) {
        await request.post(`${BASE}/manage/${id}/cancel`, {
          headers: { ...manager, 'If-Match': `"${event.version}"` },
        });
      }
      for (const person of people) await request.delete(`${DEPENDENTS}/${person}`, { headers: neighbour });
    }
  });
});
