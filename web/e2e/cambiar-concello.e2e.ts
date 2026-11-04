// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, expectNoA11yViolations, loginAs, test, text, userMenu } from './fixtures';
import { checkStep, hermetic } from './instalacions-axudas';
import {
  bearer,
  deactivateByApi,
  deleteMailsTo,
  invitationLink,
  inviteByApi,
  staffList,
  takeMail,
} from './persoal-axudas';

/**
 * Cambiar de concello desde o menú da persoa (maqueta 41; ADR-0027, revisión do 2026-11-03).
 * xestor-ferrol pasa a ser tamén persoal do mostrador de Narón (invitación aceptada pola API); ao
 * final, a pertenza desactívase. Sen páxinas novas: o menú ten dous niveis no mesmo panel.
 */
test.describe('cambiar de concello', () => {
  test('«Concello ›» no menú da persoa, co teclado: lista no mesmo panel e cambia á xestión do outro', async ({
    page,
    request,
    lang,
  }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    const manager = await bearer(request, 'xestor-naron');
    const email = 'xestor-ferrol@example.org';
    for (const left of (await staffList(request, manager)).filter(
      (m) => m.email === email && m.status !== 'DEACTIVATED',
    )) {
      await deactivateByApi(request, manager, left);
    }
    const member = await inviteByApi(request, manager, email, lang);
    try {
      const link = invitationLink(await takeMail(request, email, /Narón/));
      const accept = await request.post('/api/v1/municipalities/naron/staff-invitations/accept', {
        headers: await bearer(request, 'xestor-ferrol'),
        data: { token: link.hash.slice(1) },
      });
      expect(accept.status(), await accept.text()).toBe(200);

      // Tamén na web pública: o menú da persoa é o mesmo en todas as páxinas. Desde o listado,
      // sen as animacións da portada que checkStep agardaría.
      await page.goto('/ferrol/instalacions');
      await loginAs(page, 'xestor-ferrol', lang);

      await userMenu(page).click();
      const row = page.getByRole('menuitem', { name: `${text(lang, 'session.switchMunicipality')} Ferrol` });
      await expect(row).toBeVisible();
      await checkStep(page);
      await row.focus();
      await page.keyboard.press('ArrowRight');
      const back = page.getByRole('menuitem', { name: text(lang, 'session.switchMunicipality'), exact: true });
      await expect(back).toBeFocused();
      const ferrol = page.getByRole('menuitemradio', { name: /Ferrol/ });
      const naron = page.getByRole('menuitemradio', { name: /Narón/ });
      await expect(ferrol).toHaveAttribute('aria-checked', 'true');
      await expect(naron).toHaveAttribute('aria-checked', 'false');
      await expect(naron).toContainText(text(lang, 'session.role.STAFF'));
      await expectNoA11yViolations(page);
      await page.keyboard.press('ArrowLeft');
      await expect(row).toBeFocused();
      await page.keyboard.press('Enter');
      await expect(back).toBeFocused();
      await page.keyboard.press('ArrowDown');
      await page.keyboard.press('ArrowDown');
      await expect(naron).toBeFocused();
      await page.keyboard.press('Enter');
      await expect(page).toHaveURL(/\/naron\/xestion\/reservas$/);
      await expect(page.getByRole('menu')).toHaveCount(0);

      // Esc pecha o menú en calquera nivel.
      await userMenu(page).click();
      await page.getByRole('menuitem', { name: `${text(lang, 'session.switchMunicipality')} Narón` }).click();
      await page.keyboard.press('Escape');
      await expect(page.getByRole('menu')).toHaveCount(0);
      await expect(userMenu(page)).toBeFocused();
      await checkStep(page);
      expect(outside).toEqual([]);
    } finally {
      const left = (await staffList(request, manager)).find((m) => m.id === member.id && m.status !== 'DEACTIVATED');
      if (left) await deactivateByApi(request, manager, left);
      await deleteMailsTo(request, email);
    }
  });
});
