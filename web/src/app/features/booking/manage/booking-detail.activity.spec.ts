// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expectNoAxeViolations } from '../../../../testing/axe';
import { expectRequest, fakeSession, flushProblem } from '../../../../testing/api';
import {
  BASE,
  OVERVIEW,
  SUMMARY_URL,
  detail,
  openDetail,
  renderManage,
  text,
  useMonday,
} from '../../../../testing/manage-bookings';

/** Unha sesión dunha actividade con horario (ADR-0041), tal como a ve o persoal. */
const SESSION = detail({
  holderName: 'Natación · Iniciación',
  holderEmail: undefined,
  holderAccount: false,
  price: { kind: 'NONE' },
  paymentStatus: 'NONE',
  paymentTargets: [],
  activity: { eventId: 'a1', sessionId: 's1' },
});

describe('Ficha dunha sesión dunha actividade (ADR-0041)', () => {
  useMonday();

  it('a actividade e as súas accións: «Pasar lista», «Ver a actividade» e «Cancelar a sesión»', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle, SESSION);
    expect(text(card.querySelector('[data-value="activity"] dd'))).toBe('Natación · Iniciación');
    expect(card.querySelector('[data-value="person"]')).toBeNull();
    expect(card.querySelector('[data-detail-cancel]')).toBeNull();
    expect(card.querySelector('[data-detail-change]')).toBeNull();
    expect(card.querySelector('[data-detail-attend]')?.getAttribute('href')).toBe(
      '/naron/xestion/eventos/a1/sesions/s1',
    );
    expect(card.querySelector('[data-detail-activity-open]')?.getAttribute('href')).toBe(
      '/naron/xestion/eventos?ver=a1',
    );
    await expectNoAxeViolations(card);

    card.querySelector<HTMLButtonElement>('[data-detail-session-cancel]')!.click();
    await vi.waitFor(() => expect(document.querySelector('[data-confirm-accept]')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    flushProblem(
      await expectRequest(`${BASE}/events/manage/a1/sessions/s1/cancel`),
      409,
      'event.session-has-attendance',
    );
    await vi.waitFor(() => expect(card.querySelector('[data-detail-error]')).not.toBeNull());

    card.querySelector<HTMLButtonElement>('[data-detail-session-cancel]')!.click();
    await vi.waitFor(() => expect(document.querySelector('[data-confirm-accept]')).not.toBeNull());
    document.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    (await expectRequest(`${BASE}/events/manage/a1/sessions/s1/cancel`)).flush({});
    (await expectRequest(`${BASE}/bookings/${SESSION.id}`)).flush({
      ...SESSION,
      status: 'CANCELLED_BY_MUNICIPALITY',
      cancellable: false,
    });
    (await expectRequest((r) => r.url === SUMMARY_URL)).flush(OVERVIEW);
    await vi.waitFor(async () => {
      await settle();
      expect(card.querySelector('[data-detail-session-cancel]')).toBeNull();
    });
  });

  it('Recepción pasa lista pero non cancela a sesión', async () => {
    const { el, settle } = await renderManage(fakeSession({ staff: 'naron' }));
    const card = await openDetail(el, settle, SESSION);
    expect(card.querySelector('[data-detail-attend]')).not.toBeNull();
    expect(card.querySelector('[data-detail-session-cancel]')).toBeNull();
  });
});
