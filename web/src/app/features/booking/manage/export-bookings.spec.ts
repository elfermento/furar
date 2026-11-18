// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { exportChoice, exportFileName, exportParams, exportRangeError } from './export-bookings';
import { NO_FILTERS } from './manage-bookings';

describe('«Descargar CSV» das reservas: período, filtros e nome', () => {
  it('ao abrir, os filtros da lista; sen datas, o mes de hoxe', () => {
    expect(exportChoice(NO_FILTERS, '2026-10-26')).toEqual({
      from: '2026-10-01',
      to: '2026-10-31',
      facilityIds: [],
      states: [],
    });
    expect(
      exportChoice(
        { ...NO_FILTERS, from: '2026-11-01', to: '2026-11-30', facilityIds: ['f1'], states: ['CONFIRMED'] },
        '2026-10-26',
      ),
    ).toEqual({ from: '2026-11-01', to: '2026-11-30', facilityIds: ['f1'], states: ['CONFIRMED'] });
    // Só «desde»: falta o último día.
    expect(exportChoice({ ...NO_FILTERS, from: '2026-11-01' }, '2026-10-26').to).toBeNull();
  });

  it('o período: obrigatorio e como moito doce meses, como na API', () => {
    expect(exportRangeError(null, null)).toBe('required');
    expect(exportRangeError('2026-01-01', null)).toBe('required');
    expect(exportRangeError('2026-01-01', '2026-01-01')).toBeNull();
    expect(exportRangeError('2026-01-01', '2026-12-31')).toBeNull();
    expect(exportRangeError('2026-01-01', '2027-01-01')).toBe('tooLong');
    expect(exportRangeError('2024-02-29', '2025-02-27')).toBeNull();
    expect(exportRangeError('2024-02-29', '2025-02-28')).toBe('tooLong');
  });

  it('os parámetros: «Canceladas» son as dúas cancelacións; sen filtros, sen parámetros', () => {
    expect(exportParams('naron', { from: '2026-11-01', to: '2026-11-30', facilityIds: [], states: [] })).toEqual({
      slug: 'naron',
      from: '2026-11-01',
      to: '2026-11-30',
    });
    expect(
      exportParams('naron', {
        from: '2026-11-01',
        to: '2026-11-30',
        facilityIds: ['f1', 'f2'],
        states: ['CANCELLED'],
      }),
    ).toEqual({
      slug: 'naron',
      from: '2026-11-01',
      to: '2026-11-30',
      facilityId: ['f1', 'f2'],
      status: ['CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY'],
    });
    expect(exportFileName('naron', '2026-11-01', '2026-11-30')).toBe('furar-naron-reservas-2026-11-01-2026-11-30.csv');
  });
});
