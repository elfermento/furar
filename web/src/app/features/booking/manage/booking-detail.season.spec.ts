// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expectRequest, fakeSession } from '../../../../testing/api';
import { BASE, detail, openDetail, renderManage, text, useMonday } from '../../../../testing/manage-bookings';
import { series } from '../../../../testing/seasons';

/** Unha sesión dunha tempada, tal como a ve a xestión (coa tempada). */
const SESSION = detail({
  holderName: 'Club Baloncesto',
  holderEmail: undefined,
  holderAccount: false,
  price: { kind: 'NONE' },
  paymentStatus: 'NONE',
  paymentTargets: [],
  organization: {
    id: 'o1',
    name: 'Club Baloncesto',
    seriesId: 's1',
    series: {
      id: 's1',
      weekdays: [1, 3],
      startMinute: 1140,
      endMinute: 1230,
      firstDate: '2026-11-16',
      lastDate: '2027-06-18',
      sessions: 57,
      paymentStatus: 'PENDING',
    },
  },
});

describe('Ficha dunha sesión dunha tempada (ADR-0039)', () => {
  useMonday();

  it('a fila «Tempada», sen «Cambiar» (as sesións non se moven) e «Ver a tempada» abre o seu detalle', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle, SESSION);
    expect(text(card.querySelector('[data-value="series"] dd'))).toBe('luns e mércores · 57 sesións');
    expect(card.querySelector('[data-detail-change]')).toBeNull();
    expect(card.querySelector('[data-detail-cancel]')).not.toBeNull();
    card.querySelector<HTMLButtonElement>('[data-detail-series]')!.click();
    (await expectRequest(`${BASE}/booking-series/s1`)).flush(series());
    (await expectRequest((r) => r.url === `${BASE}/bookings` && r.params.get('q') === 'Club Baloncesto')).flush({
      items: [],
      page: 0,
      size: 50,
      totalItems: 0,
    });
    await vi.waitFor(async () => {
      await settle();
      expect(document.querySelector('[data-series-detail="s1"]')).not.toBeNull();
    });
    expect(text(document.querySelector('[data-series-detail] [data-value="sessions"] dd'))).toBe('57');
  });

  it('Recepción ve a sesión sen a tempada (a API non lla dá) e tampouco «Cambiar»', async () => {
    const { el, settle } = await renderManage(fakeSession({ staff: 'naron' }));
    const organization = { id: 'o1', name: 'Club Baloncesto', seriesId: 's1' };
    const card = await openDetail(el, settle, { ...SESSION, organization });
    expect(card.querySelector('[data-detail-series]')).toBeNull();
    expect(card.querySelector('[data-detail-change]')).toBeNull();
  });

  it('se a tempada xa non existe, o erro no seu detalle', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle, SESSION);
    card.querySelector<HTMLButtonElement>('[data-detail-series]')!.click();
    (await expectRequest(`${BASE}/booking-series/s1`)).flush(
      { type: 'about:blank', title: 'x', status: 404, code: 'booking.series-not-found', params: {}, traceId: 't' },
      { status: 404, statusText: 'x' },
    );
    await vi.waitFor(async () => {
      await settle();
      expect(text(document.querySelector('furar-series-detail'))).toContain('Esta tempada xa non existe.');
    });
  });
});
