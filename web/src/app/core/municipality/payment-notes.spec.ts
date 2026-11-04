// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { Api } from '../../api/api';
import { backend, expectRequest, provideTestApi } from '../../../testing/api';
import { effectivePaymentTexts, loadPaymentTexts } from './payment-notes';

const FURAR_PAYMENT = { gl: 'No mostrador, ao chegar', es: 'En el mostrador, al llegar' };
const FURAR_REFUND = { gl: 'O concello dirache como cha devolve', es: 'El ayuntamiento te dirá cómo te lo devuelve' };
const OWN = { gl: 'Con tarxeta', es: 'Con tarjeta' };
const notes = (scope: 'ALL' | 'FACILITY') => ({
  payment: OWN,
  paymentDefault: false,
  refund: FURAR_REFUND,
  refundDefault: true,
  scope,
  furarPayment: FURAR_PAYMENT,
  furarRefund: FURAR_REFUND,
});

describe('Pago e devolución: de onde sae cada texto (D2-11)', () => {
  it('igual para todas: os do concello; por instalación: os dela ou os de Furar, nunca os do concello', () => {
    expect(effectivePaymentTexts(notes('ALL'), { payment: { gl: 'x', es: 'x' } }).payment).toEqual(OWN);
    expect(effectivePaymentTexts(notes('FACILITY'), { payment: { gl: 'Na billeteira', es: 'En taquilla' } })).toEqual({
      payment: { gl: 'Na billeteira', es: 'En taquilla' },
      refund: FURAR_REFUND,
    });
    expect(effectivePaymentTexts(notes('FACILITY'), {}).payment).toEqual(FURAR_PAYMENT);
    expect(effectivePaymentTexts(null, null)).toEqual({ payment: null, refund: null });
  });

  it('só pide os da instalación se van por instalación', async () => {
    TestBed.configureTestingModule({ providers: provideTestApi() });
    const api = TestBed.inject(Api);
    const all = loadPaymentTexts(api, 'naron', 'f1');
    (await expectRequest('/api/v1/municipalities/naron/payment-notes')).flush(notes('ALL'));
    expect((await all).payment).toEqual(OWN);

    const byFacility = loadPaymentTexts(api, 'naron', 'f1');
    (await expectRequest('/api/v1/municipalities/naron/payment-notes')).flush(notes('FACILITY'));
    (await expectRequest('/api/v1/municipalities/naron/public/facilities/f1/payment-notes')).flush({
      refund: { gl: 'En metálico', es: 'En efectivo' },
    });
    expect(await byFacility).toEqual({ payment: FURAR_PAYMENT, refund: { gl: 'En metálico', es: 'En efectivo' } });
    backend().verify();
  });
});
