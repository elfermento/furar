// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { furarLegalHtml } from './furar-legal-html';

const t = (key: string, params: Record<string, string> = {}) =>
  [key, ...Object.entries(params).map(([k, v]) => `${k}=${v}`)].join(' ');

const NOTICE = {
  activityVisible: 'P1Y',
  activityRetention: 'P3Y',
  bookingRetention: 'P3Y',
  paidBookingRetention: 'P6Y',
  seriesRequestRetention: 'P1Y',
};

describe('furarLegalHtml e datos abertos (ADR-0034, punto 28)', () => {
  const data = (openData: boolean) => ({
    name: 'Narón',
    lang: 'gl',
    legal: { legalEntityName: 'Concello de Narón' },
    notice: NOTICE,
    openData,
  });

  it('aceso: o aviso legal di a licenza e a privacidade que non hai datos de quen reserva', () => {
    expect(furarLegalHtml('legalNotice', data(true), t)).toContain('legalNotice.openData owner=');
    expect(furarLegalHtml('privacy', data(true), t)).toContain('privacy.residents.openData');
  });

  it('apagado: ningún dos dous', () => {
    expect(furarLegalHtml('legalNotice', data(false), t)).not.toContain('legalNotice.openData');
    expect(furarLegalHtml('privacy', data(false), t)).not.toContain('privacy.residents.openData');
  });
});
