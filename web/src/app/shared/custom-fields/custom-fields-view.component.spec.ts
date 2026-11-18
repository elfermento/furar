// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { SELECT_OPTIONS, definition } from '../../../testing/fixtures';
import { provideTestI18n } from '../../../testing/i18n';
import { CustomFieldsViewComponent, format } from './custom-fields-view.component';
import { localized } from './localized';

describe('CustomFieldsViewComponent', () => {
  it('formata cada tipo no idioma pedido', () => {
    expect(format(definition({ type: 'BOOLEAN' }), true, 'gl')).toMatchObject({ text: 'common.yes', translate: true });
    expect(format(definition({ type: 'BOOLEAN' }), false, 'gl').text).toBe('common.no');
    expect(format(definition({ type: 'SELECT', options: SELECT_OPTIONS }), 'gym', 'es').text).toBe('Gimnasio');
    expect(format(definition({ type: 'SELECT', options: SELECT_OPTIONS }), 'xx', 'es').text).toBe('xx');
    expect(format(definition({ type: 'MULTI_SELECT', options: SELECT_OPTIONS }), ['pool', 'gym'], 'gl').text).toBe(
      'Piscina, Ximnasio',
    );
    expect(format(definition({ type: 'NUMBER' }), 1234.5, 'es').text).toMatch(/1\.?234,5/);
    expect(format(definition({ type: 'DATE' }), '2026-11-19', 'gl').text).toBe('19 de novembro de 2026');
    expect(format(definition({ type: 'DATE' }), '2026-11-19', 'es').text).toBe('19 de noviembre de 2026');
    expect(format(definition({ type: 'DATE' }), 5, 'gl').text).toBe('5');
    const spy = vi.spyOn(Intl.DateTimeFormat, 'supportedLocalesOf').mockReturnValue([]);
    try {
      // Sen datos gl en Intl (Chrome): nin «2026 M11 19» nin «1,234.5».
      expect(format(definition({ type: 'DATE' }), '2026-11-19', 'gl').text).toBe('19 de novembro de 2026');
      expect(format(definition({ type: 'NUMBER' }), 1234.5, 'gl').text).toMatch(/^1\.?234,5$/);
    } finally {
      spy.mockRestore();
    }
    expect(format(definition(), 'ola', 'gl').text).toBe('ola');
  });

  it('pinta os campos con valor, tamén os desactivados, nunha lista de definicións', async () => {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(CustomFieldsViewComponent);
    fixture.componentRef.setInput('definitions', [
      definition({ code: 'phone' }),
      definition({ code: 'defib', type: 'BOOLEAN', label: { gl: 'Desfibrilador', es: 'Desfibrilador' } }),
      definition({ code: 'empty' }),
      definition({ code: 'old', status: 'INACTIVE', label: { gl: 'Vello', es: 'Viejo' } }),
    ]);
    fixture.componentRef.setInput('values', { phone: '981 000 000', defib: true, old: 'x' });
    fixture.componentRef.setInput('lang', 'gl');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    // Na orde da API (alfabética no servidor), sen reordenar.
    expect([...el.querySelectorAll('dt')].map((d) => d.textContent?.trim())).toEqual([
      'Teléfono',
      'Desfibrilador',
      'Vello',
    ]);
    expect([...el.querySelectorAll('dd')].map((d) => d.textContent?.trim())).toEqual(['981 000 000', 'Si', 'x']);
  });

  it('localized usa o outro idioma se falta o pedido', () => {
    expect(localized({ gl: 'Ola', es: '' }, 'es')).toBe('Ola');
    expect(localized(undefined, 'gl')).toBe('');
  });
});
