// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { PeekSheetComponent } from './peek-sheet.component';

@Component({
  imports: [PeekSheetComponent],
  template: `<furar-peek-sheet label="60 instalacións" [(expanded)]="open"><a href="/x">Unha</a></furar-peek-sheet>`,
})
class HostComponent {
  readonly open = signal(false);
}

describe('PeekSheetComponent', () => {
  function render() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const sheet = el.querySelector<HTMLElement>('.f-peek')!;
    const head = el.querySelector<HTMLButtonElement>('[data-peek-toggle]')!;
    const settle = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
    };
    return { fixture, sheet, head, settle };
  }
  const pointer = (target: Element, type: string, clientY: number) =>
    target.dispatchEvent(new MouseEvent(type, { bubbles: true, clientY, button: 0 }));

  it('pregada: só o título, co contido inerte; picar estende e volve pregar; pasa axe', async () => {
    const { fixture, sheet, head, settle } = render();
    expect(sheet.getAttribute('aria-label')).toBe('60 instalacións');
    expect(head.textContent?.trim()).toBe('60 instalacións');
    expect(head.getAttribute('aria-expanded')).toBe('false');
    expect(sheet.querySelector('.f-peek__body')?.hasAttribute('inert')).toBe(true);
    head.click();
    await settle();
    expect(fixture.componentInstance.open()).toBe(true);
    expect(sheet.hasAttribute('data-expanded')).toBe(true);
    expect(sheet.querySelector('.f-peek__body')?.hasAttribute('inert')).toBe(false);
    await expectNoAxeViolations(fixture.nativeElement);
    head.click();
    await settle();
    expect(fixture.componentInstance.open()).toBe(false);
  });

  it('arrastrar cara arriba estende, cara abaixo prega; o «click» do arrastre non conmuta outra vez', async () => {
    const { fixture, sheet, head, settle } = render();
    pointer(head, 'pointerdown', 500);
    pointer(head, 'pointermove', 470);
    expect(sheet.hasAttribute('data-dragging')).toBe(true);
    expect(sheet.style.transform).toMatch(/^translateY\(/);
    pointer(head, 'pointermove', 400);
    pointer(head, 'pointerup', 400);
    // O «click» que segue ao arrastre (co rato ou o dedo, `detail` 1).
    head.dispatchEvent(new MouseEvent('click', { bubbles: true, detail: 1 }));
    await settle();
    expect(sheet.hasAttribute('data-dragging')).toBe(false);
    expect(sheet.style.transform).toBe('');
    expect(fixture.componentInstance.open()).toBe(true);

    pointer(head, 'pointerdown', 100);
    pointer(head, 'pointermove', 160);
    pointer(head, 'pointermove', 400);
    pointer(head, 'pointerup', 400);
    await settle();
    expect(fixture.componentInstance.open()).toBe(false);
  });

  it('Esc e o botón «atrás» do navegador préganna', async () => {
    const { fixture, sheet, head, settle } = render();
    head.click();
    await settle();
    const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true });
    const outer = vi.fn();
    fixture.nativeElement.addEventListener('keydown', outer);
    sheet.dispatchEvent(escape);
    await settle();
    expect(fixture.componentInstance.open()).toBe(false);
    // Non chega a quen a contén (o mapa pecharíase).
    expect(outer).not.toHaveBeenCalled();

    head.click();
    await settle();
    expect(history.state?.furarPeek).toBe(true);
    window.dispatchEvent(new PopStateEvent('popstate'));
    await settle();
    expect(fixture.componentInstance.open()).toBe(false);
  });
});
