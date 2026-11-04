// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { backend, expectRequest, fakeSession, flushProblem } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import {
  MEMBERS,
  NOW,
  STAFF_URL,
  dialog,
  member,
  pickAction,
  renderStaff,
  rowOf,
  text,
  useNow,
} from '../../../../testing/staff-members';

const tick = () => new Promise((resolve) => setTimeout(resolve, 0));
/** O texto do toast de «feito», xa traducido. */
const toastText = () => {
  const toast = TestBed.inject(ToastService).current();
  return toast ? TestBed.inject(TranslocoService).translate(toast.key, toast.params) : undefined;
};

/** Escribe nun campo como o faría a persoa. */
function type(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

describe('Persoal (D4-14): invitar', () => {
  useNow();

  it('formulario accesible: resumo, erro no campo por aria-describedby e foco; 409 no correo; axe', async () => {
    const { el, settle } = await renderStaff();
    el.querySelector<HTMLButtonElement>('[data-invite]')!.click();
    await settle();
    const layer = dialog()!;
    expect(text(layer.querySelector('h2'))).toBe('Invitar ao persoal');
    // Rol en tarxetas: radios nativos co nome do rol e, como descrición, o que pode facer.
    const manager = layer.querySelector<HTMLInputElement>('input[type="radio"][data-value="MANAGER"]')!;
    expect(manager.checked).toBe(true);
    const caps = (radio: Element) =>
      [...layer.querySelectorAll(`#${radio.getAttribute('aria-describedby')} li`)].map((li) => text(li)).join(' · ');
    expect(caps(manager)).toBe(
      'Si: Reservas, cobros e cancelacións · Si: Instalacións e horarios · Si: Indicadores · Si: Persoal e datos do concello',
    );
    expect(caps(layer.querySelector('input[data-value="STAFF"]')!)).toBe(
      'Si: Reservas e cobros · Non: Cancelar reservas · Non: Cambiar instalacións · Non: Indicadores e persoal',
    );
    // Idioma do correo: o idioma por defecto do concello.
    expect(layer.querySelector('[data-language="gl"]')?.getAttribute('aria-checked')).toBe('true');
    expect(layer.textContent).not.toContain('Mellor o correo do traballo');
    await expectNoAxeViolations(layer);

    layer.querySelector<HTMLButtonElement>('[data-send]')!.click();
    await settle();
    const email = layer.querySelector<HTMLInputElement>('#staff-invite-email')!;
    expect(text(layer.querySelector('furar-form-errors-summary'))).toContain('1 erro');
    expect(email.getAttribute('aria-invalid')).toBe('true');
    expect(email.getAttribute('aria-describedby')).toBe('staff-invite-email-error');
    await vi.waitFor(() => expect(document.activeElement).toBe(email));

    type(email, 'non-é-correo');
    await settle();
    expect(text(layer.querySelector('#staff-invite-email-error'))).toBe('Escribe un correo válido.');

    type(email, ' ana@naron.example ');
    layer.querySelector<HTMLButtonElement>('[data-send]')!.click();
    flushProblem(await expectRequest((r) => r.url === STAFF_URL && r.method === 'POST'), 409, 'staff.already-member');
    await vi.waitFor(async () => {
      await settle();
      expect(text(layer.querySelector('#staff-invite-email-error'))).toMatch(/^Esta persoa xa está no persoal/);
    });
    expect(dialog()).not.toBeNull();
    await expectNoAxeViolations(layer);
  });

  it('invita co rol e o idioma escollidos; foco a «Invitar» e «Invitación enviada a …» en toast', async () => {
    const { el, settle } = await renderStaff();
    const button = el.querySelector<HTMLButtonElement>('[data-invite]')!;
    button.focus();
    button.click();
    await settle();
    const layer = dialog()!;
    type(layer.querySelector<HTMLInputElement>('#staff-invite-email')!, 'nova@naron.example');
    layer.querySelector<HTMLInputElement>('input[data-value="STAFF"]')!.click();
    layer.querySelector<HTMLButtonElement>('[data-language="es"]')!.click();
    layer.querySelector<HTMLButtonElement>('[data-send]')!.click();
    const post = await expectRequest((r) => r.url === STAFF_URL && r.method === 'POST');
    expect(post.request.body).toEqual({ email: 'nova@naron.example', role: 'STAFF', language: 'es' });
    post.flush(
      member({
        id: 'm9',
        email: 'nova@naron.example',
        name: undefined,
        role: 'STAFF',
        status: 'INVITED',
        invitation: {
          sentAt: '2026-10-28T10:00:00Z',
          expiresAt: '2026-11-04T10:00:00Z',
          delivery: 'SENT',
          resendsExhausted: false,
        },
      }),
    );
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).toBeNull();
    });
    await tick();
    await settle();
    expect(document.activeElement).toBe(el.querySelector('[data-invite]'));
    expect(toastText()).toBe('Invitación enviada a nova@naron.example');
    expect(el.querySelectorAll('[data-group="current"] tbody tr')[0].getAttribute('data-member')).toBe('m9');
  });

  it('a plataforma escolle o rol coma a xestión; o idioma do correo é o do concello; 429 di canto esperar', async () => {
    const { el, settle } = await renderStaff(fakeSession({ platform: true }), MEMBERS, { defaultLanguage: 'es' });
    el.querySelector<HTMLButtonElement>('[data-invite]')!.click();
    await settle();
    const layer = dialog()!;
    expect(layer.querySelector('furar-radio-cards[formcontrolname="role"] [role="radiogroup"]')).not.toBeNull();
    expect(layer.querySelector('[data-language="es"]')?.getAttribute('aria-checked')).toBe('true');
    type(layer.querySelector<HTMLInputElement>('#staff-invite-email')!, 'conserxe@ferrol.example');
    layer.querySelector<HTMLInputElement>('input[data-value="STAFF"]')!.click();
    layer.querySelector<HTMLButtonElement>('[data-send]')!.click();
    const post = await expectRequest((r) => r.url === STAFF_URL && r.method === 'POST');
    expect(post.request.body).toMatchObject({ role: 'STAFF', language: 'es' });
    post.flush(
      { type: 'about:blank', title: 'x', status: 429, code: 'staff.invitation-rate-limited', params: {} },
      { status: 429, statusText: 'x', headers: { 'Retry-After': '7200' } },
    );
    await vi.waitFor(async () => {
      await settle();
      expect(text(layer.querySelector('[data-invite-alert] p'))).toBe(
        'Chegouse ao límite de envíos. Podes volver enviar en 2 h.',
      );
    });
  });
});

describe('Persoal (D4-14): accións por fila', () => {
  useNow();

  it('Reenviar (na fila e no menú): If-Match coa versión, anuncio e foco na fila; 429 con e sen Retry-After', async () => {
    const { el, settle } = await renderStaff();
    const resend = () => rowOf(el, 'm4').querySelector<HTMLButtonElement>('[data-resend]')!;
    resend().focus();
    resend().click();
    let post = await expectRequest(`${STAFF_URL}/m4/invitation`);
    expect(post.request.headers.get('If-Match')).toBe('"1"');
    post.flush({
      ...MEMBERS[3],
      invitation: {
        sentAt: '2026-10-28T10:00:00Z',
        expiresAt: '2026-11-04T10:00:00Z',
        delivery: 'SENT',
        resendsExhausted: false,
      },
      version: 2,
    });
    await vi.waitFor(async () => {
      await settle();
      expect(toastText()).toBe('Invitación reenviada a xan@naron.example');
    });
    expect(text(rowOf(el, 'm4').querySelector('[data-access]'))).toBe('Invitación enviada o 28/10/2026');
    // «Reenviar» xa non está na fila: o foco pasa ao botón das súas accións.
    await tick();
    expect(document.activeElement).toBe(el.querySelector('[data-actions="m4"]'));

    await pickAction(el, settle, 'm2', 'resend');
    post = await expectRequest(`${STAFF_URL}/m2/invitation`);
    post.flush(
      { type: 'about:blank', title: 'x', status: 429, code: 'staff.invitation-rate-limited', params: {} },
      { status: 429, statusText: 'x', headers: { 'Retry-After': '540' } },
    );
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('[role="alert"] [data-alert] p'))).toBe(
        'Aínda non se pode enviar outra vez. Podes reenviar en 9 min.',
      );
    });
    await tick();
    expect(document.activeElement).toBe(el.querySelector('[data-actions="m2"]'));

    await pickAction(el, settle, 'm2', 'resend');
    flushProblem(await expectRequest(`${STAFF_URL}/m2/invitation`), 429, 'staff.invitation-rate-limited');
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('[data-alert] p'))).toMatch(/xa se enviou o máximo de veces/);
    });
  });

  it('Reenviar desactivado coa conta atrás m:ss como descrición; ao rematar, un aviso e sen reloxo', async () => {
    // Reloxo simulado tamén para o intervalo da conta atrás.
    vi.useFakeTimers({ toFake: ['Date', 'setInterval', 'clearInterval'] });
    vi.setSystemTime(NOW);
    const soon = new Date(NOW.getTime() + 105_000).toISOString();
    const members = MEMBERS.map((m) =>
      m.id === 'm2' || m.id === 'm4' ? { ...m, invitation: { ...m.invitation!, resendAvailableAt: soon } } : m,
    );
    const { el, settle } = await renderStaff(undefined, members);
    expect(vi.getTimerCount()).toBe(1);

    // Na fila (m4, o correo non saíu): aria-disabled, segue co foco e non pide nada.
    const inline = rowOf(el, 'm4').querySelector<HTMLButtonElement>('[data-resend]')!;
    expect(inline.getAttribute('aria-disabled')).toBe('true');
    expect(inline.disabled).toBe(false);
    expect(inline.getAttribute('aria-describedby')).toBe('staff-resend-wait-m4');
    expect(text(el.querySelector('#staff-resend-wait-m4'))).toBe('Podes reenviar en 1:45');
    inline.click();
    await settle();

    // No menú (m2): o mesmo, e o rótulo segue sendo «Reenviar».
    el.querySelector<HTMLButtonElement>('[data-actions="m2"]')!.click();
    await settle();
    const item = () => document.querySelector<HTMLButtonElement>('[role="menu"] [data-action="resend"]')!;
    expect(item().getAttribute('aria-disabled')).toBe('true');
    expect(item().getAttribute('aria-describedby')).toBe('staff-resend-wait-menu-m2');
    const hint = document.getElementById('staff-resend-wait-menu-m2')!;
    expect(text(hint)).toBe('Podes reenviar en 1:45');
    expect(hint.getAttribute('aria-hidden')).toBe('true');
    item().click();
    await settle();
    await expectNoAxeViolations(document.querySelector<HTMLElement>('[role="menu"]')!);

    // Cada segundo cambia o texto, pero non hai aviso.
    vi.advanceTimersByTime(1_000);
    await settle();
    expect(text(hint)).toBe('Podes reenviar en 1:44');
    expect(text(el.querySelector('[data-resend-ready]'))).toBe('');

    vi.advanceTimersByTime(104_000);
    await settle();
    expect(item().getAttribute('aria-disabled')).toBeNull();
    expect(item().getAttribute('aria-describedby')).toBeNull();
    expect(text(item())).toBe('Reenviar');
    expect(rowOf(el, 'm4').querySelector('[data-resend]')!.getAttribute('aria-disabled')).toBeNull();
    // Un só aviso en role=status (o último en rematar) e o reloxo parado.
    const ready = el.querySelector('[role="status"][data-resend-ready]');
    expect(text(ready)).toMatch(/^Xa podes reenviar a invitación a (iago|xan)@naron\.example\.$/);
    expect(vi.getTimerCount()).toBe(0);
    vi.advanceTimersByTime(5_000);
    expect(vi.getTimerCount()).toBe(0);
    // Nada se pediu á API.
    backend().verify();
  });

  it('Reenviar desactivado se xa se gastaron os envíos', async () => {
    const members = MEMBERS.map((m) =>
      m.id === 'm4' ? { ...m, invitation: { ...m.invitation!, resendsExhausted: true } } : m,
    );
    const { el, settle } = await renderStaff(undefined, members);
    const inline = rowOf(el, 'm4').querySelector<HTMLButtonElement>('[data-resend]')!;
    expect(inline.getAttribute('aria-disabled')).toBe('true');
    expect(text(el.querySelector('#staff-resend-wait-m4'))).toBe('Xa se enviou o máximo de veces');
    inline.click();
    await settle();
    backend().verify();
  });

  it('Cambiar o rol: diálogo coas tarxetas, PUT con If-Match, anuncio e foco no botón da fila; 409 último xestor', async () => {
    const { el, settle } = await renderStaff();
    await pickAction(el, settle, 'm1', 'role');
    let layer = dialog()!;
    expect(text(layer.querySelector('h2'))).toBe('Cambiar o rol');
    await expectNoAxeViolations(layer);
    layer.querySelector<HTMLInputElement>('input[data-value="STAFF"]')!.click();
    await settle();
    // Pasar a xestora a Recepción: hai que escribir «recepción»; mentres, «Gardar» bloqueado e
    // descrito polo rótulo do campo.
    const save = layer.querySelector<HTMLButtonElement>('[data-save]')!;
    const phrase = layer.querySelector<HTMLInputElement>('[data-confirm-phrase]')!;
    const label = layer.querySelector(`label[for="${phrase.id}"]`)!;
    expect(text(label)).toBe('Para confirmar, escribe «recepción»');
    expect(phrase.getAttribute('aria-describedby')).toBe('staff-change-role-demote');
    expect(save.getAttribute('aria-disabled')).toBe('true');
    expect(save.disabled).toBe(false);
    expect(save.getAttribute('aria-describedby')).toBe(label.id);
    await expectNoAxeViolations(layer);
    save.click();
    type(phrase, 'recepci');
    await settle();
    expect(save.getAttribute('aria-disabled')).toBe('true');
    // Volver a Xestión e outra vez a Recepción: o escrito xa non vale.
    type(phrase, 'recepcion');
    await settle();
    expect(save.getAttribute('aria-disabled')).toBeNull();
    layer.querySelector<HTMLInputElement>('input[data-value="MANAGER"]')!.click();
    await settle();
    expect(layer.querySelector('[data-confirm-phrase]')).toBeNull();
    layer.querySelector<HTMLInputElement>('input[data-value="STAFF"]')!.click();
    await settle();
    expect(save.getAttribute('aria-disabled')).toBe('true');
    // Sen distinguir maiúsculas nin tildes.
    type(layer.querySelector<HTMLInputElement>('[data-confirm-phrase]')!, '  RECEPCIÓN ');
    await settle();
    expect(save.getAttribute('aria-disabled')).toBeNull();
    expect(save.getAttribute('aria-describedby')).toBeNull();
    save.click();
    flushProblem(await expectRequest(`${STAFF_URL}/m1/role`), 409, 'staff.last-manager');
    await vi.waitFor(async () => {
      await settle();
      expect(dialog()).toBeNull();
      expect(text(el.querySelector('[data-alert] p'))).toMatch(/^O concello ten que ter sempre/);
    });
    await tick();
    expect(document.activeElement).toBe(el.querySelector('[data-actions="m1"]'));

    await pickAction(el, settle, 'm2', 'role');
    layer = dialog()!;
    layer.querySelector<HTMLInputElement>('input[data-value="MANAGER"]')!.click();
    await settle();
    layer.querySelector<HTMLButtonElement>('[data-save]')!.click();
    const put = await expectRequest(`${STAFF_URL}/m2/role`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.headers.get('If-Match')).toBe('"1"');
    expect(put.request.body).toEqual({ role: 'MANAGER' });
    put.flush({ ...MEMBERS[1], role: 'MANAGER', version: 2 });
    await vi.waitFor(async () => {
      await settle();
      expect(toastText()).toBe('iago@naron.example agora ten o rol Xestión');
    });
    expect(el.querySelector('[data-alert] p')).toBeNull();
    expect(text(rowOf(el, 'm2').querySelector('[data-cell="role"]'))).toBe('Xestión');
  });

  it('Desactivar: o que perde, escribir «desactivar» e a casiña; a fila pasa ás desactivadas', async () => {
    const { el, settle } = await renderStaff();
    await pickAction(el, settle, 'm1', 'deactivate');
    const confirm = await vi.waitFor(() => {
      const found = document.querySelector<HTMLElement>('[role="alertdialog"]');
      if (!found) throw new Error('sen confirmación');
      return found;
    });
    await settle();
    expect(text(confirm.querySelector('h2'))).toBe('Desactivar?');
    expect(text(confirm)).toContain('Deixa de entrar na xestión do concello');
    expect(text(confirm)).toContain('Pódese reactivar cando volva facer falta, co mesmo rol.');
    expect(text(confirm)).toContain('Ana Rey');
    const accept = confirm.querySelector<HTMLButtonElement>('[data-confirm-accept]')!;
    const phrase = confirm.querySelector<HTMLInputElement>('[data-confirm-phrase]')!;
    const ack = confirm.querySelector<HTMLInputElement>('[data-confirm-ack] input')!;
    const phraseLabel = confirm.querySelector(`label[for="${phrase.id}"]`)!;
    expect(text(phraseLabel)).toBe('Para confirmar, escribe «desactivar»');
    expect(text(confirm.querySelector('[data-confirm-ack]'))).toBe(
      'Entendo que non se borra a conta nin o seu historial: o acceso pódese reactivar e os datos consérvanse segundo a política de conservación.',
    );
    expect(ack.getAttribute('aria-required')).toBe('true');
    // O foco, no campo; a acción, bloqueada e descrita polo que falta.
    await vi.waitFor(() => expect(document.activeElement).toBe(phrase));
    const ackText = confirm.querySelector('[data-confirm-ack] span')!;
    expect(accept.getAttribute('aria-disabled')).toBe('true');
    expect(accept.getAttribute('aria-describedby')).toBe(`${phraseLabel.id} ${ackText.id}`);
    await expectNoAxeViolations(confirm);
    accept.click();
    type(phrase, 'DESACTIVAR');
    await settle();
    expect(accept.getAttribute('aria-disabled')).toBe('true');
    expect(accept.getAttribute('aria-describedby')).toBe(ackText.id);
    ack.click();
    await settle();
    expect(accept.getAttribute('aria-disabled')).toBeNull();
    expect(accept.getAttribute('aria-describedby')).toBeNull();
    backend().expectNone(`${STAFF_URL}/m1/deactivate`);
    accept.click();
    const post = await expectRequest(`${STAFF_URL}/m1/deactivate`);
    expect(post.request.headers.get('If-Match')).toBe('"3"');
    post.flush({ ...MEMBERS[0], status: 'DEACTIVATED', deactivatedAt: '2026-10-28T10:00:00Z', version: 4 });
    await vi.waitFor(async () => {
      await settle();
      expect(toastText()).toBe('ana@naron.example xa está desactivada');
    });
    expect(el.querySelector('[data-group="deactivated"] [data-member="m1"]')).not.toBeNull();
    await tick();
    // A fila segue (agora coa acción «Reactivar»): o foco volve ao seu botón.
    expect(document.activeElement).toBe(el.querySelector('[data-actions="m1"]'));
  });

  it('Reactivar: o mesmo rol sen invitación nova; a fila volve ao persoal en uso', async () => {
    const { el, settle } = await renderStaff();
    await pickAction(el, settle, 'm5', 'reactivate');
    const confirm = await vi.waitFor(() => document.querySelector<HTMLElement>('[role="alertdialog"]')!);
    await settle();
    expect(text(confirm.querySelector('h2'))).toBe('Reactivar?');
    expect(text(confirm)).toContain('Volve entrar co rol Recepción, sen invitación nova.');
    expect(text(confirm)).toContain('Entra co seu correo e contrasinal, coma sempre.');
    expect(confirm.querySelector('[data-confirm-phrase]')).toBeNull();
    expect(confirm.querySelector('[data-confirm-ack]')).toBeNull();
    await expectNoAxeViolations(confirm);
    confirm.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    const post = await expectRequest(`${STAFF_URL}/m5/reactivate`);
    expect(post.request.headers.get('If-Match')).toBe('"4"');
    post.flush({ ...MEMBERS[4], status: 'ACTIVE', deactivatedAt: undefined, version: 5 });
    await vi.waitFor(async () => {
      await settle();
      expect(toastText()).toBe('rosa@naron.example volve estar no persoal co rol Recepción');
    });
    expect(el.querySelector('[data-group="current"] [data-member="m5"]')).not.toBeNull();
    expect(el.querySelector('[data-group="deactivated"]')).toBeNull();
  });

  it('en castelán: «recepción» para pasar a Recepción, sen distinguir tildes nin maiúsculas', async () => {
    const { el, settle } = await renderStaff();
    TestBed.inject(TranslocoService).setActiveLang('es');
    await settle();
    await pickAction(el, settle, 'm1', 'role');
    const layer = dialog()!;
    layer.querySelector<HTMLInputElement>('input[data-value="STAFF"]')!.click();
    await settle();
    const phrase = layer.querySelector<HTMLInputElement>('[data-confirm-phrase]')!;
    expect(text(layer.querySelector(`label[for="${phrase.id}"]`))).toBe('Para confirmar, escribe «recepción»');
    type(phrase, 'recep');
    await settle();
    expect(layer.querySelector('[data-save]')!.getAttribute('aria-disabled')).toBe('true');
    type(phrase, 'Recepcion');
    await settle();
    expect(layer.querySelector('[data-save]')!.getAttribute('aria-disabled')).toBeNull();
  });

  it('412: aviso e lista pedida de novo', async () => {
    const { el, settle } = await renderStaff();
    await pickAction(el, settle, 'm4', 'deactivate');
    const confirm = await vi.waitFor(() => document.querySelector<HTMLElement>('[role="alertdialog"]')!);
    await settle();
    expect(text(confirm)).toContain('A invitación deixa de valer');
    // Unha invitada nunca entrou: o texto da casiña é outro (invítase de novo).
    expect(text(confirm.querySelector('[data-confirm-ack]'))).toMatch(/^Entendo que a invitación deixa de valer/);
    type(confirm.querySelector<HTMLInputElement>('[data-confirm-phrase]')!, 'desactivar');
    confirm.querySelector<HTMLInputElement>('[data-confirm-ack] input')!.click();
    await settle();
    confirm.querySelector<HTMLButtonElement>('[data-confirm-accept]')!.click();
    flushProblem(await expectRequest(`${STAFF_URL}/m4/deactivate`), 412, 'common.stale-version');
    (await expectRequest((r) => r.url === STAFF_URL && r.method === 'GET')).flush(MEMBERS);
    await vi.waitFor(async () => {
      await settle();
      expect(text(el.querySelector('[role="status"] [data-stale] p'))).toMatch(/^Alguén cambiou esta persoa/);
    });
    backend().verify();
  });
});
