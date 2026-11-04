// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DialogRef } from '@angular/cdk/dialog';
import { OverlayContainer } from '@angular/cdk/overlay';
import { ApplicationRef, ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { ButtonDirective } from '../button/button.directive';
import { DialogComponent } from './dialog.component';
import { DialogService } from './dialog.service';

@Component({
  imports: [DialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog [heading]="'Editar horario'" [description]="'Tempada de inverno'" [busy]="busy()">
      <label for="dlg-name">{{ 'Nome' }}</label>
      <input id="dlg-name" />
      <button dialogActions type="button" id="dlg-save" (click)="ref.close('saved')">{{ 'Gardar' }}</button>
    </furar-dialog>
  `,
})
class SampleDialog {
  static readonly busy = signal(false);
  protected readonly busy = SampleDialog.busy;
  protected readonly ref = inject(DialogRef);
}

/** Tecla con `key` e `keyCode` (o CDK aínda le keyCode). */
function key(target: Element, name: string, keyCode: number): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true });
  Object.defineProperty(event, 'keyCode', { get: () => keyCode });
  target.dispatchEvent(event);
  return event;
}

const stable = () => TestBed.inject(ApplicationRef).whenStable();

describe('furar-dialog e DialogService.open', () => {
  let opener: HTMLButtonElement;

  beforeEach(() => {
    SampleDialog.busy.set(false);
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    opener = document.createElement('button');
    opener.textContent = 'Abrir';
    document.body.append(opener);
    opener.focus();
  });

  afterEach(() => {
    TestBed.inject(OverlayContainer).ngOnDestroy();
    opener.remove();
  });

  async function open() {
    const ref = TestBed.inject(DialogService).open<string>(SampleDialog);
    await stable();
    const container = document.querySelector<HTMLElement>('furar-dialog-container')!;
    return { ref, container };
  }

  it('é unha capa modal nomeada polo título, co foco no título e sen estilos en liña', async () => {
    const { container } = await open();
    const title = container.querySelector('h2')!;
    expect(container.getAttribute('role')).toBe('dialog');
    expect(container.getAttribute('aria-modal')).toBe('true');
    expect(container.getAttribute('aria-labelledby')).toBe(title.id);
    expect(title.textContent?.trim()).toBe('Editar horario');
    expect(document.activeElement).toBe(title);
    expect(container.querySelector('.f-dialog__text')?.textContent).toContain('Tempada de inverno');
    expect(document.querySelector('.f-dialog-backdrop')).not.toBeNull();
    // O contedor do CDK metería un <style> en liña (CSP style-src 'self'); o propio non.
    const inline = [...document.querySelectorAll('style')].map((style) => style.textContent ?? '');
    expect(inline.filter((css) => css.includes('dialog-container'))).toEqual([]);
    await expectNoAxeViolations(container);
  });

  it('«Pechar» vese sempre, ten nome e pecha devolvendo o foco a quen abriu', async () => {
    const { container } = await open();
    const close = container.querySelector<HTMLButtonElement>('.f-dialog__close')!;
    expect(close.getAttribute('aria-label')).toBe('Pechar');
    close.click();
    await stable();
    expect(document.querySelector('furar-dialog-container')).toBeNull();
    expect(document.activeElement).toBe(opener);
  });

  it('Esc pecha; as accións do pé pechan co seu resultado', async () => {
    const first = await open();
    key(first.container, 'Escape', 27);
    await stable();
    expect(document.querySelector('furar-dialog-container')).toBeNull();

    const second = await open();
    let result: string | undefined;
    second.ref.closed.subscribe((value) => (result = value));
    second.container.querySelector<HTMLButtonElement>('#dlg-save')!.click();
    expect(result).toBe('saved');
  });

  it('con busy, nin Esc nin «Pechar» pechan', async () => {
    SampleDialog.busy.set(true);
    const { ref, container } = await open();
    await stable();
    expect(container.querySelector('furar-dialog')?.getAttribute('aria-busy')).toBe('true');
    expect(ref.disableClose).toBe(true);
    key(container, 'Escape', 27);
    const close = container.querySelector<HTMLButtonElement>('.f-dialog__close')!;
    expect(close.disabled).toBe(true);
    await stable();
    expect(document.querySelector('furar-dialog-container')).not.toBeNull();
    SampleDialog.busy.set(false);
    await stable();
    expect(ref.disableClose).toBe(false);
  });

  it('o corpo é unha rexión enfocable co título de nome só se hai que desprazalo (2.1.1)', async () => {
    // jsdom non mide nin ten ResizeObserver: o alto ponse a man e o observador chámase a man.
    const callbacks: (() => void)[] = [];
    const original = window.ResizeObserver;
    window.ResizeObserver = class {
      constructor(callback: () => void) {
        callbacks.push(callback);
      }
      observe = () => undefined;
      unobserve = () => undefined;
      disconnect = () => undefined;
    } as unknown as typeof ResizeObserver;
    try {
      const { container } = await open();
      const body = container.querySelector<HTMLElement>('.f-dialog__body')!;
      const title = container.querySelector('h2')!;
      expect(body.hasAttribute('role')).toBe(false);
      expect(body.hasAttribute('tabindex')).toBe(false);
      Object.defineProperty(body, 'scrollHeight', { configurable: true, get: () => 900 });
      Object.defineProperty(body, 'clientHeight', { configurable: true, get: () => 300 });
      for (const callback of callbacks) callback();
      await stable();
      expect(body.getAttribute('role')).toBe('region');
      expect(body.getAttribute('tabindex')).toBe('0');
      expect(body.getAttribute('aria-labelledby')).toBe(title.id);
      await expectNoAxeViolations(container);
    } finally {
      window.ResizeObserver = original;
    }
  });

  it('o foco queda atrapado dentro da capa', async () => {
    const { container } = await open();
    const anchors = document.querySelectorAll('.cdk-focus-trap-anchor');
    expect(anchors.length).toBe(2);
    expect(container.contains(document.activeElement)).toBe(true);
  });

  it('con `sheet`, no móbil sae como folla pegada abaixo; no escritorio, centrada como sempre', async () => {
    const original = Object.getOwnPropertyDescriptor(window, 'matchMedia');
    Object.defineProperty(window, 'matchMedia', { configurable: true, value: () => ({ matches: true }) });
    const service = TestBed.inject(DialogService);
    try {
      let ref = service.open(SampleDialog, { sheet: true });
      await stable();
      expect(document.querySelector('.f-dialog-pane')?.classList).toContain('f-dialog-pane--sheet');
      ref.close();
      ref = service.open(SampleDialog);
      await stable();
      expect(document.querySelector('.f-dialog-pane')?.classList).not.toContain('f-dialog-pane--sheet');
      ref.close();
    } finally {
      if (original) Object.defineProperty(window, 'matchMedia', original);
      else Reflect.deleteProperty(window, 'matchMedia');
    }
  });

  describe('transición desde a orixe (View Transition)', () => {
    const doc = document as unknown as Record<string, unknown>;
    const media = Object.getOwnPropertyDescriptor(window, 'matchMedia');
    /** Nome que levaba cada elemento ao comezar cada transición. */
    let names: string[][];
    let start: ReturnType<typeof vi.fn>;

    beforeEach(() => {
      names = [];
      Object.defineProperty(window, 'matchMedia', {
        configurable: true,
        value: (query: string) => ({ matches: false, media: query }),
      });
      start = vi.fn((update: () => Promise<void>) => {
        names.push(
          [...document.querySelectorAll<HTMLElement>('*')]
            .filter((e) => e.style.getPropertyValue('view-transition-name'))
            .map((e) => (e === opener ? 'orixe' : e.classList.contains('f-dialog-pane') ? 'capa' : e.tagName)),
        );
        const done = update();
        return { updateCallbackDone: done, finished: done };
      });
      doc['startViewTransition'] = start;
    });

    afterEach(() => {
      Reflect.deleteProperty(doc, 'startViewTransition');
      if (media) Object.defineProperty(window, 'matchMedia', media);
      else Reflect.deleteProperty(window, 'matchMedia');
    });

    it('sae do botón que se acaba de premer e, ao pechar, volve a el (o foco tamén)', async () => {
      const dialogs = TestBed.inject(DialogService);
      opener.addEventListener('click', () => dialogs.open(SampleDialog), { once: true });
      opener.click();
      await stable();
      const pane = document.querySelector<HTMLElement>('.f-dialog-pane')!;
      // A capa xa está no DOM (foco e resultado como sempre) e visible ao rematar o cambio.
      expect(
        pane.closest<HTMLElement>('.cdk-overlay-connected-position-bounding-box, .cdk-global-overlay-wrapper')?.style
          .opacity,
      ).toBe('');
      document.querySelector<HTMLButtonElement>('.f-dialog__close')!.click();
      await stable();
      expect(start).toHaveBeenCalledTimes(2);
      expect(names).toEqual([['orixe'], ['capa']]);
      expect(document.querySelector('furar-dialog-container')).toBeNull();
      expect(document.activeElement).toBe(opener);
      expect(opener.style.getPropertyValue('view-transition-name')).toBe('');
    });

    it('con `from: null`, sen orixe nin transición; se a orixe xa non se ve, pecha no sitio', async () => {
      TestBed.inject(DialogService).open(SampleDialog, { from: null }).close();
      await stable();
      expect(start).not.toHaveBeenCalled();

      const ref = TestBed.inject(DialogService).open(SampleDialog, { from: opener });
      await stable();
      expect(start).toHaveBeenCalledTimes(1);
      opener.remove();
      ref.close();
      await stable();
      expect(start).toHaveBeenCalledTimes(1);
      expect(document.querySelector('furar-dialog-container')).toBeNull();
    });
  });

  it('cada capa é o seu contexto de acción: mentres garda, as súas accións bloqueadas e a páxina non', async () => {
    @Component({
      imports: [DialogComponent, ButtonDirective],
      changeDetection: ChangeDetectionStrategy.OnPush,
      template: `
        <furar-dialog [heading]="'Editar'">
          <button dialogActions type="button" furarButton id="dlg-cancel">{{ 'Cancelar' }}</button>
          <button dialogActions type="button" furarButton="primary" id="dlg-busy" [busy]="true">
            {{ 'Gardando' }}
          </button>
        </furar-dialog>
      `,
    })
    class BusyDialog {}
    const page = TestBed.createComponent(BusyDialogPage);
    await page.whenStable();
    const ref = TestBed.inject(DialogService).open(BusyDialog);
    await stable();
    const container = document.querySelector<HTMLElement>('furar-dialog-container')!;
    expect(container.querySelector('#dlg-cancel')?.getAttribute('aria-disabled')).toBe('true');
    expect(container.querySelector('furar-dialog')?.getAttribute('aria-busy')).toBe('true');
    expect(ref.disableClose).toBe(true);
    await page.whenStable();
    expect((page.nativeElement as HTMLElement).querySelector('button')?.hasAttribute('aria-disabled')).toBe(false);
    ref.disableClose = false;
    ref.close();
  });
});

@Component({
  imports: [ButtonDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<button type="button" furarButton>{{ 'Na páxina' }}</button>`,
})
class BusyDialogPage {}
