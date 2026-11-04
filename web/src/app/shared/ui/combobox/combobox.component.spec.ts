// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoService } from '@jsverse/transloco';
import { Subject } from 'rxjs';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import type { ComboboxOption, ComboboxSource } from './combobox.component';
import { ComboboxComponent } from './combobox.component';

interface Call {
  readonly query: string;
  readonly reply: Subject<readonly ComboboxOption<string>[]>;
}

const STREETS: readonly ComboboxOption<string>[] = [
  { value: 'alto', label: 'Rúa do Alto, 12', detail: '15570 Narón' },
  { value: 'real', label: 'Rúa Real, 40', detail: '15402 Ferrol' },
];

@Component({
  imports: [ReactiveFormsModule, ComboboxComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <label for="address">{{ 'Enderezo' }}</label>
    <furar-combobox
      id="address-box"
      inputId="address"
      minLength="3"
      [formControl]="address"
      [label]="'Enderezo'"
      [source]="source()"
      [displayWith]="display"
      (failed)="failures.set(failures() + 1)"
    />
    <label for="sport">{{ 'Deporte' }}</label>
    <furar-combobox
      id="sport-box"
      inputId="sport"
      allowCreate
      clearOnSelect
      [label]="'Deporte'"
      [source]="source()"
      (selected)="picked.set($event.label)"
      (create)="created.set($event)"
    />
  `,
})
class HostComponent {
  readonly calls: Call[] = [];
  readonly address = new FormControl<string | null>(null);
  readonly failures = signal(0);
  readonly picked = signal('');
  readonly created = signal('');
  readonly display = (value: string) => STREETS.find((s) => s.value === value)?.label ?? value;
  readonly source = signal<ComboboxSource<string>>((query) => {
    const reply = new Subject<readonly ComboboxOption<string>[]>();
    this.calls.push({ query, reply });
    return reply;
  });
}

function key(target: Element, name: string, init: KeyboardEventInit = {}): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true, ...init });
  target.dispatchEvent(event);
  return event;
}

describe('furar-combobox', () => {
  let fixture: ComponentFixture<HostComponent>;
  let host: HostComponent;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
    Element.prototype.scrollIntoView = () => undefined;
  });

  afterEach(() => {
    TestBed.inject(OverlayContainer).ngOnDestroy();
    vi.useRealTimers();
  });

  async function settle(ms = 0) {
    await vi.advanceTimersByTimeAsync(ms);
    fixture.detectChanges();
    await vi.advanceTimersByTimeAsync(0);
  }

  async function render(lang: 'gl' | 'es' = 'gl') {
    TestBed.configureTestingModule({ providers: provideTestI18n(lang) });
    fixture = TestBed.createComponent(HostComponent);
    host = fixture.componentInstance;
    await settle();
  }

  const input = (id = 'address') => document.getElementById(id) as HTMLInputElement;
  const options = () => [...document.querySelectorAll<HTMLElement>('[role="option"]')];
  const panel = () => document.querySelector<HTMLElement>('.f-combobox__panel');
  const announcer = (id = 'address-box') => document.querySelector(`#${id} [data-announcer]`)?.textContent?.trim();

  async function type(text: string, id = 'address') {
    const field = input(id);
    field.focus();
    field.value = text;
    field.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
  }

  async function reply(call: Call | undefined, list: readonly ComboboxOption<string>[]) {
    call?.reply.next(list);
    call?.reply.complete();
    await settle();
  }

  it('en repouso: combobox APG pechado, ligado ao seu rótulo e sen descendente activo', async () => {
    await render();
    const field = input();
    expect(field.getAttribute('role')).toBe('combobox');
    expect(field.getAttribute('aria-autocomplete')).toBe('list');
    expect(field.getAttribute('aria-expanded')).toBe('false');
    expect(field.getAttribute('autocomplete')).toBe('off');
    expect(field.hasAttribute('aria-activedescendant')).toBe(false);
    expect(document.querySelector('label[for="address"]')).not.toBeNull();
    expect(announcer()).toBe('');
  });

  it('busca tras 300 ms sen escribir, só co último texto e con «Buscando…» mentres', async () => {
    await render();
    await type('Rú');
    expect(input().getAttribute('aria-expanded')).toBe('false');
    await settle(400);
    expect(host.calls).toHaveLength(0);

    await type('Rúa');
    expect(input().getAttribute('aria-expanded')).toBe('true');
    expect(panel()?.textContent).toContain('Buscando…');
    expect(announcer()).toBe('Buscando…');
    await settle(200);
    await type('Rúa ');
    await settle(299);
    expect(host.calls).toHaveLength(0);
    await settle(1);
    expect(host.calls.map((c) => c.query)).toEqual(['Rúa']);
  });

  it('fonte lenta: mostra as suxestións ao chegar e anuncia cantas son', async () => {
    await render();
    await type('Rúa');
    await settle(300);
    await settle(5000);
    expect(panel()?.textContent).toContain('Buscando…');
    await reply(host.calls[0], STREETS);
    const list = document.querySelector('[role="listbox"]');
    expect(list?.getAttribute('aria-label')).toBe('Enderezo');
    expect(input().getAttribute('aria-controls')).toBe(list?.id);
    expect(options().map((o) => o.textContent?.trim())).toEqual([
      expect.stringContaining('Rúa do Alto, 12'),
      expect.stringContaining('Rúa Real, 40'),
    ]);
    expect(options()[0].textContent).toContain('15570 Narón');
    expect(announcer()).toBe('2 suxestións');
    expect(input().hasAttribute('aria-activedescendant')).toBe(false);
  });

  it('se a fonte cambia co panel aberto, repite a busca (os datos chegaron despois de escribir)', async () => {
    await render();
    await type('Rúa');
    await settle(300);
    await reply(host.calls[0], []);
    expect(panel()?.textContent).toContain('Sen resultados');
    host.source.set(() => Promise.resolve(STREETS));
    await settle();
    await settle(300);
    expect(options()).toHaveLength(2);
    expect(announcer()).toBe('2 suxestións');
  });

  it('unha resposta vella non pisa a nova (cancélase)', async () => {
    await render();
    await type('Rúa');
    await settle(300);
    await type('Rúa R');
    await settle(300);
    expect(host.calls).toHaveLength(2);
    await reply(host.calls[0], STREETS);
    expect(panel()?.textContent).toContain('Buscando…');
    await reply(host.calls[1], [STREETS[1]]);
    expect(options()).toHaveLength(1);
    expect(announcer()).toBe('1 suxestión');
  });

  it('teclado: frechas moven o activo (con volta), Intro escolle e fixa o valor do formulario', async () => {
    await render();
    await type('Rúa');
    await settle(300);
    await reply(host.calls[0], STREETS);
    const field = input();

    key(field, 'ArrowDown');
    await settle();
    expect(field.getAttribute('aria-activedescendant')).toBe(options()[0].id);
    expect(options()[0].getAttribute('aria-selected')).toBe('true');
    expect(options()[1].getAttribute('aria-selected')).toBe('false');
    key(field, 'ArrowDown');
    key(field, 'ArrowDown');
    await settle();
    expect(field.getAttribute('aria-activedescendant')).toBe(options()[0].id);
    key(field, 'ArrowUp');
    await settle();
    expect(field.getAttribute('aria-activedescendant')).toBe(options()[1].id);

    const enter = key(field, 'Enter');
    await settle();
    expect(enter.defaultPrevented).toBe(true);
    expect(host.address.value).toBe('real');
    expect(field.value).toBe('Rúa Real, 40');
    expect(field.getAttribute('aria-expanded')).toBe('false');
    expect(document.activeElement).toBe(field);

    // Escribir despois de escoller borra o valor: o texto solto non é un enderezo.
    await type('Rúa Real, 4');
    expect(host.address.value).toBeNull();
  });

  it('Intro sen activo non fai nada (deixa enviar o formulario); Alt+abaixo abre sen mover', async () => {
    await render();
    await type('Rúa');
    await settle(300);
    await reply(host.calls[0], STREETS);
    expect(key(input(), 'Enter').defaultPrevented).toBe(false);
    key(input(), 'Escape');
    await settle();
    key(input(), 'ArrowDown', { altKey: true });
    await settle();
    expect(input().getAttribute('aria-expanded')).toBe('true');
    expect(input().hasAttribute('aria-activedescendant')).toBe(false);
    expect(host.calls).toHaveLength(1);
  });

  it('Esc pecha e, xa pechado, borra o texto e o valor; Tab pecha sen escoller', async () => {
    await render();
    host.address.setValue('alto');
    await settle();
    expect(input().value).toBe('Rúa do Alto, 12');

    await type('Rúa');
    await settle(300);
    await reply(host.calls[0], STREETS);
    key(input(), 'ArrowDown');
    key(input(), 'Tab');
    await settle();
    expect(input().getAttribute('aria-expanded')).toBe('false');
    expect(host.address.value).toBeNull();

    key(input(), 'ArrowDown');
    await settle();
    expect(input().getAttribute('aria-expanded')).toBe('true');
    expect(host.calls).toHaveLength(1);
    const first = key(input(), 'Escape');
    await settle();
    expect(first.defaultPrevented).toBe(true);
    expect(input().getAttribute('aria-expanded')).toBe('false');
    expect(input().value).toBe('Rúa');
    key(input(), 'Escape');
    await settle();
    expect(input().value).toBe('');
  });

  it('co rato: premer non quita o foco e soltar co botón principal escolle', async () => {
    await render();
    await type('Rúa');
    await settle(300);
    await reply(host.calls[0], STREETS);
    const down = new MouseEvent('mousedown', { bubbles: true, cancelable: true });
    options()[0].dispatchEvent(down);
    expect(down.defaultPrevented).toBe(true);
    const label = options()[0].querySelector('span');
    label?.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, button: 2 }));
    await settle();
    expect(host.address.value).toBeNull();
    label?.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, button: 0 }));
    await settle();
    expect(host.address.value).toBe('alto');
    expect(panel()).toBeNull();
  });

  it('sen resultados e con «Crear»: anúncio e a última opción emite o texto', async () => {
    await render();
    await type('  Curling ', 'sport');
    await settle(300);
    expect(host.calls[0].query).toBe('Curling');
    await reply(host.calls[0], []);
    expect(panel()?.textContent).toContain('Sen resultados');
    expect(options()).toHaveLength(1);
    expect(options()[0].textContent?.trim()).toBe('Crear «Curling»');
    expect(announcer('sport-box')).toBe('Sen resultados Opción ao final: Crear «Curling».');
    key(input('sport'), 'ArrowDown');
    key(input('sport'), 'Enter');
    await settle();
    expect(host.created()).toBe('Curling');
    expect(input('sport').getAttribute('aria-expanded')).toBe('false');
  });

  it('«Crear» non sae se unha suxestión xa se chama así; clearOnSelect baleira tras escoller', async () => {
    await render();
    await type('pádel', 'sport');
    await settle(300);
    await reply(host.calls[0], [{ value: 'padel', label: 'Pádel' }]);
    expect(options().map((o) => o.dataset['kind'])).toEqual(['option']);
    key(input('sport'), 'ArrowDown');
    key(input('sport'), 'Enter');
    await settle();
    expect(host.picked()).toBe('Pádel');
    expect(input('sport').value).toBe('');
  });

  it('con erro: avisa, emite failed e non toca o valor; volver abrir busca de novo', async () => {
    await render();
    await type('Rúa');
    await settle(300);
    host.calls[0].reply.error(new Error('503'));
    await settle();
    expect(host.failures()).toBe(1);
    expect(panel()?.textContent).toContain('Non se puido buscar');
    expect(announcer()).toBe('Non se puido buscar');
    expect(options()).toHaveLength(0);
    expect(input().hasAttribute('aria-controls')).toBe(false);
    expect(host.address.value).toBeNull();

    key(input(), 'Escape');
    key(input(), 'ArrowDown');
    await settle(300);
    expect(host.calls).toHaveLength(2);
  });

  it('desactivado polo formulario e anuncios en castelán', async () => {
    await render('es');
    await type('Rúa');
    await settle(300);
    await reply(host.calls[0], [STREETS[0]]);
    expect(announcer()).toBe('1 sugerencia');
    host.address.disable();
    await settle();
    expect(input().disabled).toBe(true);
    expect(panel()).toBeNull();
  });

  it('cambia o idioma do anuncio en quente', async () => {
    await render();
    await type('Rúa');
    await settle(300);
    await reply(host.calls[0], STREETS);
    TestBed.inject(TranslocoService).setActiveLang('es');
    await settle();
    expect(announcer()).toBe('2 sugerencias');
  });

  it('aberto con suxestións e activo pasa axe', async () => {
    await render();
    await type('Rúa');
    await settle(300);
    await reply(host.calls[0], STREETS);
    key(input(), 'ArrowDown');
    await settle();
    vi.useRealTimers();
    await expectNoAxeViolations(document.body);
  });
});
