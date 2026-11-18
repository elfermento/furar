// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { OverlayContainer } from '@angular/cdk/overlay';
import { TestBed } from '@angular/core/testing';
import { fakeSession, provideTestApi } from '../../../testing/api';
import { ReportProblemComponent } from './report-problem.component';

describe('«Viches algo que non funciona?» ao final da ficha', () => {
  afterEach(() => TestBed.inject(OverlayContainer).ngOnDestroy());

  function render(authenticated: boolean) {
    const session = fakeSession({});
    session.authenticated.set(authenticated);
    TestBed.configureTestingModule({ providers: [...provideTestApi(session)] });
    const fixture = TestBed.createComponent(ReportProblemComponent);
    fixture.componentRef.setInput('slug', 'naron');
    fixture.componentRef.setInput('facilityId', 'f1');
    fixture.componentRef.setInput('facilityName', 'Pista de pádel 2');
    fixture.detectChanges();
    return { session, el: fixture.nativeElement as HTMLElement };
  }

  it('a frase e, debaixo, o botón secundario', () => {
    const { el } = render(true);
    expect(el.querySelector('.f-report-problem__question')?.textContent).toBe('Viches algo que non funciona?');
    const button = el.querySelector<HTMLButtonElement>('[data-report-open]')!;
    expect(button.textContent?.trim()).toBe('Avisar dun problema');
    expect(button.getAttribute('data-variant')).toBe('secondary');
  });

  it('sen sesión, o mesmo botón leva a entrar e volver co aviso aberto', async () => {
    const { el, session } = render(false);
    el.querySelector<HTMLButtonElement>('[data-report-open]')!.click();
    await vi.waitFor(() => expect(session.login).toHaveBeenCalled());
    expect(session.login.mock.calls[0]![0]).toContain('avisar=1');
  });
});
