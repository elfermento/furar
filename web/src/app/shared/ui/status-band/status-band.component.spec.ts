// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { provideTestI18n } from '../../../../testing/i18n';
import { ButtonDirective } from '../button/button.directive';
import { DisclosureComponent } from '../disclosure/disclosure.component';
import { StatusBandComponent } from './status-band.component';

@Component({
  imports: [ButtonDirective, DisclosureComponent, StatusBandComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-status-band id="a" tone="warn" icon="schedule" [heading]="'Hai unha praza libre para ti'">
      <p>{{ 'Ata as 18:42 só o sabes ti.' }}</p>
      <button bandActions type="button" furarButton="primary">{{ 'Confirmar a praza' }}</button>
      <button bandActions type="button" furarButton="quiet">{{ 'Non a quero' }}</button>
      <furar-disclosure bandMore [heading]="'Como funciona'"
        ><p>{{ 'Detalle' }}</p></furar-disclosure
      >
    </furar-status-band>
    <furar-status-band id="b" live="off" [heading]="'Non quedan prazas'" />
  `,
})
class Host {}

describe('StatusBandComponent: a banda de estado', () => {
  function render() {
    TestBed.configureTestingModule({ providers: [provideTestI18n()] });
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('o ton, a icona, a frase nunha rexión de estado, o contido, as accións e o pregable pechado', async () => {
    const el = render();
    const band = el.querySelector('#a')!;
    expect(band.getAttribute('data-tone')).toBe('warn');
    const status = band.querySelector('[role="status"]')!;
    expect(status.querySelector('.f-status-band__heading')?.textContent?.trim()).toBe('Hai unha praza libre para ti');
    expect(status.textContent).toContain('Ata as 18:42 só o sabes ti.');
    // As accións e o pregable quedan fóra da rexión: o lector non os le en cada cambio.
    expect(status.querySelector('button')).toBeNull();
    const actions = [...band.querySelectorAll('.f-status-band__actions > button')];
    expect(actions.map((b) => b.textContent?.trim())).toEqual(['Confirmar a praza', 'Non a quero']);
    expect(band.querySelector('.f-disclosure__toggle')?.getAttribute('aria-expanded')).toBe('false');
    expect(band.querySelector('.f-status-band__icon')?.getAttribute('aria-hidden')).toBe('true');
    await expectNoAxeViolations(el);
  });

  it('por defecto, info; con live="off", sen rexión; sen accións, a fila baleira', () => {
    const band = render().querySelector('#b')!;
    expect(band.getAttribute('data-tone')).toBe('info');
    expect(band.querySelector('[role="status"]')).toBeNull();
    expect(band.querySelector('.f-status-band__actions')?.children).toHaveLength(0);
  });
});
