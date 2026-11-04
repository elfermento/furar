// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal, viewChild } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import type { ChartEngine, ChartInstance } from './chart-engine';
import { CHART_ENGINE } from './chart-engine';
import type { ChartOption, ChartSpec } from './chart-options';
import { lineArea } from './chart-options';
import { ChartComponent, chartImage } from './chart.component';

/** Motor falso: garda o que lle pide furar-chart en vez de debuxar. */
class FakeInstance implements ChartInstance {
  readonly options: ChartOption[] = [];
  resized = 0;
  disposed = false;
  readonly images: unknown[] = [];

  constructor(readonly element: HTMLElement) {}

  setOption(option: ChartOption): void {
    this.options.push(option);
  }
  resize(): void {
    this.resized++;
  }
  dispose(): void {
    this.disposed = true;
  }
  getDataURL(settings: unknown): string {
    this.images.push(settings);
    return 'data:image/png;base64,AAAA';
  }
}

const line: ChartSpec = (t) =>
  lineArea(t, { categories: ['xan', 'feb'], series: { name: 'Reservas', values: [3, 5] } });

@Component({
  imports: [ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (shown()) {
      <furar-chart [chart]="spec()" [height]="height()" />
    }
  `,
})
class HostComponent {
  readonly shown = signal(true);
  readonly spec = signal<ChartSpec>(line);
  readonly height = signal<number | undefined>(undefined);
  readonly chart = viewChild(ChartComponent);
}

@Component({
  imports: [ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<furar-chart [chart]="spec" /><furar-chart [chart]="spec" />`,
})
class TwoChartsComponent {
  readonly spec = line;
}

describe('furar-chart', () => {
  let instances: FakeInstance[];
  let loads: number;
  let failing: boolean;
  const engine: ChartEngine = {
    init: (element) => {
      const instance = new FakeInstance(element);
      instances.push(instance);
      return instance;
    },
  };
  const root = document.documentElement.style;
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    instances = [];
    loads = 0;
    failing = false;
  });

  afterEach(() => {
    root.removeProperty('--f-primary');
    root.removeProperty('overflow');
    window.matchMedia = originalMatchMedia;
  });

  async function render() {
    TestBed.configureTestingModule({
      providers: [
        {
          provide: CHART_ENGINE,
          useValue: () => {
            loads++;
            return failing ? Promise.reject(new Error('sen rede')) : Promise.resolve(engine);
          },
        },
      ],
    });
    const fixture = TestBed.createComponent(HostComponent);
    await settle(fixture, () => instances[0]?.options.length === 1);
    return fixture;
  }

  /** O motor chega por unha promesa: agarda a que o gráfico se debuxe (ou a que pase `done`). */
  async function settle(fixture: ComponentFixture<HostComponent>, done: () => boolean) {
    for (let i = 0; i < 100 && !done(); i++) {
      await fixture.whenStable();
      await new Promise((resolve) => setTimeout(resolve, 5));
    }
    await fixture.whenStable();
  }

  const last = () => instances[0]?.options.at(-1) as ChartOption & { series: { color: string }[] };

  it('carga o motor ao aparecer e debuxa nun canvas aria-hidden coas cores dos tokens', async () => {
    const fixture = await render();
    expect(loads).toBe(1);
    expect(instances).toHaveLength(1);
    const element = instances[0]?.element as HTMLElement;
    expect(element.classList).toContain('f-chart__canvas');
    expect(element.getAttribute('aria-hidden')).toBe('true');
    expect(element.closest('furar-chart')?.getAttribute('data-size')).toBe('md');
    expect(last().series[0]?.color).toBe('#0b5c8e');
    expect(last()['animation']).toBe(true);
    expect((fixture.nativeElement as HTMLElement).querySelector('[style]')).toBeNull();
  });

  it('redebuxa cando cambia o tema en <html> e non con outros estilos', async () => {
    const fixture = await render();
    root.setProperty('overflow', 'hidden');
    await settle(fixture, () => false);
    expect(instances[0]?.options).toHaveLength(1);
    root.setProperty('--f-primary', '#7a1f2b');
    await settle(fixture, () => instances[0]?.options.length === 2);
    expect(instances[0]?.options).toHaveLength(2);
    expect(last().series[0]?.color).toBe('#7a1f2b');
  });

  it('varios gráficos comparten un só observador do tema e redebuxan todos', async () => {
    const created = vi.spyOn(window, 'MutationObserver');
    TestBed.configureTestingModule({ providers: [{ provide: CHART_ENGINE, useValue: () => Promise.resolve(engine) }] });
    const fixture = TestBed.createComponent(TwoChartsComponent);
    await settle(fixture as unknown as ComponentFixture<HostComponent>, () => instances.length === 2);
    await settle(fixture as unknown as ComponentFixture<HostComponent>, () =>
      instances.every((i) => i.options.length === 1),
    );
    expect(created).toHaveBeenCalledTimes(1);
    root.setProperty('--f-primary', '#7a1f2b');
    await settle(fixture as unknown as ComponentFixture<HostComponent>, () =>
      instances.every((i) => i.options.length === 2),
    );
    expect(instances.map((i) => i.options.length)).toEqual([2, 2]);
    created.mockRestore();
  });

  it('redebuxa cando cambian as opcións', async () => {
    const fixture = await render();
    fixture.componentInstance.spec.set((t) =>
      lineArea(t, { categories: ['xan'], series: { name: 'Persoas', values: [9] } }),
    );
    await settle(fixture, () => instances[0]?.options.length === 2);
    expect((last().series[0] as unknown as { name: string }).name).toBe('Persoas');
  });

  it('sen animación con prefers-reduced-motion', async () => {
    window.matchMedia = ((query: string) => ({
      matches: query.includes('reduce'),
      media: query,
    })) as unknown as typeof window.matchMedia;
    await render();
    expect(last()['animation']).toBe(false);
  });

  it('libera o gráfico (dispose) ao destruírse', async () => {
    const fixture = await render();
    fixture.componentInstance.shown.set(false);
    await fixture.whenStable();
    expect(instances[0]?.disposed).toBe(true);
  });

  it('image() dá un PNG a dobre resolución sobre a superficie', async () => {
    const fixture = await render();
    expect(fixture.componentInstance.chart()?.image()).toBe('data:image/png;base64,AAAA');
    expect(instances[0]?.images[0]).toEqual({ type: 'png', pixelRatio: 2, backgroundColor: '#ffffff' });
  });

  it('image("light") nun tema escuro: debuxa un momento en claro, fai a imaxe e volve ao escuro', async () => {
    const fixture = await render();
    root.setProperty('--f-surface', '#121212');
    root.setProperty('--f-primary', '#7cc4ff');
    await settle(fixture, () => instances[0]?.options.length === 2);
    const chart = (fixture.nativeElement as HTMLElement).querySelector('furar-chart')!;
    expect(chartImage(chart, 'light')).toBe('data:image/png;base64,AAAA');
    expect(instances[0]?.images.at(-1)).toEqual({ type: 'png', pixelRatio: 2, backgroundColor: '#ffffff' });
    const [light, back] = instances[0]!.options.slice(-2) as (ChartOption & { series: { color: string }[] })[];
    // O primary claro do tema escuro non chega a 3:1 sobre branco: o do tema por defecto.
    expect(light!.series[0]?.color).toBe('#0b5c8e');
    expect(light!['animation']).toBe(false);
    expect(back!.series[0]?.color).toBe('#7cc4ff');
    expect(chartImage(document.createElement('div'))).toBeNull();
    root.removeProperty('--f-surface');
  });

  it('o alto en rem vai por CSSOM nunha variable, sen atributo style no modelo', async () => {
    const fixture = await render();
    const host = (fixture.nativeElement as HTMLElement).querySelector('furar-chart') as HTMLElement;
    fixture.componentInstance.height.set(20);
    await fixture.whenStable();
    expect(host.style.getPropertyValue('--f-chart-height')).toBe('20rem');
    fixture.componentInstance.height.set(undefined);
    await fixture.whenStable();
    expect(host.style.getPropertyValue('--f-chart-height')).toBe('');
  });

  it('se o motor non carga, avisa na consola e non rompe a páxina', async () => {
    failing = true;
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    await render();
    expect(instances).toHaveLength(0);
    expect(error).toHaveBeenCalled();
    error.mockRestore();
  });
});
