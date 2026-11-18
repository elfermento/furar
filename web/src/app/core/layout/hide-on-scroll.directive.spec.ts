// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { BAR_HIDDEN_ATTR, HideOnScrollDirective } from './hide-on-scroll.directive';

@Component({
  imports: [HideOnScrollDirective],
  template: `
    <header furarHideOnScroll>
      <a href="/">Inicio</a>
      <button type="button" [attr.aria-expanded]="open()">Menú</button>
    </header>
  `,
})
class HostComponent {
  readonly open = signal(false);
}

describe('HideOnScrollDirective', () => {
  let frames: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    // Un fotograma = unha volta do bucle (o planificador de Angular tamén os usa: nunca síncrono).
    frames = vi
      .spyOn(window, 'requestAnimationFrame')
      .mockImplementation((cb) => window.setTimeout(() => cb(0), 0) as unknown as number);
  });

  afterEach(() => {
    frames.mockRestore();
    Object.defineProperty(window, 'scrollY', { value: 0, configurable: true });
  });

  async function setup() {
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const bar = (fixture.nativeElement as HTMLElement).querySelector('header')!;
    const scrollTo = async (y: number) => {
      Object.defineProperty(window, 'scrollY', { value: y, configurable: true });
      window.dispatchEvent(new Event('scroll'));
      await new Promise((resolve) => setTimeout(resolve, 5));
      await fixture.whenStable();
    };
    return { fixture, bar, scrollTo, root: document.documentElement };
  }

  it('escóndese ao baixar, volve ao subir e marca <html> para o que vai pegado debaixo', async () => {
    const { bar, scrollTo, root } = await setup();
    await scrollTo(200);
    expect(bar.hasAttribute('data-hidden')).toBe(true);
    expect(root.hasAttribute(BAR_HIDDEN_ATTR)).toBe(true);
    await scrollTo(190);
    expect(bar.hasAttribute('data-hidden')).toBe(false);
    expect(root.hasAttribute(BAR_HIDDEN_ATTR)).toBe(false);
  });

  it('non se esconde cun menú aberto nin co foco dentro', async () => {
    const { fixture, bar, scrollTo } = await setup();
    fixture.componentInstance.open.set(true);
    await fixture.whenStable();
    await scrollTo(300);
    expect(bar.hasAttribute('data-hidden')).toBe(false);

    fixture.componentInstance.open.set(false);
    await fixture.whenStable();
    await scrollTo(600);
    expect(bar.hasAttribute('data-hidden')).toBe(true);
    bar.querySelector('a')!.dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    await fixture.whenStable();
    expect(bar.hasAttribute('data-hidden')).toBe(false);
    await scrollTo(900);
    expect(bar.hasAttribute('data-hidden')).toBe(false);
    bar.querySelector('a')!.dispatchEvent(new FocusEvent('focusout', { bubbles: true, relatedTarget: null }));
    await fixture.whenStable();
    expect(bar.hasAttribute('data-hidden')).toBe(true);
  });

  it('ao desmontarse, deixa <html> sen a marca', async () => {
    const { fixture, scrollTo, root } = await setup();
    await scrollTo(200);
    expect(root.hasAttribute(BAR_HIDDEN_ATTR)).toBe(true);
    fixture.destroy();
    expect(root.hasAttribute(BAR_HIDDEN_ATTR)).toBe(false);
  });
});
