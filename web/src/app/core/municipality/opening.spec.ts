// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, provideTestApi } from '../../../testing/api';
import { OpeningStatus, missingOpeningLegal } from './opening';

const LEGAL = {
  legalEntityName: 'Concello de Narón',
  nif: 'P1505400J',
  address: 'Praza de Galicia, 1',
  generalEmail: 'info@naron.example',
  dpoEmail: 'dpd@naron.example',
};

const facilities = (items: { status: string; missing: string[] }[]) => ({
  items: items.map((f, i) => ({ id: `f${i}`, ...f })),
  page: 0,
  size: 100,
  totalItems: items.length,
});

describe('«Para abrir as reservas»: o que falta', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: provideTestApi() }));
  afterEach(() => backend().verify());

  it('os cinco datos que usan os textos legais (en branco conta como baleiro)', () => {
    expect(missingOpeningLegal(LEGAL)).toEqual([]);
    expect(missingOpeningLegal({ ...LEGAL, nif: '  ', dpoEmail: undefined })).toEqual(['nif', 'dpoEmail']);
    expect(missingOpeningLegal(undefined)).toHaveLength(5);
  });

  it('cos datos legais incompletos, pendente sen mirar as instalacións', async () => {
    const opening = TestBed.inject(OpeningStatus);
    expect(await opening.pending({ slug: 'naron', legal: { ...LEGAL, nif: '' } })).toBe(true);
  });

  it('cos datos legais completos, pendente ata que hai unha instalación activa e publicable', async () => {
    const opening = TestBed.inject(OpeningStatus);
    const url = '/api/v1/municipalities/naron/facilities?page=0&size=100';
    const without = opening.pending({ slug: 'naron', legal: LEGAL });
    (await expectRequest(url)).flush(facilities([{ status: 'ACTIVE', missing: ['USES'] }]));
    expect(await without).toBe(true);
    const done = opening.pending({ slug: 'naron', legal: LEGAL });
    (await expectRequest(url)).flush(facilities([{ status: 'ACTIVE', missing: [] }]));
    expect(await done).toBe(false);
  });
});
