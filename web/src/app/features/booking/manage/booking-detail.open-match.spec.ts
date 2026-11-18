// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { expectRequest, fakeSession, flushProblem } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { BASE, detail, dialog, openDetail, renderManage, text, useMonday } from '../../../../testing/manage-bookings';
import { bookingMatch } from '../../../../testing/open-matches';
import { ToastService } from '../../../shared/ui/toast/toast.service';

const HIDDEN_URL = `${BASE}/bookings/b1/open-match/hidden`;

describe('Ficha da reserva: a partida (ADR-0040)', () => {
  useMonday();

  it('a xestión: os números da partida, sen nomes, e a fila «Partida» co chip-menú «Na portada / Oculta»; axe', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle, detail({ openMatch: bookingMatch({ pending: 1 }) }));
    const row = card.querySelector('[data-value="match"]')!;
    expect(text(row.querySelector('dt'))).toBe('Busca xente');
    expect(text(row.querySelector('dd'))).toBe('4 de 10 · mínimo 6 · máximo 10 · 1 pendentes');
    const line = card.querySelector('[data-detail-match]')!;
    expect(text(line.querySelector('.f-field-line__label'))).toBe('Partida');
    const chip = line.querySelector<HTMLButtonElement>('furar-chip-menu button')!;
    expect(text(chip)).toBe('Na portada');
    await expectNoAxeViolations(card);

    chip.click();
    await vi.waitFor(async () => {
      await settle();
      expect(document.querySelector('[furarMenuItemRadio], [role="menuitemradio"]')).not.toBeNull();
    });
    const hide = [...document.querySelectorAll<HTMLElement>('[role="menuitemradio"]')].find((o) =>
      o.textContent?.includes('Oculta'),
    )!;
    hide.click();
    const put = await expectRequest({ method: 'PUT', url: HIDDEN_URL });
    expect(put.request.body).toEqual({ hidden: true });
    put.flush(bookingMatch({ hidden: true }));
    await vi.waitFor(async () => {
      await settle();
      expect(text(card.querySelector('[data-detail-match] furar-chip-menu button'))).toBe('Oculta');
    });
    expect(TestBed.inject(ToastService).current()).toMatchObject({ key: 'openMatch.manage.hiddenToast' });
  });

  it('un erro ao ocultar vai ao aviso da ficha', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle, detail({ openMatch: bookingMatch({ hidden: true }) }));
    card.querySelector<HTMLButtonElement>('[data-detail-match] furar-chip-menu button')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(document.querySelector('[role="menuitemradio"]')).not.toBeNull();
    });
    [...document.querySelectorAll<HTMLElement>('[role="menuitemradio"]')]
      .find((o) => o.textContent?.includes('Na portada'))!
      .click();
    flushProblem(await expectRequest({ method: 'PUT', url: HIDDEN_URL }), 404, 'booking.match-not-found');
    await vi.waitFor(async () => {
      await settle();
      expect(text(card.querySelector('[data-detail-error]'))).toContain('Esta partida xa non está dispoñible.');
    });
  });

  it('o mostrador ve os números pero non oculta', async () => {
    const { el, settle } = await renderManage(fakeSession({ staff: 'naron' }));
    const card = await openDetail(el, settle, detail({ openMatch: bookingMatch() }));
    expect(card.querySelector('[data-value="match"]')).not.toBeNull();
    expect(card.querySelector('[data-detail-match]')).toBeNull();
  });

  it('pechada: sen chip-menú e «xa non busca»', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle, detail({ openMatch: bookingMatch({ status: 'CLOSED' }) }));
    expect(text(card.querySelector('[data-value="match"]'))).toContain('xa non busca');
    expect(card.querySelector('[data-detail-match]')).toBeNull();
  });

  it('cancelar unha reserva con partida avisa de cantas persoas apuntadas se avisan', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle, detail({ openMatch: bookingMatch({ confirmed: 1 }) }));
    card.querySelector<HTMLButtonElement>('[data-detail-cancel]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-cancel-match]')).toBeTruthy();
    });
    expect(text(dialog()!.querySelector('[data-cancel-match]'))).toBe(
      'Cancélase tamén a partida e avisamos por correo á persoa apuntada.',
    );
    dialog()!.querySelector<HTMLButtonElement>('[data-cancel-keep]')!.click();
    await vi.waitFor(() => expect(dialog()).toBeNull());
  });
});
