// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { randomBytes } from 'node:crypto';
import type { APIRequestContext, Browser, BrowserContext, Locator, Page, TestInfo } from '@playwright/test';
import { DEMO_PASSWORD, demoEmail, expect, text } from './fixtures';
import { hermetic } from './instalacions-axudas';

/**
 * Axudas dos e2e do persoal e do rexistro de actividade (D4-17): a API do persoal co cliente
 * `furar-e2e` (só no realm de demo), o Mailpit do compose (correos lidos e borrados), unha segunda
 * persoa no mesmo navegador (outro contexto, coa mesma garda de rede e de CSP ca `page`) e o
 * rexistro de Keycloak.
 */

export const SLUG = 'naron';
export const STAFF_API = `/api/v1/municipalities/${SLUG}/staff`;
const MAILPIT = process.env['FURAR_E2E_MAILPIT_URL'] ?? 'http://localhost:8025';

/** Persoal da demo (`R__demo_staff.sql`). */
export const DEMO_EXPIRED = 'persoal-antigo@example.org';

/** Correo novo e único para unha proba (dominio reservado `example.org`). */
export function freshEmail(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}${randomBytes(3).toString('hex')}@example.org`;
}

// --- API ------------------------------------------------------------------------------------

export interface StaffMember {
  id: string;
  email?: string | null;
  name?: string | null;
  role: 'MANAGER' | 'STAFF';
  status: 'INVITED' | 'ACTIVE' | 'DEACTIVATED';
  invitedBy: { kind: 'MEMBER' | 'PLATFORM'; name?: string | null };
  version: number;
}

/** Token dunha conta polo cliente furar-e2e (usuario ou correo). */
export async function bearer(
  request: APIRequestContext,
  username: string,
  password = DEMO_PASSWORD,
): Promise<Record<string, string>> {
  const response = await request.post('/auth/realms/furar/protocol/openid-connect/token', {
    form: {
      grant_type: 'password',
      client_id: 'furar-e2e',
      username,
      password,
    },
  });
  expect(response.ok(), `token de ${username}: ${await response.text()}`).toBe(true);
  const { access_token: accessToken } = (await response.json()) as { access_token: string };
  return { Authorization: `Bearer ${accessToken}` };
}

export async function staffList(request: APIRequestContext, headers: Record<string, string>): Promise<StaffMember[]> {
  const response = await request.get(STAFF_API, { headers });
  expect(response.ok(), 'listado do persoal').toBe(true);
  return (await response.json()) as StaffMember[];
}

/** Invita pola API (o xestor): devolve a pertenza creada. */
export async function inviteByApi(
  request: APIRequestContext,
  headers: Record<string, string>,
  email: string,
  language: 'gl' | 'es',
  role: 'STAFF' | 'MANAGER' = 'STAFF',
): Promise<StaffMember> {
  const response = await request.post(STAFF_API, { headers, data: { email, role, language } });
  expect(response.status(), `invitar ${email}: ${await response.text()}`).toBe(201);
  return (await response.json()) as StaffMember;
}

/** Desactiva pola API (limpeza: o que deixase unha proba). */
export async function deactivateByApi(
  request: APIRequestContext,
  headers: Record<string, string>,
  member: StaffMember,
): Promise<void> {
  const response = await request.post(`${STAFF_API}/${member.id}/deactivate`, {
    headers: { ...headers, 'If-Match': `"${member.version}"` },
  });
  expect([200, 204], `desactivar ${member.email}: ${await response.text()}`).toContain(response.status());
}

/** Reactiva pola API (limpeza: devolve a demo como estaba). */
export async function reactivateByApi(
  request: APIRequestContext,
  headers: Record<string, string>,
  member: StaffMember,
): Promise<void> {
  const response = await request.post(`${STAFF_API}/${member.id}/reactivate`, {
    headers: { ...headers, 'If-Match': `"${member.version}"` },
  });
  expect(response.status(), `reactivar ${member.email}: ${await response.text()}`).toBe(200);
}

// --- Mailpit --------------------------------------------------------------------------------

interface MailSummary {
  ID: string;
  Subject: string;
  To: { Address: string }[];
}

export interface Mail {
  ID: string;
  Subject: string;
  Text: string;
  HTML: string;
}

/** Espera o correo a `address` cuxo asunto casa con `subject`; devólveo enteiro e bórrao de Mailpit. */
export async function takeMail(request: APIRequestContext, address: string, subject: RegExp): Promise<Mail> {
  let found: MailSummary | undefined;
  await expect
    .poll(
      async () => {
        const response = await request.get(`${MAILPIT}/api/v1/search`, { params: { query: `to:"${address}"` } });
        if (!response.ok()) return false;
        const { messages } = (await response.json()) as { messages: MailSummary[] };
        found = messages.find((m) => subject.test(m.Subject));
        return Boolean(found);
      },
      { message: `correo «${subject}» a ${address} en Mailpit`, timeout: 30_000 },
    )
    .toBe(true);
  const detail = await request.get(`${MAILPIT}/api/v1/message/${found!.ID}`);
  expect(detail.ok(), 'correo en Mailpit').toBe(true);
  const mail = (await detail.json()) as Mail;
  await deleteMails(request, [found!.ID]);
  return mail;
}

/** Borra correos de Mailpit (os lidos polas probas: non quedan datos nin ligazóns). */
export async function deleteMails(request: APIRequestContext, ids: string[]): Promise<void> {
  if (!ids.length) return;
  const response = await request.delete(`${MAILPIT}/api/v1/messages`, { data: { IDs: ids } });
  expect(response.ok(), 'borrar en Mailpit').toBe(true);
}

/** Cantos correos hai en Mailpit para un enderezo. */
export async function mailCount(request: APIRequestContext, address: string): Promise<number> {
  const response = await request.get(`${MAILPIT}/api/v1/search`, { params: { query: `to:"${address}"` } });
  expect(response.ok(), 'buscar en Mailpit').toBe(true);
  return ((await response.json()) as { messages: MailSummary[] }).messages.length;
}

/** Borra todos os correos a un enderezo (os que non se chegaron a ler, ao rematar). */
export async function deleteMailsTo(request: APIRequestContext, address: string): Promise<void> {
  const response = await request.get(`${MAILPIT}/api/v1/search`, { params: { query: `to:"${address}"` } });
  if (!response.ok()) return;
  const { messages } = (await response.json()) as { messages: MailSummary[] };
  await deleteMails(
    request,
    messages.map((m) => m.ID),
  );
}

/** A ligazón da invitación do correo: `<orixe>/naron/convite#<token>` (o token só no fragmento). */
export function invitationLink(mail: Mail): URL {
  const match = /https?:\/\/[^\s"<>]+\/naron\/convite#[A-Za-z0-9_-]+/.exec(mail.Text);
  expect(match, 'ligazón da invitación no correo').not.toBeNull();
  const url = new URL(match![0]);
  expect(url.search, 'o token non vai na query').toBe('');
  expect(url.hash.length, 'token no fragmento').toBeGreaterThan(20);
  expect(mail.HTML).toContain(url.hash.slice(1));
  return url;
}

// --- Outra persoa no mesmo navegador --------------------------------------------------------

export interface Person {
  context: BrowserContext;
  page: Page;
  /** Peticións fóra de localhost (debe quedar baleira). */
  outside: string[];
  /** Violacións de CSP da web (debe quedar baleira). */
  csp: string[];
}

/**
 * Un contexto novo coas opcións do proxecto (idioma, tamaño, táctil) e as mesmas gardas ca
 * `page`: rede hermética e `securitypolicyviolation`. `theme` (p. ex. `darkTheme`) aplícase antes
 * de abrir nada.
 */
export async function otherPerson(
  browser: Browser,
  info: TestInfo,
  theme?: (page: Page) => Promise<void>,
): Promise<Person> {
  const use = info.project.use;
  const context = await browser.newContext({
    baseURL: use.baseURL,
    locale: use.locale,
    viewport: use.viewport,
    hasTouch: use.hasTouch,
    userAgent: use.userAgent,
    deviceScaleFactor: use.deviceScaleFactor,
    serviceWorkers: 'block',
  });
  const page = await context.newPage();
  const csp: string[] = [];
  await page.exposeFunction('__furarCsp', (detail: string) => csp.push(detail));
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (event) => {
      if (location.pathname.startsWith('/auth/')) return;
      const report = (window as unknown as { __furarCsp: (d: string) => void }).__furarCsp;
      report(`${event.violatedDirective} ${event.blockedURI} ${location.pathname}`);
    });
  });
  const outside = await hermetic(page);
  if (theme) await theme(page);
  return { context, page, outside, csp };
}

// --- Pantallas ------------------------------------------------------------------------------

/** A fila (táboa) ou tarxeta (móbil) da persoa co correo `email` na lista do persoal. */
export function memberRow(page: Page, email: string): Locator {
  return page.locator('[data-member]').filter({ has: page.locator('[data-email]', { hasText: email }) });
}

/** Abre o menú de accións da fila e escolle `action`. */
export async function rowAction(
  page: Page,
  row: Locator,
  action: 'resend' | 'role' | 'deactivate' | 'reactivate',
): Promise<void> {
  await row.locator('[data-actions]').click();
  const item = page.locator(`[role="menu"] [data-action="${action}"]`);
  await expect(item).toBeVisible();
  await item.click();
}

/** Entra no formulario de Keycloak co correo e o contrasinal (sen premer «Entrar» da web). */
export async function keycloakLogin(page: Page, username: string, password = DEMO_PASSWORD): Promise<void> {
  await page.waitForURL(/\/auth\/realms\/furar\//);
  await page.locator('#username').fill(demoEmail(username));
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
}

/** Espera a volta de Keycloak á web (sen `code=` no URL). */
export async function backFromKeycloak(page: Page): Promise<void> {
  await page.waitForURL((url) => !url.pathname.startsWith('/auth/') && !url.search.includes('code='));
}

/** «Podes reenviar en m:ss» con calquera tempo (depende do reloxo), ata os 2 min. */
export function resendWaitText(lang: 'gl' | 'es'): RegExp {
  const sample = text(lang, 'staff.members.resendWait.in', { time: '9:99' });
  const [before, after] = sample.split('9:99');
  return new RegExp(`^${escapeRe(before!)}[0-2]:[0-5]\\d${escapeRe(after!)}$`);
}

function escapeRe(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}
