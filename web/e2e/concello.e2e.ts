// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  expect,
  expectNoA11yViolations,
  expectNoHorizontalScroll,
  keepPaymentNotes,
  loginAs,
  openSectionTab,
  test,
  text,
} from './fixtures';

/**
 * Sección «Concello» (maqueta 49): Aparencia (cores en mosaico, frases de contraste sen números,
 * «Axustar» e a vista previa ao lado, que medra desde o botón) e Servizo («Pago e devolución»
 * igual para todas as instalacións ou distinto por instalación: a ficha leva entón a súa sección e
 * a web pública usa o texto da instalación ou o de Furar, nunca o do concello). En Xeral, «Para
 * abrir as reservas» (maqueta 48): avisa do que falta e marcha co todo feito. Axe en cada paso.
 */
test.describe.configure({ mode: 'serial' });

test.describe('sección Concello', () => {
  test('Aparencia: «Axustar» arranxa o contraste e a vista previa vai ao lado; saír pregunta', async ({
    page,
    lang,
  }, testInfo) => {
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await openSectionTab(page, lang, 'appearance');
    await expect(page.locator('furar-color-tile')).toHaveCount(5);
    const checks = page.locator('.f-theme__checks');
    await expect(checks).not.toContainText(':1');
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    // Un realce claro non se le: a frase di que facer e «Axustar» arranxao.
    await page.locator('input[data-color="accent"]').evaluate((input: HTMLInputElement) => {
      input.value = '#c8d6e5';
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    const accent = page.locator('[data-check="accentOnBackground"]');
    await expect(accent).toHaveAttribute('data-ok', 'false');
    await accent.locator('[data-fix]').click();
    await expect(accent).toHaveAttribute('data-ok', 'true');
    await expect(page.locator('input[data-color="accent"]')).toBeFocused();
    await page.locator('details.f-theme__tech summary').click();
    await expect(page.locator('details.f-theme__tech')).toContainText(':1');

    // Vista previa: ao lado no escritorio, debaixo no móbil; segue as cores; Esc pecha e volve ao botón.
    const toggle = page.locator('[data-preview-toggle]');
    await toggle.click();
    const preview = page.locator('[data-preview]');
    await expect(preview).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await page.waitForFunction(() => document.getAnimations().every((a) => a.playState !== 'running'));
    const colours = await page.locator('.f-appearance__colours').boundingBox();
    const panel = await preview.boundingBox();
    if (testInfo.project.name.endsWith('mobile')) {
      expect(panel!.y).toBeGreaterThan(colours!.y);
    } else {
      expect(panel!.x).toBeGreaterThan(colours!.x + colours!.width - 1);
    }
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
    await page.keyboard.press('Escape');
    await expect(preview).toHaveCount(0);
    await expect(toggle).toBeFocused();

    // Hai un cambio sen gardar: saír pregunta e «Descartar» deixa o tema como estaba.
    await page.locator('a[data-section-tab="service"]').click();
    const leave = page.getByRole('alertdialog');
    await expect(leave).toContainText(
      text(lang, 'ui.unsavedChoice.title', { tab: text(lang, 'section.tab.appearance') }),
    );
    await leave.locator('[data-choice="discard"]').click();
    await expect(page).toHaveURL(/\/concello\/servizo$/);
  });

  test('Servizo: «Distinto por instalación» leva o pago á ficha de cada unha', async ({ page, lang, request }) => {
    const restore = await keepPaymentNotes(request);
    try {
      await page.goto('/naron');
      await loginAs(page, 'xestor-naron', lang);
      await openSectionTab(page, lang, 'service');
      const scope = (option: string) => page.locator(`[data-scope="${option}"]`);
      await expect(scope('ALL')).toHaveAttribute('aria-checked', 'true');
      await expect(page.locator('#settings-note-payment')).toBeVisible();
      await scope('FACILITY').click();
      await expect(page.locator('[data-payment-by-facility]')).toHaveText(text(lang, 'settings.payment.byFacility'));
      await expect(page.locator('#settings-note-payment')).toHaveCount(0);
      await expectNoA11yViolations(page);
      await page.getByRole('button', { name: text(lang, 'common.save'), exact: true }).click();
      await expect(page.getByText(text(lang, 'settings.saved'))).toBeVisible();

      // A ficha dunha instalación leva agora «Pago e devolución», co texto por defecto.
      const list = (await (await request.get('/api/v1/municipalities/naron/public/facilities')).json()) as {
        id: string;
      }[];
      await page.goto(`/naron/xestion/instalacions/${list[0]!.id}`);
      const section = page.locator('#facility-payment-notes-section');
      await expect(
        section.getByRole('heading', { level: 2, name: text(lang, 'facility.paymentNotes.title') }),
      ).toBeVisible();
      await expect(section.locator('[data-value="payment"]')).toContainText(
        text(lang, 'facility.paymentNotes.payment'),
      );
      await expectNoA11yViolations(page);
      await expectNoHorizontalScroll(page);
    } finally {
      await restore();
    }
  });

  test('Xeral: «Para abrir as reservas» avisa do que falta e marcha co todo feito', async ({ page, lang }) => {
    const save = page.getByRole('button', { name: text(lang, 'common.save'), exact: true });
    const card = page.getByRole('region', { name: text(lang, 'section.opening.title') });
    const facilities = () => page.waitForResponse((r) => /\/municipalities\/naron\/facilities\?page=0/.test(r.url()));

    // Narón ten instalacións publicadas; sen NIF, faltan datos que usan os textos legais.
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await openSectionTab(page, lang, 'legal');
    await page.locator('#settings-legal-nif').fill('');
    await save.click();
    await expect(page.getByText(text(lang, 'settings.saved'))).toBeVisible();
    const loaded = facilities();
    await page.locator('a[data-section-tab="general"]').click();
    await loaded;
    await expect(card).toBeVisible();
    await expect(card.locator('[data-count]')).toHaveText(text(lang, 'ui.checklist.count', { done: 1, total: 2 }));
    await expect(card.locator('[data-item="facility"]')).toHaveAttribute('data-done', 'true');
    await expect(card.locator('[data-item="legal"]')).toHaveAttribute('data-done', 'false');
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    // «Completar» leva a «Legal»; cos cinco datos gardados, a card xa non sae.
    await card.getByRole('link', { name: text(lang, 'section.opening.complete') }).click();
    await expect(page).toHaveURL(/\/concello\/legal\/privacidade$/);
    const filled: Record<string, string> = {
      legalEntityName: 'Concello de Narón (demo)',
      nif: 'P1505400J',
      address: 'Praza de Galicia, 1, 15570 Narón',
      generalEmail: 'concello@naron.example',
      dpoEmail: 'dpd@naron.example',
    };
    for (const [key, value] of Object.entries(filled)) {
      const input = page.locator(`#settings-legal-${key}`);
      if (!(await input.inputValue()).trim()) await input.fill(value);
    }
    // O aviso «Gardado» do primeiro garda aínda pode estar á vista: agárdase a resposta deste.
    const stored = page.waitForResponse(
      (r) => r.request().method() === 'PUT' && /\/municipalities\/naron$/.test(r.url()) && r.ok(),
    );
    await save.click();
    await stored;
    await expect(page.getByText(text(lang, 'settings.saved')).last()).toBeVisible();
    const again = facilities();
    await page.locator('a[data-section-tab="general"]').click();
    await again;
    await expect(page.locator('#settings-name')).toBeVisible();
    await expect(card).toHaveCount(0);
  });

  test('Instalacións con `?nova=1` (a ligazón «Crear»): abre o asistente «Nova instalación»', async ({
    page,
    lang,
  }) => {
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto('/naron/xestion/instalacions?nova=1');
    await expect(page).toHaveURL(/\/naron\/xestion\/instalacions\/nova$/);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'facility.wizard.title') })).toBeVisible();
    // Sen nada escrito, volver ao listado non pregunta.
    await page.locator('[data-wizard-back]').click();
    await expect(page).toHaveURL(/\/naron\/xestion\/instalacions$/);
  });
});
