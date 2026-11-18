// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import type { ComboboxSource } from '../combobox/combobox.component';
import { foldText } from '../combobox/combobox-search';
import { MultiComboboxComponent } from './multi-combobox.component';

const NAMES: Record<string, string> = {
  f1: 'Pista de pádel 1',
  f2: 'Pista de pádel 2',
  f3: 'Campo de fútbol',
  c1: 'Bolos celtas',
};

@Component({
  imports: [MultiComboboxComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-multi-combobox
      #field
      fieldId="sitio"
      label="Instalación"
      chosenLabel="Instalacións escollidas"
      debounce="0"
      highlight
      allowCreate
      [limit]="limit()"
      [source]="source"
      [key]="key"
      [display]="display"
      [outline]="outline"
      [countText]="countText"
      [(selected)]="selected"
      (create)="created.set($event); field.add('c1')"
    />
  `,
})
class HostComponent {
  readonly selected = signal<readonly string[]>(['f3']);
  readonly limit = signal(0);
  readonly created = signal('');
  readonly source: ComboboxSource<string> = async (query) =>
    Object.entries(NAMES)
      .filter(([id, name]) => id !== 'c1' && foldText(name).includes(foldText(query)))
      .map(([value, label]) => ({ value, label }));
  readonly key = (id: string) => id;
  readonly display = (id: string) => NAMES[id] ?? id;
  readonly outline = (id: string) => id.startsWith('c');
  readonly countText = (count: number) => `${count} escollidas`;
}

describe('furar-multi-combobox', () => {
  let fixture: ComponentFixture<HostComponent>;

  async function render() {
    TestBed.configureTestingModule({ providers: provideTestI18n('gl') });
    fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function settle() {
    fixture.detectChanges();
    await fixture.whenStable();
  }

  const el = () => fixture.nativeElement as HTMLElement;
  const field = () => el().querySelector<HTMLInputElement>('#sitio')!;
  const chips = () => [...el().querySelectorAll('.f-chip')].map((chip) => chip.textContent?.trim());
  const options = () => [...document.querySelectorAll<HTMLElement>('[role="option"]')];
  const status = () => el().querySelector('furar-chip-list [role="status"]')?.textContent?.trim();
  const type = async (value: string) => {
    field().value = value;
    field().dispatchEvent(new Event('input'));
    await vi.waitFor(async () => {
      await settle();
      expect(options().length).toBeGreaterThan(0);
    });
  };
  const key = async (name: string) => {
    field().dispatchEvent(new KeyboardEvent('keydown', { key: name, bubbles: true, cancelable: true }));
    await settle();
  };

  it('rótulo dentro do campo e os escollidos debaixo; busca sen acentos e sen os xa escollidos; axe', async () => {
    await render();
    expect(el().querySelector('label[for="sitio"]')?.textContent?.trim()).toBe('Instalación');
    expect(el().querySelector('.f-field__control > furar-combobox + furar-chip-list')).not.toBeNull();
    expect(chips()).toEqual(['Campo de fútbol']);

    await type('PADEL');
    expect(options().map((o) => o.textContent?.trim())).toEqual([
      'Pista de pádel 1',
      'Pista de pádel 2',
      expect.stringContaining('Crear'),
    ]);
    await key('ArrowDown');
    await key('ArrowDown');
    await key('Enter');
    expect(fixture.componentInstance.selected()).toEqual(['f3', 'f2']);
    expect(chips()).toEqual(['Campo de fútbol', 'Pista de pádel 2']);
    expect(field().value).toBe('');
    expect(status()).toBe('Engadiuse Pista de pádel 2. 2 escollidas.');

    // A xa escollida non volve saír.
    await type('pista');
    expect(options().map((o) => o.textContent?.trim())).toEqual(['Pista de pádel 1', expect.stringContaining('Crear')]);
    await expectNoAxeViolations(el());
  });

  it('con `limit`, como moito esas suxestións', async () => {
    await render();
    fixture.componentInstance.selected.set([]);
    fixture.componentInstance.limit.set(2);
    await settle();
    await type('a');
    expect(options().filter((o) => !o.textContent?.includes('Crear'))).toHaveLength(2);
  });

  it('Retroceso co campo baleiro quita o último; o «×» quita o seu; anúnciase cada baixa', async () => {
    await render();
    fixture.componentInstance.selected.set(['f1', 'f2', 'f3']);
    await settle();
    field().focus();
    await key('Backspace');
    expect(chips()).toEqual(['Pista de pádel 1', 'Pista de pádel 2']);
    expect(document.activeElement).toBe(field());
    expect(status()).toBe('Quitouse Campo de fútbol. 2 escollidas.');

    el().querySelector<HTMLButtonElement>('[data-chip-remove="0"]')!.click();
    await settle();
    expect(fixture.componentInstance.selected()).toEqual(['f2']);
    expect(status()).toBe('Quitouse Pista de pádel 1. 1 escollidas.');

    // Con texto no campo, Retroceso só borra letras.
    field().value = 'Pi';
    await key('Backspace');
    expect(chips()).toEqual(['Pista de pádel 2']);
  });

  it('«Crear» emite o texto e a feature engade co método `add`, con bordo', async () => {
    await render();
    await type('Bolos');
    await key('ArrowUp');
    await key('Enter');
    expect(fixture.componentInstance.created()).toBe('Bolos');
    await settle();
    expect(chips()).toEqual(['Campo de fútbol', 'Bolos celtas']);
    expect(el().querySelector('.f-chip[data-outline]')?.textContent?.trim()).toBe('Bolos celtas');
  });
});
