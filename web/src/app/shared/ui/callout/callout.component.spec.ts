// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { TagComponent } from '../tag/tag.component';
import { CalloutComponent } from './callout.component';

@Component({
  imports: [CalloutComponent, TagComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-callout id="a" tone="danger" live="alert" heading="Non se gardou"
      ><p>{{ 'Texto' }}</p></furar-callout
    >
    <furar-callout id="b" tone="success" live="status"
      ><p>{{ 'Gardado' }}</p></furar-callout
    >
    <furar-callout id="c"
      ><p>{{ 'Info' }}</p></furar-callout
    >
    <furar-tag id="t1">{{ 'Texto curto' }}</furar-tag>
    <furar-tag id="t2" tone="warn">{{ 'Dato persoal' }}</furar-tag>
  `,
})
class HostComponent {}

describe('furar-callout e furar-tag', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('o ton vai en texto para lectores (non só cor) e o rol só se se pide', async () => {
    const el = await render();
    const danger = el.querySelector('#a') as HTMLElement;
    expect(danger.getAttribute('role')).toBe('alert');
    expect(danger.dataset['tone']).toBe('danger');
    expect(danger.querySelector('.sr-only')?.textContent).toContain('Erro:');
    expect(danger.querySelector('.f-callout__title')?.textContent).toContain('Non se gardou');
    expect(danger.querySelector('furar-icon')?.getAttribute('aria-hidden')).toBe('true');
    expect(el.querySelector('#b')?.getAttribute('role')).toBe('status');
    expect(el.querySelector('#b .sr-only')?.textContent).toContain('Feito:');
    const info = el.querySelector('#c') as HTMLElement;
    expect(info.hasAttribute('role')).toBe(false);
    expect(info.dataset['tone']).toBe('info');
    expect(info.querySelector('.f-callout__title')).toBeNull();
  });

  it('as etiquetas levan o ton (neutral por defecto) e o conxunto pasa axe', async () => {
    const el = await render();
    expect(el.querySelector('#t1')?.getAttribute('data-tone')).toBe('neutral');
    expect(el.querySelector('#t2')?.getAttribute('data-tone')).toBe('warn');
    expect(el.querySelector('#t2')?.classList).toContain('f-tag');
    await expectNoAxeViolations(el);
  });
});
