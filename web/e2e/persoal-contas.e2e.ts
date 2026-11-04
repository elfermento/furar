// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, loginAs, openSectionTab, test, text } from './fixtures';
import { checkStep, hermetic } from './instalacions-axudas';
import {
  bearer,
  deactivateByApi,
  deleteMailsTo,
  inviteByApi,
  invitationLink,
  memberRow,
  rowAction,
  staffList,
  takeMail,
} from './persoal-axudas';
import { PADEL, bookingDay, freeSlots } from './reservas-axudas';

/**
 * Contas do persoal (ADR-0027, revisión do 2026-10-30): a última xestora dun concello non se
 * desactiva nin se baixa a recepción, nin sequera a si mesma (Ferrol, que só ten unha); a conta de
 * traballo non reserva como veciña (o aviso antes e o 403 da API) e unha conta con reservas non
 * acepta unha invitación ao persoal (pola API). Axe en cada paso e sen peticións fóra de localhost.
 */
test.describe('contas do persoal', () => {
  test('a última xestora non se desactiva nin se baixa a recepción, nin a si mesma (Ferrol)', async ({
    page,
    lang,
  }) => {
    const outside = await hermetic(page);
    await page.goto('/ferrol');
    await loginAs(page, 'xestor-ferrol', lang);
    await openSectionTab(page, lang, 'staff');
    const own = memberRow(page, 'xestor-ferrol@example.org');
    await expect(own.locator('[data-chip]')).toHaveAttribute('data-chip', 'ACTIVE');

    await rowAction(page, own, 'deactivate');
    const confirm = page.getByRole('alertdialog', { name: text(lang, 'staff.members.deactivate.title') });
    await confirm.locator('[data-confirm-phrase]').fill(text(lang, 'staff.members.deactivate.phrase'));
    await confirm.getByRole('checkbox').check();
    await confirm.locator('[data-confirm-accept]').click();
    await expect(confirm).toBeHidden();
    await expect(page.locator('[data-alert]')).toContainText(text(lang, 'errors.staff.last-manager'));
    await checkStep(page);

    await rowAction(page, own, 'role');
    const role = page.getByRole('dialog', { name: text(lang, 'staff.members.changeRole.title') });
    await role.getByRole('radio', { name: text(lang, 'staff.members.role.STAFF') }).check();
    await role.locator('[data-confirm-phrase]').fill(text(lang, 'staff.members.changeRole.phrase'));
    await role.locator('[data-save]').click();
    await expect(role).toBeHidden();
    await expect(page.locator('[data-alert]')).toContainText(text(lang, 'errors.staff.last-manager'));
    await expect(own.locator('[data-chip]')).toHaveAttribute('data-chip', 'ACTIVE');
    await expect(own.locator('[data-cell="role"]').first()).toHaveText(text(lang, 'staff.members.role.MANAGER'));
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('a conta de traballo non reserva como veciña: aviso antes e 403 na API', async ({
    page,
    lang,
    request,
  }, info) => {
    const outside = await hermetic(page);
    const day = bookingDay(45, info.project.name);
    await page.goto('/naron');
    await loginAs(page, 'recepcion-naron', lang);
    await page.goto(`/naron/instalacions/${PADEL.id}/reservar?uso=${PADEL.use}&data=${day}`);
    const notice = page.locator('[data-reserve-work-account]');
    await expect(notice).toContainText(text(lang, 'booking.reserve.workAccount'));
    const go = page.locator('[data-reserve-go]');
    await expect(go).toBeDisabled();
    await expect(go).toHaveAttribute('aria-describedby', 'reserve-work-account');
    await checkStep(page);

    // A API é a rede: tamén responde que é unha conta de traballo.
    // Un oco libre de verdade, para que o único motivo do 403 sexa a conta.
    const [free] = await freeSlots(request, PADEL.id, PADEL.use, day);
    expect(free, `un oco libre o ${day}`).toBeDefined();
    const start = free!.start;
    const response = await request.post('/api/v1/municipalities/naron/me/bookings', {
      headers: { ...(await bearer(request, 'recepcion-naron')), 'Idempotency-Key': crypto.randomUUID() },
      data: { facilityId: PADEL.id, useId: PADEL.use, start, slots: 1 },
    });
    expect(response.status()).toBe(403);
    expect(((await response.json()) as { code: string }).code).toBe('booking.staff-account');
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('unha conta con reservas non acepta unha invitación ao persoal (API)', async ({ request, lang }) => {
    const manager = await bearer(request, 'xestor-naron');
    const email = 'vecino-naron@example.org';
    // Se quedou dunha execución anterior, fóra.
    for (const left of (await staffList(request, manager)).filter(
      (m) => m.email === email && m.status !== 'DEACTIVATED',
    )) {
      await deactivateByApi(request, manager, left);
    }
    const member = await inviteByApi(request, manager, email, lang);
    try {
      const link = invitationLink(await takeMail(request, email, /Narón/));
      const accept = await request.post('/api/v1/municipalities/naron/staff-invitations/accept', {
        headers: await bearer(request, 'vecino-naron'),
        data: { token: link.hash.slice(1) },
      });
      expect(accept.status()).toBe(403);
      const problem = (await accept.json()) as { code: string; detail: string };
      expect(problem.code).toBe('staff.invitation-personal-account');
      expect((await staffList(request, manager)).find((m) => m.id === member.id)?.status).toBe('INVITED');
    } finally {
      const left = (await staffList(request, manager)).find((m) => m.id === member.id && m.status !== 'DEACTIVATED');
      if (left) await deactivateByApi(request, manager, left);
      await deleteMailsTo(request, email);
    }
  });
});
