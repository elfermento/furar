// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { provideTestI18n } from '../../../../testing/i18n';
import { StarRatingComponent } from '../star-rating/star-rating.component';
import { formatAverage } from './rating-format';
import { StarsComponent } from './stars.component';

describe('furar-stars e furar-star-rating (ADR-0037)', () => {
  it('as estrelas: tantas cheas coma a nota, cun nome', async () => {
    TestBed.configureTestingModule({ providers: [provideTestI18n('es')] });
    const fixture = TestBed.createComponent(StarsComponent);
    fixture.componentRef.setInput('value', 3);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.getAttribute('role')).toBe('img');
    expect(el.getAttribute('aria-label')).toBe('3 de 5 estrellas');
    expect(el.querySelectorAll('furar-icon')).toHaveLength(5);
    expect(el.getAttribute('data-stars')).toBe('3');
  });

  it('escoller a nota: cinco radios nativos co seu nome e o erro ligado ao grupo', async () => {
    TestBed.configureTestingModule({ providers: [provideTestI18n('gl')] });
    const fixture = TestBed.createComponent(StarRatingComponent);
    fixture.componentRef.setInput('label', 'Nota');
    fixture.componentRef.setInput('required', true);
    fixture.componentRef.setInput('error', 'Escolle unha nota.');
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const radios = [...el.querySelectorAll<HTMLInputElement>('input[type="radio"]')];
    expect(radios).toHaveLength(5);
    expect(new Set(radios.map((r) => r.name)).size).toBe(1);
    expect(el.querySelector('legend')?.textContent).toContain('Nota');
    expect([...el.querySelectorAll('.f-star-rating__star .sr-only')].map((s) => s.textContent?.trim())).toEqual([
      '1 estrela',
      '2 estrelas',
      '3 estrelas',
      '4 estrelas',
      '5 estrelas',
    ]);
    const group = el.querySelector('fieldset')!;
    expect(group.getAttribute('aria-describedby')).toBe(el.querySelector('.f-field-error')?.id);
    radios[2]!.click();
    await fixture.whenStable();
    expect(fixture.componentInstance.value()).toBe(3);
    expect(el.querySelectorAll('.f-star-rating__star[data-on]')).toHaveLength(3);
  });

  it('a media cun decimal e coma, tamén en galego', () => {
    expect(formatAverage(4.583, 'gl')).toBe('4,6');
    expect(formatAverage(4, 'es')).toBe('4,0');
  });
});
