// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import type { StaffRole } from '../../../core/auth/roles';
import { managementHome } from '../../../core/auth/roles';
import { PlatformAccessService } from '../../../core/auth/platform-access.service';
import { SessionService } from '../../../core/auth/session.service';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { placeOf } from '../../../shared/i18n/place-name';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import type { InvitationProgress } from './invitation-progress';
import { clearProgress, isToken, readProgress, saveProgress } from './invitation-progress';
import type { StaffInvitationPreviewResponse } from './staff-invitation.store';
import { StaffInvitationStore } from './staff-invitation.store';

/** Onde está a invitación: entrar ou un estado final ou de erro, cada un co seu título. */
export type InvitationStep =
  'loading' | 'sign-in' | 'expired' | 'invalid' | 'other-account' | 'personal' | 'already' | 'unverified' | 'error';

/** Erros ao aceptar que só levan a outra pantalla, sen máis. */
const ACCEPT_ERROR_STEPS: Readonly<Record<string, InvitationStep>> = {
  // A sesión é doutra conta: outro correo, a da plataforma ou a de quen invitou (maqueta 50). Unha
  // soa pantalla, sen ningún correo: pechar sesión e seguir, ou volver.
  'staff.invitation-email-mismatch': 'other-account',
  'staff.invitation-platform-account': 'other-account',
  'staff.invitation-own': 'other-account',
  // A conta xa reservou como veciña: a de traballo vai á parte (ADR-0027, revisión do 2026-10-30).
  'staff.invitation-personal-account': 'personal',
  'auth.email-not-verified': 'unverified',
};

const normalize = (email: string | undefined) => email?.trim().toLowerCase() ?? '';

/**
 * O camiño da invitación (maqueta 38): sen sesión, a web leva a persoa directamente a crear a conta
 * en Keycloak co correo invitado xa posto e a proba da invitación (`prompt=create` + `login_hint` +
 * `furar_invitation`: o correo queda verificado sen outro correo; quen xa ten conta preme
 * «Entrar» alí), só unha vez por token e lapela para non entrar en bucle. Ao volver con sesión,
 * acepta soa, sen capa nin casiña (ADR-0027, revisión do 2026-11-03: as obrigas do persoal están no
 * aviso legal), e leva co toast á xestión que lle toca. Os erros levan cada un ao seu estado.
 */
@Injectable()
export class InvitationFlow {
  private readonly store = inject(StaffInvitationStore);
  readonly session = inject(SessionService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly platform = inject(PlatformAccessService);
  private readonly staffAccess = inject(StaffAccessService);

  readonly step = signal<InvitationStep>('loading');
  readonly invitation = signal<StaffInvitationPreviewResponse | null>(null);
  /** O rol, xa aceptada (ou o de quen xa era persoal). */
  readonly role = signal<StaffRole | null>(null);
  readonly busy = signal(false);
  /** Texto do erro xenérico (xa traducido). */
  readonly errorText = signal('');

  readonly slug = computed(() => this.municipality.slug() ?? '');
  readonly name = computed(() => this.municipality.name() ?? '');
  /** A xestión que lle toca: os datos do concello (xestión) ou as reservas (persoal do mostrador). */
  readonly managementUrl = computed(() => managementHome(this.slug(), this.role() ?? 'MANAGER'));

  private progress: InvitationProgress | null = null;

  /** O token chegou no fragmento: gárdase (se é novo) e o URL queda sen el. */
  receive(fragment: string): void {
    if (isToken(fragment) && readProgress(this.storage(), this.slug())?.token !== fragment) {
      this.remember({ slug: this.slug(), token: fragment });
    }
  }

  /** Onde está a invitación: o paso que toca, tamén ao volver de Keycloak. */
  async start(): Promise<void> {
    this.step.set('loading');
    this.errorText.set('');
    await this.session.whenReady();
    const progress = this.progress ?? readProgress(this.storage(), this.slug());
    this.progress = progress;
    if (!progress) {
      this.step.set('invalid');
      return;
    }
    try {
      this.invitation.set(await this.store.preview(this.slug(), progress.token));
    } catch (error) {
      await this.previewFailed(error);
      return;
    }
    if (!this.session.authenticated()) {
      // A primeira vez, directamente a Keycloak; se volve sen sesión, un botón (sen bucle).
      if (progress.stage === 'keycloak') this.step.set('sign-in');
      else await this.signIn();
    } else if (this.session.email() && normalize(this.session.email()) !== normalize(this.invitation()?.email)) {
      this.step.set('other-account');
    } else {
      await this.accept();
    }
  }

  /**
   * Crear a conta (ou entrar, desde a mesma páxina de Keycloak) co correo invitado xa posto e a
   * proba da invitación: Keycloak dá ese correo por verificado e non manda outro correo.
   */
  async signIn(): Promise<void> {
    if (!this.progress) return;
    this.remember({ ...this.progress, stage: 'keycloak' });
    const email = this.invitation()?.email;
    const proof = this.invitation()?.registrationProof;
    this.busy.set(true);
    await this.session.login(this.returnUrl(), {
      prompt: 'create',
      ...(email ? { loginHint: email } : {}),
      ...(proof ? { invitationProof: proof } : {}),
    });
  }

  /** Acepta coa persoa da sesión. Ben: toast e a xestión que lle toca. Mal: o estado do erro. */
  private async accept(): Promise<void> {
    const progress = this.progress;
    if (!progress || this.busy()) return;
    this.busy.set(true);
    try {
      const { role } = await this.store.accept(this.slug(), progress.token);
      this.role.set(role);
      this.forget('loading');
      this.toasts.show('staff.invitation.accepted', {
        ofName: placeOf(this.name(), this.transloco.getActiveLang()),
      });
      // O acceso gardado neste concello aínda é o de antes (sen rol): as gardas lerían ese.
      await this.staffAccess.reload(this.slug());
      await this.router.navigateByUrl(this.managementUrl(), { replaceUrl: true });
    } catch (error) {
      await this.acceptFailed(error);
    } finally {
      this.busy.set(false);
    }
  }

  /**
   * Outra conta: pecha a sesión e volve á invitación, que leva outra vez ao rexistro (ou a entrar)
   * co correo invitado. A invitación segue nesta lapela, coma nova (sen a marca de Keycloak).
   */
  async signOut(): Promise<void> {
    if (this.progress) this.remember({ slug: this.progress.slug, token: this.progress.token });
    this.busy.set(true);
    await this.session.logout(this.returnUrl());
  }

  /**
   * Outra conta, «Volver»: deixa a invitación e leva a persoa ao seu sitio neste concello (a súa
   * xestión, a da plataforma ou a portada).
   */
  async back(): Promise<void> {
    this.busy.set(true);
    const role = await this.memberRole();
    clearProgress(this.storage());
    this.progress = null;
    const url = role
      ? managementHome(this.slug(), role)
      : this.platform.platformAdmin()
        ? `/${this.slug()}/xestion/concello`
        : `/${this.slug()}`;
    this.busy.set(false);
    await this.router.navigateByUrl(url, { replaceUrl: true });
  }

  /** Sen correo verificado: volve a Keycloak, que pide verificalo ou trae o token ao día. */
  async verified(): Promise<void> {
    this.busy.set(true);
    await this.session.login(this.returnUrl());
  }

  private async previewFailed(error: unknown): Promise<void> {
    const problem = asProblem(error);
    if (problem.status === 410) {
      this.forget('expired');
    } else if (problem.status === 404) {
      // Xa usada: se quen entrou xa é persoal deste concello, dío; se non, non vale.
      this.forget((await this.memberRole()) ? 'already' : 'invalid');
    } else {
      this.fail(problem);
    }
  }

  private async acceptFailed(error: unknown): Promise<void> {
    const problem = asProblem(error);
    const step = ACCEPT_ERROR_STEPS[problem.code];
    if (step) return this.step.set(step);
    if (problem.code === 'staff.already-member') {
      await this.memberRole();
      return this.forget('already');
    }
    if (problem.status === 410) {
      this.forget('expired');
    } else if (problem.status === 404) {
      // Unha segunda aceptación despois dun 200 (dobre clic, outra lapela) dá 404: xa está aceptada.
      this.forget((await this.memberRole()) ? 'already' : 'invalid');
    } else {
      this.fail(problem);
    }
  }

  /** O rol de quen ten a sesión neste concello (`null` sen sesión, sen rol ou se falla). */
  private async memberRole(): Promise<StaffRole | null> {
    if (!this.session.authenticated()) return null;
    try {
      const role = (await this.store.myAccess(this.slug())).role ?? null;
      this.role.set(role);
      return role;
    } catch {
      return null;
    }
  }

  private fail(error: unknown): void {
    const problem = asProblem(error);
    this.errorText.set(problem.translated);
    this.step.set('error');
  }

  /** Estado final sen volta atrás: a invitación xa non serve nesta lapela. */
  private forget(step: InvitationStep): void {
    clearProgress(this.storage());
    this.progress = null;
    this.step.set(step);
  }

  private remember(progress: InvitationProgress): InvitationProgress {
    this.progress = progress;
    saveProgress(this.storage(), progress);
    return progress;
  }

  private returnUrl(): string {
    return `/${this.slug()}/convite`;
  }

  private storage(): Storage | undefined {
    try {
      return this.document.defaultView?.sessionStorage;
    } catch {
      return undefined;
    }
  }
}
