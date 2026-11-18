// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Signal } from '@angular/core';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { countdownClock, formatCountdown } from './countdown';

const START = new Date('2026-10-28T10:00:00Z').getTime();

@Component({
  selector: 'furar-countdown-host',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '',
})
class HostComponent {
  readonly deadlines = signal<number[]>([]);
  readonly now: Signal<number> = countdownClock(() => this.deadlines());
}

describe('countdownClock e formatCountdown', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] });
    vi.setSystemTime(START);
  });
  afterEach(() => vi.useRealTimers());

  async function host() {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return fixture;
  }

  it('m:ss cos segundos cara arriba e sen negativos', () => {
    expect(formatCountdown(105_000)).toBe('1:45');
    expect(formatCountdown(104_001)).toBe('1:45');
    expect(formatCountdown(5_000)).toBe('0:05');
    expect(formatCountdown(120_000)).toBe('2:00');
    expect(formatCountdown(0)).toBe('0:00');
    expect(formatCountdown(-3_000)).toBe('0:00');
  });

  it('sen prazos no futuro non hai temporizador', async () => {
    const fixture = await host();
    fixture.componentInstance.deadlines.set([START - 1_000]);
    TestBed.tick();
    expect(vi.getTimerCount()).toBe(0);
    vi.advanceTimersByTime(5_000);
    expect(fixture.componentInstance.now()).toBe(START);
  });

  it('avanza cada segundo ata o prazo, párase e volve arrancar cun prazo novo', async () => {
    const fixture = await host();
    const { componentInstance: c } = fixture;
    c.deadlines.set([START + 2_500]);
    TestBed.tick();
    expect(vi.getTimerCount()).toBe(1);

    vi.advanceTimersByTime(1_000);
    expect(c.now()).toBe(START + 1_000);
    vi.advanceTimersByTime(2_000);
    expect(c.now()).toBe(START + 3_000);
    TestBed.tick();
    expect(vi.getTimerCount()).toBe(0);

    c.deadlines.set([START + 60_000]);
    TestBed.tick();
    expect(vi.getTimerCount()).toBe(1);
    vi.advanceTimersByTime(1_000);
    expect(c.now()).toBe(START + 4_000);
  });

  it('ao destruír o compoñente non queda ningún intervalo', async () => {
    const fixture = await host();
    fixture.componentInstance.deadlines.set([START + 60_000]);
    TestBed.tick();
    expect(vi.getTimerCount()).toBe(1);
    fixture.destroy();
    expect(vi.getTimerCount()).toBe(0);
  });
});
