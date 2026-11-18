// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, test, text } from './fixtures';

/** O Mailpit do compose, onde chega a solicitude (na demo, FURAR_JOIN_REQUEST_EMAIL de exemplo). */
const MAILPIT = process.env['FURAR_E2E_MAILPIT_URL'] ?? 'http://localhost:8025';

/**
 * «¿Es un concello?» (ADR-0042): a franxa ao final da portada común e a ligazón do pé levan á
 * solicitude (a ligazón do pé, só na portada común: revisión do 2026-11-17); a frase de privacidade
 * leva á súa páxina; o formulario marca o erro debaixo de cada campo e leva o foco ao primeiro;
 * enviada, na mesma páxina só queda a confirmación, e o correo chega a quen opera o servizo, no
 * idioma da web e co Reply-To de quen a pide. Precisa varios concellos (db/demo) e a función activa
 * (compose.yaml, demo).
 */
test.describe('«¿Es un concello?»: franxa, pé e solicitude de alta', () => {
  test('a franxa do final da portada común e a ligazón do pé levan á solicitude; pasa axe', async ({ page, lang }) => {
    await page.goto('/');
    const band = page.locator('[data-join-band]');
    await expect(band.getByRole('heading', { name: text(lang, 'joinRequest.band.title') })).toBeVisible();
    await expect(band).toContainText(text(lang, 'joinRequest.band.text'));
    const footerLink = page.locator('footer [data-footer-column="service"]').getByRole('link', {
      name: text(lang, 'footer.joinRequest'),
    });
    await expect(footerLink).toHaveAttribute('href', '/alta-concello');
    await expectNoA11yViolations(page);
    await band.getByRole('link', { name: text(lang, 'joinRequest.band.action') }).click();
    await expect(page).toHaveURL(/\/alta-concello$/);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'joinRequest.title') })).toBeVisible();
    await expect(page.locator('.f-page-header__intro')).toHaveText(text(lang, 'joinRequest.intro'));

    // A privacidade, na súa páxina do despregue.
    const privacy = page.locator('[data-join-privacy]');
    await expect(privacy).toContainText(text(lang, 'joinRequest.privacySentence'));
    await privacy.getByRole('link', { name: text(lang, 'joinRequest.privacyLink') }).click();
    await expect(page).toHaveURL(/\/alta-concello\/privacidade$/);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'privacy.title') })).toBeVisible();
    await expect(
      page.getByRole('heading', { level: 2, name: text(lang, 'joinRequest.privacyPageHeading') }),
    ).toBeVisible();
    await expect(page.locator('[data-legal-join-requests]')).toContainText(text(lang, 'joinRequest.privacyBasis'));
    await expect(page.locator('[data-legal-join-requests]')).toContainText(text(lang, 'joinRequest.privacyStorage'));
    await expectNoA11yViolations(page);
  });

  test('no pé da portada dun concello non hai «¿Es un concello?»', async ({ page, lang }) => {
    await page.goto('/naron');
    const service = page.locator('footer [data-footer-column="service"]');
    await expect(service).toBeVisible();
    await expect(service.getByRole('link', { name: text(lang, 'footer.joinRequest') })).toHaveCount(0);
    await expect(page.locator('footer [data-footer-join-request]')).toHaveCount(0);
  });

  test('erros debaixo de cada campo e foco ao primeiro; enviada, só a confirmación e o correo en Mailpit', async ({
    page,
    request,
    lang,
  }, info) => {
    await page.goto('/alta-concello');
    const form = page.locator('[data-join-form]');
    const field = (key: string) => form.getByLabel(text(lang, `joinRequest.${key}`), { exact: false });
    const send = form.getByRole('button', { name: text(lang, 'joinRequest.send') });

    // Baleiro: o erro debaixo de cada obrigatorio e o foco no primeiro.
    await send.click();
    await expect(page.locator('#join-request-municipality-error')).toHaveText(text(lang, 'errors.validation.required'));
    await expect(page.locator('#join-request-phone-error')).toHaveText(text(lang, 'errors.validation.required'));
    await expect(page.locator('#join-request-municipality')).toBeFocused();
    await expectNoA11yViolations(page);

    const municipality = `Concello E2E ${info.project.name} ${Date.now()}`;
    await page.locator('#join-request-municipality').fill(municipality);
    await page.locator('#join-request-name').fill('Ana Rey');
    await page.locator('#join-request-role').fill('Concelleira de deportes');
    await page.locator('#join-request-email').fill('ana@example');
    await page.locator('#join-request-phone').fill('981 000 000');
    await page.locator('#join-request-message').fill('Temos seis instalacións');
    await send.click();
    await expect(page.locator('#join-request-email-error')).toHaveText(text(lang, 'joinRequest.emailInvalid'));
    await expect(page.locator('#join-request-email')).toBeFocused();
    await expect(field('municipality')).toHaveValue(municipality);

    await page.locator('#join-request-email').fill('ana@example.org');
    await send.click();
    const done = page.locator('[data-join-sent]');
    await expect(done).toContainText(text(lang, 'joinRequest.sent.title'));
    await expect(done).toContainText(`${text(lang, 'joinRequest.sent.text')} ana@example.org.`);
    await expect(done).toBeFocused();
    await expect(form).toHaveCount(0);
    await expect(page.locator('.f-page-header__intro')).toHaveCount(0);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'joinRequest.title') })).toBeVisible();
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    // O correo, a quen opera o servizo, no idioma da web, co asunto, os campos e o Reply-To de quen a
    // pide.
    const es = lang === 'es';
    const subject = `${es ? 'Solicitud' : 'Solicitude'} de alta: ${municipality}`;
    let found: { Text: string; ReplyTo: { Address: string }[]; To: { Address: string }[] } | undefined;
    await expect
      .poll(
        async () => {
          const response = await request.get(`${MAILPIT}/api/v1/search`, {
            params: { query: `subject:"${subject}"` },
          });
          if (!response.ok()) return false;
          const { messages } = (await response.json()) as { messages: { ID: string; Subject: string }[] };
          const summary = messages.find((m) => m.Subject === subject);
          if (!summary) return false;
          const detail = await request.get(`${MAILPIT}/api/v1/message/${summary.ID}`);
          if (!detail.ok()) return false;
          found = (await detail.json()) as NonNullable<typeof found>;
          return true;
        },
        { message: `correo «${subject}» en Mailpit`, timeout: 30_000 },
      )
      .toBe(true);
    expect(found?.ReplyTo.map((r) => r.Address)).toEqual(['ana@example.org']);
    expect(found?.To.map((r) => r.Address)).toEqual(['altas@example.org']);
    expect(found?.Text).toContain(`${es ? 'Nombre y apellidos' : 'Nome e apelidos'}: Ana Rey`);
    expect(found?.Text).toContain('Teléfono: 981 000 000');
    expect(found?.Text).toContain(`${es ? 'Mensaje' : 'Mensaxe'}: Temos seis instalacións`);
  });
});
