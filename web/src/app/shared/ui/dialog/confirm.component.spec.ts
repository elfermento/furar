// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import type { ConfirmOptions } from './confirm.component';
import { DialogService } from './dialog.service';

const stable = () => TestBed.inject(ApplicationRef).whenStable();
const container = () => document.querySelector<HTMLElement>('furar-dialog-container');
const cancel = () => container()!.querySelector<HTMLButtonElement>('[data-confirm-cancel]')!;
const accept = () => container()!.querySelector<HTMLButtonElement>('[data-confirm-accept]')!;

const WITHDRAW: ConfirmOptions = {
  heading: 'Dar de baixa?',
  message: 'Deixa de verse e non se pode reservar.',
  confirmLabel: 'Dar de baixa',
  busyLabel: 'Dando de baixa',
  tone: 'danger',
};

describe('furar-confirm e DialogService.confirm', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideTestI18n()] }));
  afterEach(() => TestBed.inject(OverlayContainer).ngOnDestroy());

  async function ask(options: ConfirmOptions = WITHDRAW) {
    const answer = TestBed.inject(DialogService).confirm(options);
    await stable();
    return answer;
  }

  it('é un alertdialog co título e a mensaxe como nome e descrición, e o foco en «Cancelar»', async () => {
    void ask();
    await stable();
    const dialog = container()!;
    expect(dialog.getAttribute('role')).toBe('alertdialog');
    expect(dialog.getAttribute('aria-modal')).toBe('true');
    expect(document.getElementById(dialog.getAttribute('aria-labelledby')!)?.textContent).toBe('Dar de baixa?');
    expect(document.getElementById(dialog.getAttribute('aria-describedby')!)?.textContent).toContain('Deixa de verse');
    expect(document.activeElement).toBe(cancel());
    expect(cancel().textContent?.trim()).toBe('Cancelar');
    expect(accept().textContent?.trim()).toBe('Dar de baixa');
    expect(accept().getAttribute('data-variant')).toBe('danger');
    // Cancelar á esquerda (antes no DOM) e a acción á dereita.
    expect(cancel().compareDocumentPosition(accept()) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    await expectNoAxeViolations(dialog);
  });

  it('resolve true ao confirmar e false ao cancelar ou con Esc', async () => {
    const yes = ask();
    await stable();
    accept().click();
    expect(await yes).toBe(true);

    const no = ask({ ...WITHDRAW, cancelLabel: 'Volver', tone: undefined });
    await stable();
    expect(cancel().textContent?.trim()).toBe('Volver');
    expect(accept().getAttribute('data-variant')).toBe('primary');
    cancel().click();
    expect(await no).toBe(false);

    const escaped = ask();
    await stable();
    const event = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true });
    Object.defineProperty(event, 'keyCode', { get: () => 27 });
    container()!.dispatchEvent(event);
    expect(await escaped).toBe(false);
  });

  it('con action: queda ocupada co rótulo en xerundio ata que remata e despois pecha con true', async () => {
    let finish: () => void = () => undefined;
    const answer = ask({ ...WITHDRAW, action: () => new Promise<void>((resolve) => (finish = resolve)) });
    await stable();
    accept().click();
    await stable();
    expect(accept().getAttribute('aria-busy')).toBe('true');
    expect(accept().textContent?.trim()).toBe('Dando de baixa');
    // «Cancelar» bloqueado sen `disabled`; Esc tampouco pecha mentres corre.
    expect(cancel().getAttribute('aria-disabled')).toBe('true');
    expect(cancel().disabled).toBe(false);
    cancel().click();
    accept().click(); // un segundo clic non repite a acción
    finish();
    expect(await answer).toBe(true);
    expect(container()).toBeNull();
  });

  it('con action que falla (tamén un Observable): pecha e rexeita co erro, sexa ou non un Error', async () => {
    const failure = { status: 409 };
    const answer = ask({ ...WITHDRAW, action: () => throwError(() => failure) });
    await stable();
    accept().click();
    await expect(answer).rejects.toBe(failure);

    const ok = ask({ ...WITHDRAW, busyLabel: undefined, action: () => of(1) });
    await stable();
    accept().click();
    expect(await ok).toBe(true);
  });

  it('con typeToConfirm: foco no campo e a acción bloqueada (aria-disabled) ata escribir a frase', async () => {
    const answer = ask({ ...WITHDRAW, typeToConfirm: 'eliminar instalación' });
    await stable();
    const phrase = container()!.querySelector<HTMLInputElement>('[data-confirm-phrase]')!;
    const label = container()!.querySelector<HTMLLabelElement>(`label[for="${phrase.id}"]`)!;
    expect(label.textContent).toContain('Para confirmar, escribe «eliminar instalación»');
    expect(document.activeElement).toBe(phrase);
    expect(accept().getAttribute('aria-disabled')).toBe('true');
    expect(accept().disabled).toBe(false);
    expect(accept().getAttribute('aria-describedby')).toBe(label.id);
    accept().click();
    phrase.value = 'eliminar';
    phrase.dispatchEvent(new Event('input'));
    await stable();
    expect(accept().getAttribute('aria-disabled')).toBe('true');
    // Sen distinguir maiúsculas, tildes nin espazos de máis.
    phrase.value = '  ELIMINAR   instalacion ';
    phrase.dispatchEvent(new Event('input'));
    await stable();
    expect(accept().getAttribute('aria-disabled')).toBeNull();
    expect(accept().getAttribute('aria-describedby')).toBeNull();
    await expectNoAxeViolations(container()!);
    accept().click();
    expect(await answer).toBe(true);
  });

  it('con acknowledgement: casiña obrigatoria co texto exacto; con frase, fan falta as dúas', async () => {
    const answer = ask({
      ...WITHDRAW,
      typeToConfirm: 'desactivar',
      acknowledgement: 'Entendo que non se borra a conta.',
    });
    await stable();
    const phrase = container()!.querySelector<HTMLInputElement>('[data-confirm-phrase]')!;
    const ack = container()!.querySelector<HTMLInputElement>('[data-confirm-ack] input')!;
    const ackText = container()!.querySelector('[data-confirm-ack] span')!;
    expect(ackText.textContent?.trim()).toBe('Entendo que non se borra a conta.');
    expect(ack.type).toBe('checkbox');
    expect(ack.getAttribute('aria-required')).toBe('true');
    // A casiña sola non abonda.
    ack.click();
    await stable();
    expect(accept().getAttribute('aria-disabled')).toBe('true');
    phrase.value = 'Desactivár';
    phrase.dispatchEvent(new Event('input'));
    await stable();
    expect(accept().getAttribute('aria-disabled')).toBeNull();
    // Desmarcala bloquea outra vez e a acción descríbese co texto da casiña.
    ack.click();
    await stable();
    expect(accept().getAttribute('aria-disabled')).toBe('true');
    expect(accept().getAttribute('aria-describedby')).toBe(ackText.id);
    await expectNoAxeViolations(container()!);
    ack.click();
    await stable();
    accept().click();
    expect(await answer).toBe(true);
  });

  it('con details (estilo A): liñas con icona no canto da mensaxe, e son a descrición; axe', async () => {
    void ask({
      heading: 'Cancelar a reserva?',
      confirmLabel: 'Cancelar a reserva',
      tone: 'danger',
      details: [
        {
          icon: 'calendar-month',
          tone: 'primary',
          title: 'Pista de pádel 1',
          text: 'xoves, 29 de outubro · 20:30–21:30',
        },
        { icon: 'info', tone: 'warn', text: 'Xa está pagada.' },
      ],
    });
    await stable();
    const dialog = container()!;
    expect(dialog.querySelector('.f-dialog__text')).toBeNull();
    const lines = [...dialog.querySelectorAll('[data-confirm-detail]')];
    expect(lines.map((line) => line.getAttribute('data-tone'))).toEqual(['primary', 'warn']);
    expect(lines[0].querySelector('b')?.textContent).toBe('Pista de pádel 1');
    expect(lines[0].querySelector('furar-icon')).not.toBeNull();
    const description = document.getElementById(dialog.getAttribute('aria-describedby')!);
    expect(description?.textContent).toContain('xoves, 29 de outubro');
    expect(description?.textContent).toContain('Xa está pagada.');
    await expectNoAxeViolations(dialog);
  });
});
