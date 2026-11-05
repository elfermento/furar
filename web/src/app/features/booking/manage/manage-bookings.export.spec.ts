// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { expectRequest, fakeSession } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { BASE, dialog, renderManage, text, useMonday } from '../../../../testing/manage-bookings';
import { ToastService } from '../../../shared/ui/toast/toast.service';

const EXPORT_URL = `${BASE}/bookings/export`;

describe('Reservas do concello: «Descargar CSV» (maqueta 60)', () => {
  useMonday();

  async function openExport(el: HTMLElement, settle: () => Promise<void>): Promise<HTMLElement> {
    el.querySelector<HTMLButtonElement>('[data-manage-export]')!.click();
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()?.querySelector('[data-export-download]')).toBeTruthy();
    });
    return dialog()!;
  }

  it('a xestión descarga o mes de hoxe co nome do concello e do período; aviso; axe', async () => {
    const { el, settle } = await renderManage();
    const button = el.querySelector('[data-manage-export]');
    expect(button?.getAttribute('aria-label')).toBe('Descargar CSV');
    const box = await openExport(el, settle);
    expect(text(box.querySelector('h2'))).toBe('Descargar CSV');
    expect(text(box.querySelector('[data-date-chip]'))).toBe('Do 01/10 ao 31/10');
    expect(box.querySelector('input[type="date"]')).toBeNull();
    expect([...box.querySelectorAll('[data-filter]')].map((g) => g.getAttribute('data-filter'))).toEqual([
      'facility',
      'state',
    ]);
    await expectNoAxeViolations(box);

    const view = document.defaultView!;
    const createObjectURL = vi.fn(() => 'blob:csv');
    Object.assign(view.URL, { createObjectURL, revokeObjectURL: vi.fn() });
    const names: string[] = [];
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      names.push(this.download);
    });
    box.querySelector<HTMLButtonElement>('[data-filter="state"] [data-value="CANCELLED"]')!.click();
    await settle();
    box.querySelector<HTMLButtonElement>('[data-export-download]')!.click();
    const get = await expectRequest((r) => r.url === EXPORT_URL);
    expect(get.request.params.get('from')).toBe('2026-10-01');
    expect(get.request.params.get('to')).toBe('2026-10-31');
    expect(get.request.params.getAll('status')).toEqual(['CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY']);
    expect(get.request.params.has('facilityId')).toBe(false);
    get.flush(new Blob(['﻿Data;Inicio\r\n'], { type: 'text/csv' }));
    await vi.waitFor(async () => {
      await settle();
      expect(TestBed.inject(ToastService).current()?.key).toBe('booking.manage.export.done');
    });
    expect(TestBed.inject(ToastService).current()?.params).toEqual({
      file: 'furar-naron-reservas-2026-10-01-2026-10-31.csv',
    });
    expect(names).toEqual(['furar-naron-reservas-2026-10-01-2026-10-31.csv']);
    expect(dialog()).toBeNull();
    click.mockRestore();
  });

  it('sen datas non descarga e dío; o erro da API queda no diálogo', async () => {
    const { el, settle } = await renderManage();
    const box = await openExport(el, settle);
    box.querySelector<HTMLButtonElement>('[data-date-clear]')!.click();
    await settle();
    expect(box.querySelector('[data-export-error]')).toBeNull();
    box.querySelector<HTMLButtonElement>('[data-export-download]')!.click();
    await settle();
    expect(text(box.querySelector('[data-export-error]'))).toBe('Escolle o primeiro e o último día.');

    box.querySelector<HTMLButtonElement>('[data-date-add]')!.click();
    await settle();
    box.querySelector<HTMLButtonElement>('[data-preset="today"]')!.click();
    await settle();
    expect(box.querySelector('[data-export-error]')).toBeNull();
    box.querySelector<HTMLButtonElement>('[data-export-download]')!.click();
    const get = await expectRequest((r) => r.url === EXPORT_URL);
    expect(get.request.params.get('from')).toBe('2026-10-26');
    expect(get.request.params.get('to')).toBe('2026-10-26');
    get.flush(null, { status: 500, statusText: 'Server Error' });
    await vi.waitFor(async () => {
      await settle();
      expect(box.querySelector('furar-problem-alert furar-callout')).not.toBeNull();
    });
    expect(dialog()).not.toBeNull();
  });

  it('o mostrador (STAFF) non ve «Descargar CSV»', async () => {
    const { el } = await renderManage(fakeSession({ staff: 'naron' }));
    expect(el.querySelector('[data-manage-filters]')).not.toBeNull();
    expect(el.querySelector('[data-manage-export]')).toBeNull();
  });
});
