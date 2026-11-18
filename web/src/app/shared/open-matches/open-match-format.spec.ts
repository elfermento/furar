// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { bookingMatch, matchCard, myMatch } from '../../../testing/open-matches';
import {
  openMatchChip,
  openMatchCounts,
  openMatchCountsText,
  openMatchRule,
  openMatchTitle,
  openMatchUseDetail,
  openMatchWhenParts,
  searchingChip,
} from './open-match-format';
import { bookingOpenMatch } from './open-matches.store';

/** Tradución de proba: os textos de verdade que importan aquí. */
const TEXTS: Record<string, string> = {
  'openMatch.ofTotal': '{{total}} de {{maximum}}',
  'openMatch.missingOne': 'Falta 1',
  'openMatch.missingOther': 'Faltan {{count}}',
  'openMatch.freeOne': 'Queda 1 praza',
  'openMatch.freeOther': 'Quedan {{count}} prazas',
  'openMatch.full': 'Completa',
  'openMatch.page.rule': 'Xógase se hai {{minimum}} persoas antes do {{day}} ás {{time}}. Máximo {{maximum}}.',
};
const t = (key: string, params: Record<string, unknown> = {}) =>
  (TEXTS[key] ?? key).replace(/\{\{(\w+)\}\}/g, (_, name: string) => String(params[name]));

describe('Buscar xente para xogar: os textos da partida', () => {
  it('as contas: o que falta para o mínimo e o sitio ata o máximo', () => {
    expect(openMatchCounts(matchCard())).toEqual({ total: 4, maximum: 10, missing: 2, free: 6 });
    expect(openMatchCounts(matchCard({ total: 12, minimum: 10, maximum: 12 }))).toEqual({
      total: 12,
      maximum: 12,
      missing: 0,
      free: 0,
    });
  });

  it('«4 de 10 · faltan 2», en singular se falta 1, as prazas que quedan e completa', () => {
    expect(openMatchCountsText(matchCard(), t)).toBe('4 de 10 · faltan 2');
    expect(openMatchCountsText(matchCard({ total: 3, minimum: 4, maximum: 4 }), t)).toBe('3 de 4 · falta 1');
    expect(openMatchCountsText(matchCard({ total: 6, minimum: 6, maximum: 8 }), t)).toBe('6 de 8 · quedan 2 prazas');
    expect(openMatchCountsText(matchCard({ total: 7, minimum: 6, maximum: 8 }), t)).toBe('7 de 8 · queda 1 praza');
    expect(openMatchCountsText(matchCard({ total: 12, minimum: 10, maximum: 12 }), t)).toBe('12 de 12 · completa');
  });

  it('o chip da tarxeta: azul se falta xente, verde se queda sitio, vermello completa', () => {
    expect(openMatchChip(matchCard())).toEqual({ key: 'missingOther', tone: 'info', count: 2 });
    expect(openMatchChip(matchCard({ total: 5 }))).toEqual({ key: 'missingOne', tone: 'info', count: 1 });
    expect(openMatchChip(matchCard({ total: 9, minimum: 6 }))).toEqual({ key: 'freeOne', tone: 'success', count: 1 });
    expect(openMatchChip(matchCard({ total: 10 }))).toEqual({ key: 'full', tone: 'danger', count: 0 });
  });

  it('o nome: o título ou, sen el, o uso (o día e a hora van debaixo); o uso só se non é o nome', () => {
    expect(openMatchTitle(matchCard())).toBe('Pachanga de venres');
    expect(openMatchTitle(matchCard({ title: undefined }))).toBe('Fútbol sala');
    expect(openMatchUseDetail(matchCard())).toBe('Fútbol sala');
    expect(openMatchUseDetail(matchCard({ title: undefined }))).toBeNull();
  });

  it('cando: longo ou curto, coas horas', () => {
    expect(openMatchWhenParts(matchCard(), 'gl')).toEqual(['venres, 20 de novembro', '20:00–21:30']);
    expect(openMatchWhenParts(matchCard(), 'gl', true)).toEqual(['ven. 20 nov.', '20:00–21:30']);
  });

  it('a regra do prazo, co día e a hora de Madrid', () => {
    expect(openMatchRule({ minimum: 6, maximum: 10, deadline: '2099-11-20T07:00:00Z' }, 'gl', t)).toBe(
      'Xógase se hai 6 persoas antes do venres, 20 de novembro ás 08:00. Máximo 10.',
    );
  });

  it('«Buscando xente» só mentres busca e se se ve na portada', () => {
    expect(searchingChip(bookingMatch())).toEqual({ total: 4, maximum: 10 });
    expect(searchingChip(bookingMatch({ hidden: true }))).toBeNull();
    expect(searchingChip(bookingMatch({ status: 'CLOSED' }))).toBeNull();
    expect(searchingChip(undefined)).toBeNull();
  });

  it('o resumo da reserva a partir da partida de quen organiza: as confirmadas e as pendentes', () => {
    const match = myMatch({
      me: { role: 'ORGANIZER' },
      players: [{ id: 'p1', name: 'Brais Castro' }],
      requests: [
        { id: 'r1', name: 'Xoán Iglesias', requestedAt: '2099-11-18T10:00:00Z' },
        { id: 'r2', name: 'Carme Pena', requestedAt: '2099-11-18T11:00:00Z' },
      ],
    });
    expect(bookingOpenMatch(match)).toEqual({
      id: 'm1',
      status: 'OPEN',
      hidden: false,
      total: 4,
      minimum: 6,
      maximum: 10,
      confirmed: 1,
      pending: 2,
    });
  });
});
