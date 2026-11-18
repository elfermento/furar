// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ParticipantsSelection,
  SELF,
  firstName,
  joinNames,
  namedParticipants,
  participantNames,
  validBirthYear,
} from './participants';

describe('«Para quen»: nomes e anos', () => {
  it('xunta os nomes con comas e «e»', () => {
    expect(joinNames([], ' e ')).toBe('');
    expect(joinNames(['Marta'], ' e ')).toBe('Marta');
    expect(joinNames(['Marta', 'Uxía'], ' e ')).toBe('Marta e Uxía');
    expect(joinNames(['Marta', 'Uxía', 'Brais'], ' y ')).toBe('Marta, Uxía y Brais');
    expect(firstName('  Uxía  Vilar Rey ')).toBe('Uxía');
  });

  it('o ano só sae onde a API o manda (a xestión)', () => {
    const people = [
      { kind: 'SELF' as const, name: 'Marta Vilar' },
      { kind: 'DEPENDENT' as const, name: 'Uxía Vilar', birthYear: 2015 },
    ];
    expect(participantNames(people)).toEqual(['Marta Vilar', 'Uxía Vilar (2015)']);
    expect(participantNames(undefined)).toEqual([]);
  });

  it('unha reserva só para quen a ten non repite o seu nome; só para unha persoa a cargo, si', () => {
    expect(namedParticipants([{ kind: 'SELF', name: 'Marta Vilar' }])).toEqual([]);
    expect(namedParticipants([{ kind: 'DEPENDENT', name: 'Uxía Vilar' }])).toEqual(['Uxía Vilar']);
    expect(namedParticipants([])).toEqual([]);
  });

  it('o ano de nacemento: catro cifras, do actual menos 120 ao actual', () => {
    expect(validBirthYear('2015', 2026, 120)).toBe(true);
    expect(validBirthYear(' 1906 ', 2026, 120)).toBe(true);
    expect(validBirthYear('1905', 2026, 120)).toBe(false);
    expect(validBirthYear('2027', 2026, 120)).toBe(false);
    expect(validBirthYear('15', 2026, 120)).toBe(false);
    expect(validBirthYear('dous', 2026, 120)).toBe(false);
  });
});

describe('«Para quen»: o escollido', () => {
  it('empeza con quen reserva: unha praza', () => {
    const who = new ParticipantsSelection();
    expect(who.seats()).toBe(1);
    expect(who.selected()).toEqual([SELF]);
  });

  it('cada persoa marcada é unha praza; pódese reservar só para unha persoa a cargo', () => {
    const who = new ParticipantsSelection();
    who.toggle('d1');
    who.toggle('d2');
    expect(who.seats()).toBe(3);
    who.toggle(SELF);
    who.toggle('d2');
    expect(who.selected()).toEqual(['d1']);
    expect(who.seats()).toBe(1);
    who.toggle('d1');
    expect(who.seats()).toBe(0);
  });

  it('a ficha recén gardada queda marcada unha vez e as eliminadas deixan de contar', () => {
    const who = new ParticipantsSelection();
    who.add('d1');
    who.add('d1');
    who.add('d2');
    expect(who.dependentIds()).toEqual(['d1', 'd2']);
    who.keep(['d2', 'd3']);
    expect(who.dependentIds()).toEqual(['d2']);
  });

  it('os acompañantes do mostrador engádense, escríbense e quítanse fila a fila', () => {
    const who = new ParticipantsSelection();
    who.addCompanion();
    who.addCompanion();
    who.editCompanion(1, { name: 'Icía Pardo' });
    who.editCompanion(1, { year: '2016' });
    expect(who.companions()).toEqual([
      { name: '', year: '' },
      { name: 'Icía Pardo', year: '2016' },
    ]);
    expect(who.seats()).toBe(3);
    who.removeCompanion(0);
    expect(who.companions()).toEqual([{ name: 'Icía Pardo', year: '2016' }]);
    who.set(false, ['d1'], []);
    expect(who.seats()).toBe(1);
  });
});
