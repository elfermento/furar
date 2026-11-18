// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { provideTestI18n } from '../../../../testing/i18n';
import { SeatsChipComponent, seatsChip } from './seats-chip.component';

describe('seatsChip: as prazas libres nun chip', () => {
  it('verde con máis de 2, ámbar coas últimas 2 e a última, gris completa; sen límite, ningún', () => {
    expect(seatsChip(6)).toEqual({ key: 'free', tone: 'success', count: 6 });
    expect(seatsChip(2)).toEqual({ key: 'lastTwo', tone: 'warn', count: 2 });
    expect(seatsChip(1)).toEqual({ key: 'lastOne', tone: 'warn', count: 1 });
    expect(seatsChip(0)).toEqual({ key: 'full', tone: 'muted', count: 0 });
    expect(seatsChip(-1)).toEqual({ key: 'full', tone: 'muted', count: 0 });
    expect(seatsChip(null)).toBeNull();
    expect(seatsChip(undefined)).toBeNull();
  });
});

describe('SeatsChipComponent', () => {
  function render(free: number | null) {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(SeatsChipComponent);
    fixture.componentRef.setInput('free', free);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('o texto e o ton', () => {
    const el = render(5);
    const tag = el.querySelector('furar-tag');
    expect(tag?.textContent?.trim()).toBe('5 prazas libres');
    expect(tag?.getAttribute('data-tone')).toBe('success');
    expect(tag?.getAttribute('data-seats')).toBe('free');
  });

  it('completa, en gris', () => {
    const tag = render(0).querySelector('furar-tag');
    expect(tag?.textContent?.trim()).toBe('Completa');
    expect(tag?.getAttribute('data-tone')).toBe('muted');
  });

  it('sen límite, nada', () => {
    expect(render(null).querySelector('furar-tag')).toBeNull();
  });
});
