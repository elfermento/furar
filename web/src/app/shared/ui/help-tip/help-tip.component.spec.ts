// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import type { ComponentFixture } from '@angular/core/testing';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { HELP_TIP_ENTER_DELAY_MS, HELP_TIP_LEAVE_DELAY_MS, HelpTipComponent } from './help-tip.component';

@Component({
  imports: [HelpTipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-help-tip id="tip" [label]="'Que é o DPD?'" [text]="'Quen vela polos datos persoais.'" />
    <button id="after" type="button">{{ 'Seguinte' }}</button>
    <p id="outside">{{ 'Texto' }}</p>
  `,
})
class HostComponent {}

describe('furar-help-tip', () => {
  afterEach(() => TestBed.inject(OverlayContainer).ngOnDestroy());

  async function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(HostComponent);
    await fixture.whenStable();
    return fixture;
  }

  const button = (fixture: ComponentFixture<HostComponent>) =>
    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#tip button')!;
  const panel = () => document.querySelector<HTMLElement>('.f-help-tip__panel');
  const pointer = (type: string, pointerType = 'mouse') => {
    const event = new MouseEvent(type, { bubbles: type !== 'pointerenter' && type !== 'pointerleave' });
    Object.defineProperty(event, 'pointerType', { value: pointerType });
    return event;
  };
  const settle = async (fixture: ComponentFixture<HostComponent>, ms = 0) => {
    await new Promise((resolve) => setTimeout(resolve, ms));
    await fixture.whenStable();
  };

  it('un «?» co nome da pregunta, descrito polo texto (sempre no DOM)', async () => {
    const fixture = await render();
    const trigger = button(fixture);
    expect(trigger.textContent?.trim()).toBe('?');
    expect(trigger.getAttribute('aria-label')).toBe('Que é o DPD?');
    expect(document.getElementById(trigger.getAttribute('aria-describedby')!)?.textContent?.trim()).toBe(
      'Quen vela polos datos persoais.',
    );
    expect(panel()).toBeNull();
    await expectNoAxeViolations(fixture.nativeElement as HTMLElement);
  });

  it('o rato por riba abre (cun retardo) e segue aberta mentres está enriba do texto; ao saír pecha', async () => {
    const fixture = await render();
    const trigger = button(fixture);
    trigger.dispatchEvent(pointer('pointerenter'));
    await settle(fixture);
    // Pasar de largo non abre: o retardo evita o pestanexo.
    expect(panel()).toBeNull();
    await settle(fixture, HELP_TIP_ENTER_DELAY_MS + 30);
    expect(panel()?.textContent?.trim()).toBe('Quen vela polos datos persoais.');
    trigger.dispatchEvent(pointer('pointerleave'));
    panel()!.dispatchEvent(pointer('pointerenter'));
    await settle(fixture, HELP_TIP_LEAVE_DELAY_MS + 30);
    expect(panel()).not.toBeNull();
    panel()!.dispatchEvent(pointer('pointerleave'));
    await settle(fixture, HELP_TIP_LEAVE_DELAY_MS + 30);
    expect(panel()).toBeNull();
  });

  it('cruzar o «?» co rato sen parar non abre', async () => {
    const fixture = await render();
    const trigger = button(fixture);
    trigger.dispatchEvent(pointer('pointerenter'));
    trigger.dispatchEvent(pointer('pointerleave'));
    await settle(fixture, HELP_TIP_ENTER_DELAY_MS + HELP_TIP_LEAVE_DELAY_MS + 30);
    expect(panel()).toBeNull();
  });

  it('co rato por riba, Esc pecha (descartable)', async () => {
    const fixture = await render();
    const trigger = button(fixture);
    trigger.dispatchEvent(pointer('pointerenter'));
    await settle(fixture, HELP_TIP_ENTER_DELAY_MS + 30);
    expect(panel()).not.toBeNull();
    const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
    document.body.dispatchEvent(escape);
    await settle(fixture);
    expect(panel()).toBeNull();
  });

  it('o foco do teclado non abre só; Intro (clic) fixa; Esc pecha e o foco queda no «?»; Tab fóra pecha', async () => {
    const fixture = await render();
    const trigger = button(fixture);
    trigger.focus();
    await settle(fixture, HELP_TIP_ENTER_DELAY_MS + 30);
    expect(panel()).toBeNull();

    trigger.click();
    await settle(fixture);
    expect(panel()).not.toBeNull();
    await expectNoAxeViolations(document.body);
    const escape = new KeyboardEvent('keydown', { key: 'Escape', bubbles: true, cancelable: true });
    trigger.dispatchEvent(escape);
    await settle(fixture);
    expect(escape.defaultPrevented).toBe(true);
    expect(panel()).toBeNull();
    expect(document.activeElement).toBe(trigger);

    trigger.click();
    await settle(fixture);
    expect(panel()).not.toBeNull();
    const after = (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('#after')!;
    after.focus();
    await settle(fixture);
    expect(panel()).toBeNull();
  });

  it('táctil: o toque non conta como rato; un toque abre e fixa, outro pecha; un toque fóra tamén', async () => {
    const fixture = await render();
    const trigger = button(fixture);
    trigger.dispatchEvent(pointer('pointerenter', 'touch'));
    await settle(fixture, HELP_TIP_ENTER_DELAY_MS + 30);
    expect(panel()).toBeNull();
    trigger.click();
    await settle(fixture);
    expect(panel()).not.toBeNull();
    trigger.dispatchEvent(pointer('pointerleave', 'touch'));
    await settle(fixture, HELP_TIP_LEAVE_DELAY_MS + 30);
    expect(panel()).not.toBeNull();
    trigger.click();
    await settle(fixture);
    expect(panel()).toBeNull();

    trigger.click();
    await settle(fixture);
    expect(panel()).not.toBeNull();
    (fixture.nativeElement as HTMLElement).querySelector<HTMLElement>('#outside')!.click();
    await settle(fixture);
    expect(panel()).toBeNull();
  });

  it('co rato, un clic fixa a capa: xa non pecha ao saír; outro clic pecha', async () => {
    const fixture = await render();
    const trigger = button(fixture);
    trigger.dispatchEvent(pointer('pointerenter'));
    await settle(fixture, HELP_TIP_ENTER_DELAY_MS + 30);
    trigger.click();
    trigger.dispatchEvent(pointer('pointerleave'));
    await settle(fixture, HELP_TIP_LEAVE_DELAY_MS + 30);
    expect(panel()).not.toBeNull();
    trigger.click();
    await settle(fixture);
    expect(panel()).toBeNull();
  });
});
