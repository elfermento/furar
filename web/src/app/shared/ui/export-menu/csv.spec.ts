// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { downloadCsv, toCsv } from './csv';

describe('toCsv e downloadCsv', () => {
  it('UTF-8 con BOM, «;» entre celas e CRLF ao final de cada fila', () => {
    const csv = toCsv([
      ['Mes', 'Reservas'],
      ['Outubro', 412],
    ]);
    expect(csv.charCodeAt(0)).toBe(0xfeff);
    expect(csv.slice(1)).toBe('Mes;Reservas\r\nOutubro;412\r\n');
  });

  it('decimais con coma, sen separador de miles; baleiras e non finitos sen texto', () => {
    expect(toCsv([[61.5, 1234.25, -3, 0, null, undefined, Number.NaN]]).slice(1)).toBe('61,5;1234,25;-3;0;;;\r\n');
  });

  it('comiñas RFC 4180 co separador, comiñas ou saltos de liña dentro', () => {
    expect(toCsv([['Campo; pista 1', 'O "Alto"', 'dúas\nliñas', 'Pavillón']]).slice(1)).toBe(
      '"Campo; pista 1";"O ""Alto""";"dúas\nliñas";Pavillón\r\n',
    );
  });

  it('neutraliza fórmulas (OWASP): = + - @, tabulador e retorno de carro levan un apóstrofo', () => {
    const csv = toCsv([['=SUM(A1)', '+34 981', '-12 %', '@cmd', '\tx', '\ry', 'ok=1']]).slice(1);
    expect(csv).toBe(`'=SUM(A1);'+34 981;'-12 %;'@cmd;'\tx;"'\ry";ok=1\r\n`);
  });

  it('unha fila baleira separa bloques', () => {
    expect(toCsv([['A'], [], ['B']]).slice(1)).toBe('A\r\n\r\nB\r\n');
  });

  it('descarga cun Blob e un <a download> e libera o URL', async () => {
    const view = document.defaultView!;
    const created: Blob[] = [];
    const createObjectURL = vi.fn((blob: Blob) => {
      created.push(blob);
      return 'blob:csv';
    });
    const revokeObjectURL = vi.fn();
    Object.assign(view.URL, { createObjectURL, revokeObjectURL });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toBe('indicadores-naron-2026-10.csv');
      expect(this.href).toBe('blob:csv');
    });

    downloadCsv(document, 'indicadores-naron-2026-10.csv', [['Mes', 'Reservas']]);

    expect(click).toHaveBeenCalledOnce();
    expect(created[0]!.type).toBe('text/csv;charset=utf-8');
    const bytes = new Uint8Array(await created[0]!.arrayBuffer());
    expect([...bytes.slice(0, 3)]).toEqual([0xef, 0xbb, 0xbf]);
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:csv');
    click.mockRestore();
  });
});
