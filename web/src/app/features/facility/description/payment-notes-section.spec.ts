// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { paymentNotes, renderDetail } from '../../../../testing/facility-detail';
import { facility } from '../detail/facility.fixture';

const URL = '/api/v1/municipalities/naron/facilities/f1';

async function render(scope: 'ALL' | 'FACILITY', notes = {}) {
  const view = await renderDetail({ facility: { paymentNotes: notes }, paymentNotes: paymentNotes(scope) });
  const section = () => view.el.querySelector<HTMLElement>('#facility-payment-notes-section')!;
  return { ...view, section };
}

describe('Sección Pago e devolución da ficha (D2-11, «Distinto por instalación»)', () => {
  afterEach(() => {
    backend().verify();
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  it('igual para todas as instalacións: a ficha non ten esta sección', async () => {
    const { section } = await render('ALL');
    expect(section().querySelector('furar-edit-section')).toBeNull();
  });

  it('por instalación: os seus textos ou, sen eles, o de Furar (non se herda o do concello)', async () => {
    const { section } = await render('FACILITY', { payment: { gl: 'Na billeteira', es: 'En la taquilla' } });
    await vi.waitFor(() => expect(section().querySelector('h2')?.textContent).toContain('Pago e devolución'));
    const rows = [...section().querySelectorAll('[data-value]')].map((row) => [
      row.querySelector('dt')?.textContent?.trim(),
      row.querySelector('dd')?.textContent?.trim(),
    ]);
    expect(rows).toEqual([
      ['Como se paga', 'Na billeteira'],
      ['Como se devolve', 'Por defecto: «O concello dirache como cha devolve»'],
    ]);
    await expectNoAxeViolations(section());
  });

  it('garda co If-Match da ficha; un texto nun só idioma é un erro', async () => {
    const { section, edit, type, save, stable, el } = await render('FACILITY');
    await vi.waitFor(() => expect(section().querySelector('h2')).not.toBeNull());
    await edit();
    const payment = () => section().querySelector<HTMLTextAreaElement>('#facility-payment-payment')!;
    await type(payment(), 'Con tarxeta');
    await save();
    expect(section().querySelector('.f-field-error')).not.toBeNull();
    backend().expectNone({ method: 'PUT', url: `${URL}/payment-notes` });
    section().querySelector<HTMLButtonElement>('button[data-lang="es"]')!.click();
    await stable();
    await type(payment(), 'Con tarjeta');
    await save();
    const put = await expectRequest({ method: 'PUT', url: `${URL}/payment-notes` });
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({
      payment: { gl: 'Con tarxeta', es: 'Con tarjeta' },
      refund: { gl: '', es: '' },
    });
    put.flush(facility({ version: 4, paymentNotes: { payment: { gl: 'Con tarxeta', es: 'Con tarjeta' } } }));
    await vi.waitFor(async () => {
      await stable();
      expect(el.querySelector('furar-action-bar')).toBeNull();
    });
  });
});
