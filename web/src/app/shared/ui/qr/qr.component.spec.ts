// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { LanguageService } from '../../../core/i18n/language.service';
import { QrComponent } from './qr.component';

const PATH = 'M0 0h7v7h-7zM1 1v5h5v-5zM2 2h3v3h-3zM10 10h1v1h-1z';

@Component({
  imports: [QrComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-qr [size]="21" [path]="path" [code]="'FUR-7K2Q'" />`,
})
class HostComponent {
  readonly path = PATH;
}

describe('furar-qr', () => {
  async function render(lang: 'gl' | 'es') {
    TestBed.configureTestingModule({ providers: [provideTestI18n(lang)] });
    const fixture = TestBed.createComponent(HostComponent);
    await TestBed.inject(LanguageService).use(lang);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('pinta o path da API como atributo, co tamaño en módulos', async () => {
    const el = await render('gl');
    const svg = el.querySelector('svg')!;
    expect(svg.getAttribute('viewBox')).toBe('0 0 21 21');
    expect(svg.querySelector('path')?.getAttribute('d')).toBe(PATH);
    expect(svg.querySelectorAll('*')).toHaveLength(1);
  });

  it.each([
    ['gl', 'Código QR da reserva FUR-7K2Q'],
    ['es', 'Código QR de la reserva FUR-7K2Q'],
  ] as const)('é unha imaxe co código no nome (%s) e pasa axe', async (lang, name) => {
    const el = await render(lang);
    const svg = el.querySelector('svg')!;
    expect(svg.getAttribute('role')).toBe('img');
    expect(svg.getAttribute('aria-label')).toBe(name);
    await expectNoAxeViolations(el);
  });
});
