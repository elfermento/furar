// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { TOAST_DURATION_MS, ToastComponent } from './toast.component';
import { ToastService } from './toast.service';

describe('furar-toast', () => {
  afterEach(() => vi.useRealTimers());

  async function render() {
    vi.useFakeTimers();
    TestBed.configureTestingModule({ providers: provideTestI18n() });
    const fixture = TestBed.createComponent(ToastComponent);
    document.body.append(fixture.nativeElement);
    const toasts = TestBed.inject(ToastService);
    const el = fixture.nativeElement as HTMLElement;
    const stable = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
    };
    await stable();
    return { el, toasts, stable };
  }

  it('rexión role="status" sempre presente; un só aviso, o novo substitúe ao vello, sen roubar o foco', async () => {
    const { el, toasts, stable } = await render();
    expect(el.getAttribute('role')).toBe('status');
    expect(el.getAttribute('aria-live')).toBe('polite');
    expect(el.querySelector('[data-toast]')).toBeNull();
    const before = document.activeElement;

    toasts.show('settings.saved');
    await stable();
    expect(el.querySelector('[data-toast]')?.textContent).toContain('Cambios gardados');
    expect(el.querySelector('.sr-only')?.textContent).toContain('Feito:');
    toasts.show('platform.created', { ofName: 'da Coruña' });
    await stable();
    expect(el.querySelectorAll('[data-toast]')).toHaveLength(1);
    expect(el.querySelector('[data-toast]')?.textContent).toContain('Concello da Coruña creado');
    expect(document.activeElement).toBe(before);
    expect(el.querySelector('[data-toast-close]')?.getAttribute('aria-label')).toBe('Pechar o aviso');
    vi.useRealTimers();
    await expectNoAxeViolations(el);
  });

  it('desaparece aos 6 s; o rato enriba para a conta e, ao saír, segue co que quedaba', async () => {
    const { el, toasts, stable } = await render();
    toasts.show('settings.saved');
    await stable();
    vi.advanceTimersByTime(TOAST_DURATION_MS - 1000);
    const toast = el.querySelector<HTMLElement>('[data-toast]')!;
    toast.dispatchEvent(new MouseEvent('mouseenter'));
    vi.advanceTimersByTime(TOAST_DURATION_MS * 2);
    await stable();
    expect(toasts.current()).not.toBeNull();
    toast.dispatchEvent(new MouseEvent('mouseleave'));
    vi.advanceTimersByTime(999);
    expect(toasts.current()).not.toBeNull();
    vi.advanceTimersByTime(1);
    await stable();
    expect(toasts.current()).toBeNull();
    expect(el.querySelector('[data-toast]')).toBeNull();
  });

  it('o foco dentro tamén para a conta; ✕ e Esc pechan', async () => {
    const { el, toasts, stable } = await render();
    toasts.show('settings.saved');
    await stable();
    const close = el.querySelector<HTMLButtonElement>('[data-toast-close]')!;
    close.focus();
    vi.advanceTimersByTime(TOAST_DURATION_MS * 2);
    expect(toasts.current()).not.toBeNull();
    close.click();
    await stable();
    expect(toasts.current()).toBeNull();

    toasts.show('settings.saved');
    await stable();
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await stable();
    expect(toasts.current()).toBeNull();
  });

  it('un temporizador vello non pecha o aviso novo', async () => {
    const { toasts, stable } = await render();
    toasts.show('settings.saved');
    await stable();
    vi.advanceTimersByTime(TOAST_DURATION_MS - 100);
    toasts.show('platform.created', { ofName: 'de Narón' });
    await stable();
    vi.advanceTimersByTime(200);
    expect(toasts.current()?.key).toBe('platform.created');
  });
});
