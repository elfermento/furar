// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { fakeSession, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { BookingKindComponent } from './booking-kind.component';

describe('«Tipo de reserva» en Nova reserva', () => {
  function render(session = fakeSession(), tipo: string | null = null) {
    TestBed.configureTestingModule({
      providers: [
        ...provideTestApi(session),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(tipo ? { tipo } : {}) } } },
      ],
    });
    TestBed.inject(CurrentMunicipality).set(municipality());
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(BookingKindComponent);
    fixture.detectChanges();
    return { fixture, el: fixture.nativeElement as HTMLElement, navigate };
  }

  it('a xestión: «Unha vez · Tempada»; escoller Tempada pono no URL; axe', async () => {
    const { fixture, el, navigate } = render();
    const season = el.querySelector<HTMLButtonElement>('[data-kind="season"]')!;
    expect(el.querySelector('[data-kind="once"]')?.getAttribute('aria-checked')).toBe('true');
    // Se o segmentado non cabe ao carón do texto, baixa á esquerda.
    expect(el.querySelector('[data-book-for-kind]')?.getAttribute('data-wrap')).toBe('start');
    await expectNoAxeViolations(el);
    season.click();
    fixture.detectChanges();
    expect(fixture.componentInstance.kind()).toBe('season');
    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { tipo: 'tempada' } }));
    el.querySelector<HTMLButtonElement>('[data-kind="once"]')!.click();
    expect(navigate).toHaveBeenLastCalledWith([], expect.objectContaining({ queryParams: { tipo: null } }));
  });

  it('recargar con `?tipo=tempada` abre a tempada; cambiando unha reserva ou en Recepción, sen fila', () => {
    expect(render(fakeSession(), 'tempada').fixture.componentInstance.kind()).toBe('season');
    TestBed.resetTestingModule();
    const { el } = render(fakeSession({ staff: 'naron' }));
    expect(el.querySelector('[data-book-for-kind]')).toBeNull();
    TestBed.resetTestingModule();
    const manager = render();
    manager.fixture.componentRef.setInput('changing', true);
    manager.fixture.detectChanges();
    expect(manager.el.querySelector('[data-book-for-kind]')).toBeNull();
  });
});
