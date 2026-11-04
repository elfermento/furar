// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideTestI18n } from '../../../../testing/i18n';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { CarouselDrag } from './carousel-drag';
import { CarouselComponent, CarouselItemDirective } from './carousel.component';

@Component({
  imports: [CarouselComponent, CarouselItemDirective],
  template: `
    <furar-carousel
      label="Fotos"
      hint="A primeira é a portada"
      [reorderable]="reorderable()"
      [items]="items()"
      [key]="key"
      (reordered)="order.set($event)"
    >
      <ng-template furarCarouselItem let-item let-i="index" let-hint="hintId">
        <button type="button" [attr.aria-describedby]="hint">{{ item }} {{ i + 1 }}</button>
      </ng-template>
    </furar-carousel>
  `,
})
class HostComponent {
  readonly items = signal(['a', 'b', 'c']);
  readonly reorderable = signal(true);
  readonly order = signal<string[]>([]);
  readonly key = (item: string) => item;
}

describe('CarouselComponent', () => {
  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    return { fixture, el, buttons: () => [...el.querySelectorAll<HTMLButtonElement>('li button')] };
  }

  it('lista con nome, un elemento por entrada, frechas desactivadas nos extremos e axe', async () => {
    const { el, buttons } = await render();
    expect(el.querySelector('ul')?.getAttribute('aria-label')).toBe('Fotos');
    expect(buttons().map((b) => b.textContent?.trim())).toEqual(['a 1', 'b 2', 'c 3']);
    const arrows = [...el.querySelectorAll<HTMLButtonElement>('.f-carousel__arrow')];
    expect(arrows.map((a) => a.disabled)).toEqual([true, true]);
    expect(arrows.map((a) => a.getAttribute('aria-label'))).toEqual(['Anteriores', 'Seguintes']);
    await expectNoAxeViolations(el);
  });

  it('Alt + frecha move o enfocado, emite a orde e anúnciao; sen reorderable non', async () => {
    const { fixture, el, buttons } = await render();
    const host = fixture.componentInstance;
    buttons()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', altKey: true, bubbles: true }));
    expect(host.order()).toEqual(['b', 'a', 'c']);
    await fixture.whenStable();
    expect(el.querySelector('[role="status"]')?.textContent).toContain('posición 2 de 3');
    buttons()[0].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowLeft', altKey: true, bubbles: true }));
    expect(host.order()).toEqual(['b', 'a', 'c']);
    host.reorderable.set(false);
    host.order.set([]);
    await fixture.whenStable();
    buttons()[1].dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', altKey: true, bubbles: true }));
    expect(host.order()).toEqual([]);
  });

  it('o aviso sae co foco ou o rato, fica ao pasar por riba e Esc péchao', async () => {
    const { fixture, el, buttons } = await render();
    const tip = () => el.querySelector<HTMLElement>('[role="tooltip"]')!;
    expect(tip().hidden).toBe(true);
    expect(buttons()[0].getAttribute('aria-describedby')).toBe(tip().id);
    buttons()[0].dispatchEvent(new FocusEvent('focusin', { bubbles: true }));
    await fixture.whenStable();
    expect(tip().hidden).toBe(false);
    el.querySelector('furar-carousel')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await fixture.whenStable();
    expect(tip().hidden).toBe(true);
    el.querySelector('furar-carousel')!.dispatchEvent(new MouseEvent('mouseenter'));
    await fixture.whenStable();
    expect(tip().hidden).toBe(false);
  });
});

describe('CarouselDrag', () => {
  function strip(keys: string[]): HTMLElement {
    const ul = document.createElement('ul');
    keys.forEach((key, i) => {
      const li = document.createElement('li');
      li.className = 'f-carousel__item';
      li.dataset['key'] = key;
      li.getBoundingClientRect = () => ({ left: i * 100, width: 100, right: i * 100 + 100 }) as DOMRect;
      ul.append(li);
    });
    ul.getBoundingClientRect = () => ({ left: 0, right: 300 }) as DOMRect;
    return ul;
  }

  it('arrastrar coloca o elemento onde se solta e só emite se cambiou a orde', () => {
    vi.useFakeTimers();
    const keys = ['a', 'b', 'c'];
    const ul = strip(keys);
    const preview = signal<readonly string[] | null>(null);
    const dropped = vi.fn();
    const drag = new CarouselDrag({
      view: null,
      strip: () => ul,
      keys: () => keys,
      preview,
      afterRender: (work) => work(),
      reducedMotion: () => true,
      dropped,
    });
    drag.start({ target: ul.children[0], clientX: 50, dataTransfer: null } as unknown as DragEvent);
    vi.runAllTimers();
    expect(preview()).toEqual(['a', 'b', 'c']);
    drag.placeAt(260);
    expect(preview()).toEqual(['b', 'c', 'a']);
    drag.end();
    expect(preview()).toBeNull();
    expect(dropped).toHaveBeenCalledWith(['b', 'c', 'a'], 'a');

    dropped.mockClear();
    drag.start({ target: ul.children[1], clientX: 150, dataTransfer: null } as unknown as DragEvent);
    vi.runAllTimers();
    drag.end();
    expect(dropped).not.toHaveBeenCalled();
    vi.useRealTimers();
  });

  it('sen arrastre activo ignora dragover, drop e end; fóra dun elemento non empeza', () => {
    const ul = strip(['a', 'b']);
    const preview = signal<readonly string[] | null>(null);
    const dropped = vi.fn();
    const drag = new CarouselDrag({
      view: null,
      strip: () => ul,
      keys: () => ['a', 'b'],
      preview,
      afterRender: (work) => work(),
      reducedMotion: () => true,
      dropped,
    });
    const prevent = vi.fn();
    drag.start({ target: ul, clientX: 10, dataTransfer: null } as unknown as DragEvent);
    expect(drag.active).toBe(false);
    drag.over({ clientX: 10, preventDefault: prevent } as unknown as DragEvent);
    drag.drop({ preventDefault: prevent } as unknown as DragEvent);
    drag.end();
    drag.placeAt(10);
    expect(prevent).not.toHaveBeenCalled();
    expect(preview()).toBeNull();
    expect(dropped).not.toHaveBeenCalled();
  });

  it('dragover dentro da tira recoloca, drop acepta e, preto do bordo, a tira desprázase soa con FLIP', () => {
    vi.useFakeTimers();
    const keys = ['a', 'b', 'c'];
    const ul = strip(keys);
    ul.scrollLeft = 0;
    const preview = signal<readonly string[] | null>(null);
    const frames: (() => void)[] = [];
    const view = {
      requestAnimationFrame: (cb: () => void) => frames.push(cb),
      cancelAnimationFrame: vi.fn(),
    } as unknown as Window;
    const drag = new CarouselDrag({
      view,
      strip: () => ul,
      keys: () => keys,
      preview,
      afterRender: (work) => work(),
      reducedMotion: () => false,
      dropped: vi.fn(),
    });
    const data = { setData: vi.fn(), effectAllowed: '' };
    drag.start({ target: ul.children[0], clientX: 150, dataTransfer: data } as unknown as DragEvent);
    expect(data.effectAllowed).toBe('move');
    vi.runAllTimers();
    const prevent = vi.fn();
    drag.over({ clientX: 260, preventDefault: prevent } as unknown as DragEvent);
    expect(prevent).toHaveBeenCalled();
    expect(preview()).toEqual(['b', 'c', 'a']);
    drag.over({ clientX: 400, preventDefault: prevent } as unknown as DragEvent);
    frames.shift()?.();
    expect(ul.scrollLeft).toBeGreaterThan(0);
    drag.over({ clientX: -50, preventDefault: prevent } as unknown as DragEvent);
    frames.shift()?.();
    drag.drop({ preventDefault: prevent } as unknown as DragEvent);
    drag.end();
    expect(view.cancelAnimationFrame).toHaveBeenCalled();
    vi.useRealTimers();
  });
});
