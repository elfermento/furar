// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { provideTestI18n } from '../../../testing/i18n';
import { myRegistration, publicActivity } from './public-event.fixture';
import { RegistrationBandComponent, bandState } from './registration-band.component';

describe('RegistrationBandComponent: a inscrición na banda de estado', () => {
  afterEach(() => vi.useRealTimers());

  it('o estado de cada inscrición', () => {
    expect(bandState({ status: 'CONFIRMED', seatCovered: false })).toBe('joined');
    expect(bandState({ status: 'PENDING', seatCovered: false })).toBe('pending');
    expect(bandState({ status: 'REJECTED', seatCovered: false })).toBe('rejected');
    expect(bandState({ status: 'WAITING', seatCovered: false })).toBe('waiting');
    expect(bandState({ status: 'WAITING', seatCovered: true })).toBe('covered');
    expect(bandState({ status: 'NOTIFIED', seatCovered: false })).toBe('offer');
    expect(bandState({ seatCovered: false })).toBeNull();
  });

  it('avisada: ao chegar a hora só para ela, a frase cambia sen recargar', async () => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] });
    vi.setSystemTime(new Date('2026-11-16T17:00:00Z'));
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(RegistrationBandComponent);
    fixture.componentRef.setInput(
      'item',
      myRegistration({ status: 'NOTIFIED', exclusiveUntil: '2026-11-16T17:42:00Z', event: publicActivity() }),
    );
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const line = () => el.querySelector('[data-band-line]')?.textContent?.trim();
    expect(line()).toBe('Ata as 18:42 só o sabes ti.');
    vi.advanceTimersByTime(43 * 60_000);
    fixture.detectChanges();
    expect(line()).toBe('Xa avisamos tamén a máis persoas: é túa se a confirmas mentres siga libre.');
  });

  it('un aviso que segue mañá di o día', () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-11-16T21:30:00Z'));
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(RegistrationBandComponent);
    fixture.componentRef.setInput(
      'item',
      myRegistration({ status: 'NOTIFIED', exclusiveUntil: '2026-11-17T07:30:00Z', event: publicActivity() }),
    );
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).querySelector('[data-band-line]')?.textContent?.trim()).toBe(
      'Ata o martes, 17 de novembro ás 08:30 só o sabes ti.',
    );
  });
});
