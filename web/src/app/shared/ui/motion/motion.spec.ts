// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { fadeIn, growFrom, motionAllowed, shrinkInto, slideFrom, slideOut } from './motion';

describe('Animacións lista → detalle', () => {
  const view = (reduce: boolean) => ({ matchMedia: () => ({ matches: reduce }) }) as unknown as Window;
  function element(left: number) {
    const el = document.createElement('section');
    const animation = { onfinish: null as (() => void) | null };
    el.animate = vi.fn(() => animation as unknown as Animation);
    el.getBoundingClientRect = () => ({ left }) as DOMRect;
    return { el, animation };
  }

  it('sen Web Animations ou con movemento reducido, nada se move e o peche remata xa', () => {
    const plain = document.createElement('div');
    Object.defineProperty(plain, 'animate', { value: undefined });
    expect(motionAllowed(view(false), plain)).toBe(false);
    const { el } = element(0);
    expect(motionAllowed(view(true), el)).toBe(false);
    expect(motionAllowed(null, el)).toBe(true);
    const done = vi.fn();
    slideOut(view(true), el, done);
    slideOut(view(false), undefined, done);
    expect(done).toHaveBeenCalledTimes(2);
    expect(el.animate).not.toHaveBeenCalled();
  });

  it('a sección entra desde onde estaba, só cun 35 % do camiño; o detalle sae e despois remata', () => {
    const { el, animation } = element(100);
    slideFrom(view(false), el, { left: 300 } as DOMRect);
    expect(el.animate).toHaveBeenCalledWith(
      [
        { transform: 'translateX(70px)', opacity: 0.6 },
        { transform: 'none', opacity: 1 },
      ],
      expect.objectContaining({ duration: 320 }),
    );
    slideFrom(view(false), el, { left: 100 } as DOMRect);
    expect(el.animate).toHaveBeenCalledTimes(1);
    fadeIn(view(false), el);
    fadeIn(view(true), el);
    expect(el.animate).toHaveBeenCalledTimes(2);
    const done = vi.fn();
    slideOut(view(false), el, done);
    expect(done).not.toHaveBeenCalled();
    animation.onfinish?.();
    expect(done).toHaveBeenCalled();
  });

  it('o panel medra desde o botón e encolle de volta a el; con movemento reducido, nada', () => {
    const rect = (left: number, top: number, width: number, height: number) =>
      ({ left, top, width, height }) as DOMRect;
    const panel = document.createElement('aside');
    const animation = { onfinish: null as (() => void) | null };
    panel.animate = vi.fn(() => animation as unknown as Animation);
    panel.getBoundingClientRect = () => rect(500, 100, 300, 600);
    growFrom(view(false), panel, rect(20, 400, 150, 60));
    const frames = (panel.animate as ReturnType<typeof vi.fn>).mock.calls[0]?.[0] as Keyframe[];
    expect(frames[0]?.['transform']).toBe('translate(-480px, 300px) scale(0.5, 0.1)');
    expect(frames[1]?.['transform']).toBe('none');

    const done = vi.fn();
    shrinkInto(view(false), panel, () => rect(20, 400, 150, 60), done);
    expect(done).not.toHaveBeenCalled();
    animation.onfinish?.();
    expect(done).toHaveBeenCalledTimes(1);

    const still = document.createElement('aside');
    still.animate = vi.fn();
    growFrom(view(true), still, rect(0, 0, 10, 10));
    shrinkInto(view(true), still, () => rect(0, 0, 10, 10), done);
    expect(still.animate).not.toHaveBeenCalled();
    expect(done).toHaveBeenCalledTimes(2);
  });
});
