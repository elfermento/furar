// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component, signal, viewChild } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { LanguageService } from '../../../core/i18n/language.service';
import { FieldPopoverComponent } from './field-popover.component';

@Component({
  imports: [FieldPopoverComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="f-field-group">
      <furar-field-popover
        #what
        id="what"
        [label]="'Que'"
        [value]="use()"
        [placeholder]="'Escoller'"
        [nextField]="when"
      >
        <button id="football" type="button" (click)="use.set('Fútbol 11'); what.next()">{{ 'Fútbol 11' }}</button>
        @if (tennis()) {
          <button id="tennis" type="button">{{ 'Tenis' }}</button>
        }
      </furar-field-popover>
      <furar-field-popover #when id="when" [label]="'Data'" [value]="'Mércores 28'">
        <button id="tomorrow" type="button" (click)="when.close()">{{ 'Mañá' }}</button>
      </furar-field-popover>
    </div>
    <furar-field-popover
      id="summary"
      look="summary"
      icon="calendar-month"
      [label]="'Cando'"
      [value]="'Todos os días'"
      [detail]="'Sempre'"
      [action]="'Cambiar'"
    >
      <button id="inside" type="button">{{ 'Listo' }}</button>
    </furar-field-popover>
    <button id="after" type="button">{{ 'Seguinte' }}</button>
    <p id="outside">{{ 'Texto' }}</p>
  `,
})
class HostComponent {
  readonly use = signal<string | null>(null);
  readonly tennis = signal(true);
  readonly what = viewChild.required<FieldPopoverComponent>('what');
}

describe('furar-field-popover', () => {
  let narrow = false;
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    narrow = false;
    window.matchMedia = ((query: string) => ({ matches: narrow, media: query })) as unknown as typeof window.matchMedia;
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
    TestBed.inject(OverlayContainer).ngOnDestroy();
  });

  async function render(lang: 'gl' | 'es' = 'gl') {
    TestBed.configureTestingModule({ providers: [provideTestI18n(lang)] });
    const fixture = TestBed.createComponent(HostComponent);
    await TestBed.inject(LanguageService).use(lang);
    await fixture.whenStable();
    return fixture;
  }

  const trigger = (fixture: ComponentFixture<HostComponent>, id: string) =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(`#${id} .f-field-popover__trigger`)!;
  const dialog = () => document.querySelector<HTMLElement>('[role="dialog"]');
  const active = () => document.activeElement as HTMLElement;

  async function press(fixture: ComponentFixture<HostComponent>, key: string, shiftKey = false) {
    const event = new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true });
    active().dispatchEvent(event);
    await fixture.whenStable();
    return event;
  }

  async function open(fixture: ComponentFixture<HostComponent>, id = 'what') {
    trigger(fixture, id).click();
    await fixture.whenStable();
  }

  it('o campo mostra rótulo e valor (ou o texto baleiro) e abre un diálogo co nome do rótulo', async () => {
    const fixture = await render();
    const button = trigger(fixture, 'what');
    expect(button.textContent?.replace(/\s+/g, ' ').trim()).toBe('Que Escoller');
    expect(button.getAttribute('aria-haspopup')).toBe('dialog');
    expect(button.getAttribute('aria-expanded')).toBe('false');
    await open(fixture);
    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(dialog()?.getAttribute('aria-label')).toBe('Que');
    expect(button.getAttribute('aria-controls')).toBe(dialog()?.id);
    expect(dialog()?.hasAttribute('aria-modal')).toBe(false);
    expect(active().id).toBe('football');
  });

  it('frecha abaixo abre; Esc pecha e devolve o foco ao campo', async () => {
    const fixture = await render();
    trigger(fixture, 'what').focus();
    await press(fixture, 'ArrowDown');
    expect(dialog()).not.toBeNull();
    expect(active().id).toBe('football');
    const escape = await press(fixture, 'Escape');
    expect(escape.defaultPrevented).toBe(true);
    expect(dialog()).toBeNull();
    expect(active()).toBe(trigger(fixture, 'what'));
    expect(trigger(fixture, 'what').getAttribute('aria-expanded')).toBe('false');
  });

  it('o Tab dá a volta dentro do panel', async () => {
    const fixture = await render();
    await open(fixture);
    await press(fixture, 'Tab');
    expect(active().id).toBe('tennis');
    await press(fixture, 'Tab');
    expect(active().id).toBe('football');
    await press(fixture, 'Tab', true);
    expect(active().id).toBe('tennis');
  });

  it('se o contido cambia e leva o control co foco, o foco volve dentro do panel', async () => {
    const fixture = await render();
    await open(fixture);
    await press(fixture, 'Tab');
    expect(active().id).toBe('tennis');
    fixture.componentInstance.tennis.set(false);
    await fixture.whenStable();
    await new Promise((resolve) => setTimeout(resolve));
    expect(active().id).toBe('football');
  });

  it('un clic fóra pecha; o foco volve ao campo salvo que o clic fose noutro control', async () => {
    const fixture = await render();
    const outside = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('#outside')!;
    await open(fixture);
    outside.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }));
    outside.click();
    await fixture.whenStable();
    expect(dialog()).toBeNull();
    expect(active()).toBe(trigger(fixture, 'what'));

    await open(fixture);
    const after = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#after')!;
    after.focus();
    after.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }));
    after.click();
    await fixture.whenStable();
    expect(dialog()).toBeNull();
    expect(active()).toBe(after);
  });

  it('un segundo clic no campo pecha e deixa o foco nel', async () => {
    const fixture = await render();
    await open(fixture);
    trigger(fixture, 'what').dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }));
    await open(fixture);
    expect(dialog()).toBeNull();
    expect(active()).toBe(trigger(fixture, 'what'));
  });

  it('next() pecha este campo e abre o seguinte co foco dentro; ao pechalo, o foco vai a ese campo', async () => {
    const fixture = await render();
    await open(fixture);
    active().click();
    await fixture.whenStable();
    expect(trigger(fixture, 'what').textContent).toContain('Fútbol 11');
    expect(trigger(fixture, 'what').getAttribute('aria-expanded')).toBe('false');
    expect(trigger(fixture, 'when').getAttribute('aria-expanded')).toBe('true');
    expect(dialog()?.getAttribute('aria-label')).toBe('Data');
    expect(active().id).toBe('tomorrow');
    active().click();
    await fixture.whenStable();
    expect(dialog()).toBeNull();
    expect(active()).toBe(trigger(fixture, 'when'));
  });

  it('por baixo de 640 px é unha folla modal co velo e o botón de pechar', async () => {
    narrow = true;
    const fixture = await render();
    await open(fixture);
    const sheet = dialog()!;
    expect(sheet.dataset['sheet']).toBeDefined();
    expect(sheet.getAttribute('aria-modal')).toBe('true');
    expect(active().id).toBe('football');
    const close = sheet.querySelector<HTMLButtonElement>('[data-close]')!;
    expect(close.getAttribute('aria-label')).toBe('Pechar «Que»');
    close.click();
    await fixture.whenStable();
    expect(dialog()).toBeNull();
    expect(active()).toBe(trigger(fixture, 'what'));

    await open(fixture);
    document.querySelector<HTMLElement>('.f-field-popover-backdrop')!.click();
    await fixture.whenStable();
    expect(dialog()).toBeNull();
    expect(active()).toBe(trigger(fixture, 'what'));
  });

  it.each(['gl', 'es'] as const)('pasa axe pechado e aberto, tamén como folla (%s)', async (lang) => {
    const fixture = await render(lang);
    await expectNoAxeViolations(fixture.nativeElement);
    await open(fixture);
    await expectNoAxeViolations(document.body);
    fixture.componentInstance.what().close();
    narrow = true;
    await open(fixture);
    await expectNoAxeViolations(document.body);
  });

  it('look="summary": rótulo enriba, valor, detalle e acción; o nome accesible leva o rótulo', async () => {
    const fixture = await render();
    const host = (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('#summary')!;
    expect(host.getAttribute('data-look')).toBe('summary');
    expect(host.querySelector('.f-field-popover__heading')?.textContent?.trim()).toBe('Cando');
    const button = trigger(fixture, 'summary');
    expect(button.querySelector('.f-field-popover__icon')).not.toBeNull();
    expect(button.querySelector('.f-field-popover__value')?.textContent?.trim()).toBe('Todos os días');
    expect(button.querySelector('.f-field-popover__detail')?.textContent?.trim()).toBe('Sempre');
    expect(button.querySelector('.f-field-popover__action')?.textContent?.trim()).toBe('Cambiar');
    expect(button.textContent?.replace(/\s+/g, ' ').trim()).toBe('Cando: Todos os días Sempre Cambiar');
    await open(fixture, 'summary');
    expect(dialog()?.getAttribute('aria-label')).toBe('Cando');
    expect(active().id).toBe('inside');
    await expectNoAxeViolations(fixture.nativeElement as HTMLElement);
  });
});
