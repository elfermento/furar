// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { fakeSession } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { detail, openDetail, renderManage, text, useMonday } from '../../../../testing/manage-bookings';

describe('Reservas do concello: reserva por outra persoa (maqueta 58)', () => {
  useMonday();

  it('«Nova reserva» á vista para Recepción', async () => {
    const { el } = await renderManage(fakeSession({ staff: 'naron' }));
    const button = el.querySelector('[data-manage-new]');
    expect(button?.getAttribute('href')).toBe('/naron/xestion/reservas/nova');
    expect(text(button)).toBe('Nova reserva');
  });

  it('o detalle di quen a fixe, o teléfono, o correo que saíu e «Cambiar»; axe', async () => {
    const { el, settle } = await renderManage(fakeSession({ staff: 'naron' }));
    const card = await openDetail(
      el,
      settle,
      detail({
        holderAccount: false,
        holderPhone: '666 123 456',
        bookedBy: { name: 'Lucía Rey', role: 'STAFF' },
        lastMail: { kind: 'CONFIRMED', sentAt: '2026-10-26T09:42:00Z' },
      }),
    );
    expect(text(card.querySelector('furar-value-list'))).toContain('Reservada porLucía Rey · Recepción');
    expect(card.querySelector('[data-detail-phone]')?.getAttribute('href')).toBe('tel:666 123 456');
    expect(text(card.querySelector('[data-detail-mail-note]'))).toBe('Confirmación enviada o 26/10/2026, 10:42');
    expect(card.querySelector('[data-detail-change]')?.getAttribute('href')).toBe('/naron/xestion/reservas/b1/cambiar');
    // Recepción non cancela.
    expect(card.querySelector('[data-detail-cancel]')).toBeNull();
    await expectNoAxeViolations(card);
  });

  it('sen correo, o código que hai que darlle', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(
      el,
      settle,
      detail({ holderAccount: false, holderEmail: undefined, bookedBy: { name: 'Marta Souto', role: 'MANAGER' } }),
    );
    expect(card.querySelector('[data-detail-email]')).toBeNull();
    expect(text(card.querySelector('[data-detail-mail-note]'))).toBe('Sen correo: dálle o código FUR-7K2Q.');
  });
});
