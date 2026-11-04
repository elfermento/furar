// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import AxeBuilder from '@axe-core/playwright';
import { type APIRequestContext, type Page, test as base, expect } from '@playwright/test';
import es from '../src/i18n/es.json' with { type: 'json' };
import gl from '../src/i18n/gl.json' with { type: 'json' };

/**
 * Contrasinal (público, só demo) dos usuarios do realm de demo, lido do overlay, que é a fonte
 * (deploy/keycloak/demo/overlay.json → import-demo): así non se repite nos tests.
 */
export const DEMO_PASSWORD: string =
  process.env['FURAR_DEMO_PASSWORD'] ??
  (() => {
    const overlay = JSON.parse(
      readFileSync(fileURLToPath(new URL('../../deploy/keycloak/demo/overlay.json', import.meta.url)), 'utf8'),
    ) as { users: readonly { username: string; credentials?: readonly { type: string; value?: string }[] }[] };
    const password = overlay.users
      .find((u) => u.username === 'xestor-naron')
      ?.credentials?.find((c) => c.type === 'password')?.value;
    if (!password) throw new Error('overlay.json: xestor-naron sen contrasinal');
    return password;
  })();
/**
 * Usuarios de demo (táboa en deploy/keycloak/README.md). O rol de persoal sae da táboa de pertenzas
 * (db/demo/R__demo_staff.sql); `ex-recepcion-naron` está desactivado. Todos entran só co correo e o
 * contrasinal: non hai segundo factor (decisión do 2026-11-02).
 */
export type DemoUser =
  'admin-plataforma' | 'xestor-naron' | 'vecino-naron' | 'xestor-ferrol' | 'recepcion-naron' | 'ex-recepcion-naron';

type Texts = typeof gl;
const TEXTS: Record<'gl' | 'es', Texts> = { gl, es };

/** Texto da web nun idioma (as mesmas claves que src/i18n), con {{parámetros}}. */
export function text(lang: 'gl' | 'es', key: string, params: Record<string, string | number> = {}): string {
  const value = key
    .split('.')
    .reduce<unknown>((node, part) => (node as Record<string, unknown> | undefined)?.[part], TEXTS[lang]);
  if (typeof value !== 'string') throw new Error(`Sen texto para ${key}`);
  return value.replace(/\{\{\s*(\w+)\s*\}\}/g, (_, name: string) => String(params[name] ?? ''));
}

/**
 * Agarda ás animacións con fin no tempo (as infinitas e as ligadas ao scroll non rematan nunca):
 * cunha capa entrando, axe mide un contraste a medias e un clic polas coordenadas pode caer fóra.
 */
export async function settleAnimations(page: Page): Promise<void> {
  await page.waitForFunction(() =>
    document
      .getAnimations()
      .every(
        (a) =>
          a.playState !== 'running' ||
          !(a.timeline instanceof DocumentTimeline) ||
          a.effect?.getTiming().iterations === Infinity,
      ),
  );
}

/** Analiza a páxina con axe (WCAG 2.1 A e AA) e falla se hai violacións. */
export async function expectNoA11yViolations(page: Page, exclude: string[] = []): Promise<void> {
  await settleAnimations(page);
  let builder = new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']);
  for (const selector of exclude) builder = builder.exclude(selector);
  const { violations } = await builder.analyze();
  const summary = violations.map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
  expect(summary, `axe en ${page.url()}`).toEqual([]);
}

/** Sen scroll horizontal (reflow a 320 px, WCAG 1.4.10). */
export async function expectNoHorizontalScroll(page: Page): Promise<void> {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow, 'scroll horizontal').toBeLessThanOrEqual(0);
}

/**
 * Correo dun usuario de demo: o formulario de acceso pide o correo (P13, o correo é o usuario).
 * O nome de usuario de demo tamén entra, pero a veciñanza nova só ten o correo.
 */
export function demoEmail(user: string): string {
  return user.includes('@') ? user : `${user}@example.org`;
}

/** Inicia sesión polo formulario real de Keycloak (co correo) e volve á páxina de orixe. */
export async function loginAs(page: Page, user: DemoUser, lang: 'gl' | 'es'): Promise<void> {
  await page.getByRole('button', { name: text(lang, 'session.login') }).click();
  await page.waitForURL(/\/auth\/realms\/furar\//);
  await expect(page.locator('label[for="username"]')).toHaveText('Correo electrónico');
  await page.locator('#username').fill(demoEmail(user));
  await page.locator('#password').fill(DEMO_PASSWORD);
  await page.locator('#kc-login').click();
  await page.waitForURL((url) => !url.pathname.startsWith('/auth/') && !url.search.includes('code='));
  await expect(userMenu(page)).toBeVisible();
  void lang;
}

/** O botón do menú da persoa (pastilla co nome e as iniciais) na barra superior. */
export function userMenu(page: Page) {
  return page.locator('furar-site-bar furar-user-menu button.f-usermenu__trigger');
}

/**
 * Vai a unha páxina da xestión desde a barra (a de arriba no escritorio, a de abaixo no móbil).
 * Se a ligazón vai en «Máis» (a barra de abaixo con máis de catro destinos), ábreo antes.
 */
export async function openManageLink(page: Page, lang: 'gl' | 'es', key: string): Promise<void> {
  const nav = page.getByRole('navigation', { name: text(lang, 'app.manageNav') });
  const link = nav.getByRole('link', { name: text(lang, key), exact: true });
  const more = nav.locator('button[data-more]:visible');
  // A barra pinta os destinos cando chegan os roles: agárdase á ligazón ou a «Máis».
  await expect(link.or(more).first()).toBeVisible();
  if (!(await link.isVisible())) {
    await more.click();
  }
  await link.click();
}

/**
 * Abre unha pestana da sección «Concello» (maqueta 49): «Concello» na barra e despois a pestana
 * (`general`, `appearance`, `service`, `legal`, `staff`, `customFields`, `activity`).
 */
export async function openSectionTab(page: Page, lang: 'gl' | 'es', tab: string): Promise<void> {
  await openManageLink(page, lang, 'nav.municipality');
  const tabs = page.getByRole('navigation', { name: text(lang, 'section.title'), exact: true });
  await tabs.locator(`a[data-section-tab="${tab}"]`).click();
  await expect(tabs.locator(`a[data-section-tab="${tab}"]`)).toHaveAttribute('aria-current', 'page');
}

/** Pecha a sesión desde o menú da persoa. */
export async function logout(page: Page, lang: 'gl' | 'es'): Promise<void> {
  await userMenu(page).click();
  await page.getByRole('menuitem', { name: text(lang, 'session.logout') }).click();
}

interface Fixtures {
  lang: 'gl' | 'es';
  /** Rexistra violacións de CSP (securitypolicyviolation) e falla ao final da proba se houbo. */
  cspGuard: undefined;
}

export const test = base.extend<Fixtures>({
  lang: async ({}, use, testInfo) => {
    await use(testInfo.project.name.startsWith('es') ? 'es' : 'gl');
  },
  cspGuard: [
    async ({ page }, use) => {
      const violations: string[] = [];
      await page.exposeFunction('__furarCsp', (detail: string) => violations.push(detail));
      await page.addInitScript(() => {
        document.addEventListener('securitypolicyviolation', (event) => {
          // As páxinas de Keycloak (/auth) teñen a súa propia CSP: aquí vixíase a web.
          if (location.pathname.startsWith('/auth/')) return;
          const report = (window as unknown as { __furarCsp: (d: string) => void }).__furarCsp;
          report(`${event.violatedDirective} ${event.blockedURI} ${location.pathname}`);
        });
      });
      await use(undefined);
      expect(violations, 'violacións de CSP').toEqual([]);
    },
    { auto: true },
  ],
});

export { expect };

/** Token do xestor do concello polo cliente furar-e2e (só no realm de demo). */
export async function managerHeaders(request: APIRequestContext, slug: string): Promise<Record<string, string>> {
  const token = await request.post('/auth/realms/furar/protocol/openid-connect/token', {
    form: { grant_type: 'password', client_id: 'furar-e2e', username: `xestor-${slug}`, password: DEMO_PASSWORD },
  });
  expect(token.ok(), 'token de furar-e2e').toBe(true);
  const { access_token: accessToken } = (await token.json()) as { access_token: string };
  return { Authorization: `Bearer ${accessToken}` };
}

/**
 * Elimina as instalacións que crearon as probas (nome que empeza por `prefix`, un por proxecto
 * para non pisar os que corren á vez) e que quedasen na base da demo
 * (unha execución interrompida ou a propia proba ao rematar). Non teñen reservas: bórranse de
 * verdade, coas súas imaxes da lista; as fotos quedan na biblioteca.
 */
export async function deleteE2eFacilities(request: APIRequestContext, prefix: string, slug = 'naron'): Promise<void> {
  const headers = await managerHeaders(request, slug);
  const list = await request.get(`/api/v1/municipalities/${slug}/facilities?page=0&size=100`, { headers });
  expect(list.ok(), 'listado de instalacións').toBe(true);
  const { items } = (await list.json()) as { items: { id: string; name: string; version: number }[] };
  for (const facility of items.filter((f) => f.name.startsWith(prefix))) {
    const deleted = await request.delete(`/api/v1/municipalities/${slug}/facilities/${facility.id}`, {
      headers: { ...headers, 'If-Match': `"${facility.version}"` },
    });
    expect([204, 404], `eliminar ${facility.name}`).toContain(deleted.status());
  }
}

interface LeftoverField {
  id: string;
  code: string;
  status: string;
  version: number;
  hasValues?: boolean;
}

/**
 * Quita os campos que deixase unha execución anterior interrompida (código e2e_*), de calquera
 * sitio, para que as probas non dependan de restos: elimina os que non teñen valores e desactiva
 * os que os teñen. Usa o cliente furar-e2e (só no realm de demo).
 */
export async function removeLeftoverE2eFields(request: APIRequestContext, slug = 'naron'): Promise<void> {
  const token = await request.post('/auth/realms/furar/protocol/openid-connect/token', {
    form: { grant_type: 'password', client_id: 'furar-e2e', username: `xestor-${slug}`, password: DEMO_PASSWORD },
  });
  expect(token.ok(), 'token de furar-e2e').toBe(true);
  const { access_token: accessToken } = (await token.json()) as { access_token: string };
  const headers = { Authorization: `Bearer ${accessToken}` };
  const list = await request.get(`/api/v1/municipalities/${slug}/custom-fields?includeInactive=true`, { headers });
  const fields = (await list.json()) as LeftoverField[];
  for (const field of fields.filter((f) => f.code.startsWith('e2e_'))) {
    const url = `/api/v1/municipalities/${slug}/custom-fields/${field.id}`;
    const ifMatch = { ...headers, 'If-Match': `"${field.version}"` };
    if (!field.hasValues) {
      const deleted = await request.delete(url, { headers: ifMatch });
      expect(deleted.status(), `eliminar ${field.code}`).toBe(204);
    } else if (field.status === 'ACTIVE') {
      const deactivated = await request.post(`${url}/deactivate`, { headers: ifMatch });
      expect(deactivated.ok(), `desactivar ${field.code}`).toBe(true);
    }
  }
}

interface MunicipalityBody {
  name: string;
  languages: string[];
  defaultLanguage: string;
  theme: Record<string, string>;
  customFields: Record<string, unknown>;
  legal: Record<string, unknown>;
  version: number;
}

/**
 * Deixa as páxinas legais do concello coma na demo (datos-legais.e2e.ts): as tres na «Páxina de
 * Furar», sen enderezos propios e sen textos propios, para que o resto das probas (o pé, «Privacidade») non dependan
 * dunha execución anterior. O resto do concello vai igual no `PUT` (o xestor ve todos os campos).
 */
export async function resetLegalPages(request: APIRequestContext, slug = 'naron'): Promise<void> {
  const headers = await managerHeaders(request, slug);
  const url = `/api/v1/municipalities/${slug}`;
  const current = await request.get(url, { headers });
  expect(current.ok(), 'ler o concello').toBe(true);
  const m = (await current.json()) as MunicipalityBody;
  let version = m.version;
  const modes = ['privacyMode', 'accessibilityMode', 'legalNoticeMode'];
  const urls = ['privacyUrl', 'accessibilityUrl', 'legalNoticeUrl'];
  if (modes.some((mode) => m.legal[mode] !== 'FURAR') || urls.some((url) => m.legal[url])) {
    const legal = {
      ...m.legal,
      ...Object.fromEntries(modes.map((mode) => [mode, 'FURAR'])),
      ...Object.fromEntries(urls.map((url) => [url, null])),
    };
    const { name, languages, defaultLanguage, theme, customFields } = m;
    const put = await request.put(url, {
      headers: { ...headers, 'If-Match': `"${version}"` },
      data: { name, languages, defaultLanguage, theme, customFields, legal },
    });
    expect(put.ok(), `páxinas de Furar: ${await put.text()}`).toBe(true);
    version = ((await put.json()) as MunicipalityBody).version;
  }
  const texts = await request.put(`${url}/legal-texts`, {
    headers: { ...headers, 'If-Match': `"${version}"` },
    data: {},
  });
  expect(texts.ok(), `sen textos propios: ${await texts.text()}`).toBe(true);
}

interface NotesBody {
  payment: { gl: string; es: string };
  paymentDefault: boolean;
  refund: { gl: string; es: string };
  refundDefault: boolean;
  scope: 'ALL' | 'FACILITY';
}

/**
 * Garda o pago e devolución do concello (D2-11) e devolve como deixalo como estaba: o ámbito e os
 * textos propios (os de Furar, baleiros).
 */
export async function keepPaymentNotes(request: APIRequestContext, slug = 'naron'): Promise<() => Promise<void>> {
  const url = `/api/v1/municipalities/${slug}/payment-notes`;
  const before = (await (await request.get(url)).json()) as NotesBody;
  const own = (note: { gl: string; es: string }, isDefault: boolean) => (isDefault ? { gl: '', es: '' } : note);
  return async () => {
    const headers = await managerHeaders(request, slug);
    const current = await request.get(url);
    const version = /"(\d+)"/.exec(current.headers()['etag'] ?? '')?.[1];
    const put = await request.put(url, {
      headers: { ...headers, 'If-Match': `"${version}"` },
      data: {
        scope: before.scope,
        payment: own(before.payment, before.paymentDefault),
        refund: own(before.refund, before.refundDefault),
      },
    });
    expect(put.ok(), `pago e devolución como estaban: ${await put.text()}`).toBe(true);
  };
}
