// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, DestroyRef, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CalloutComponent } from '../../../shared/ui/callout/callout.component';
import { ToastService } from '../../../shared/ui/toast/toast.service';
import type { ConfirmDetail } from '../../../shared/ui/dialog/confirm.component';
import { countdownClock } from '../../../shared/ui/countdown/countdown';
import { DialogService } from '../../../shared/ui/dialog/dialog.service';
import { IconComponent, registerSymbols } from '../../../shared/ui/icon/icon.component';
import { LAZY_SYMBOLS } from '../../../shared/ui/icon/lazy-symbols';
import { PageHeaderComponent } from '../../../shared/ui/page-header/page-header.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import type { ChangeRoleData, ChangeRoleResult } from './change-role-dialog.component';
import { ChangeRoleDialogComponent } from './change-role-dialog.component';
import type { InviteStaffData, InviteStaffResult } from './invite-staff-dialog.component';
import { InviteStaffDialogComponent } from './invite-staff-dialog.component';
import type { MemberActionRequest } from './staff-members-list.component';
import { StaffMembersListComponent } from './staff-members-list.component';
import type { StaffMemberResponse, StaffMode } from './staff-members';
import {
  RATE_LIMITED,
  memberLabel,
  memberState,
  resendBlock,
  resendDeadlines,
  retryWait,
  splitMembers,
} from './staff-members';
import { StaffMembersStore } from './staff-members.store';

// As iconas da páxina que non van na carga inicial (accións, invitación).
registerSymbols(LAZY_SYMBOLS);

/** Desde este ancho, táboa; por debaixo, tarxetas. */
const DESKTOP_QUERY = '(min-width: 56rem)';

/**
 * «Persoal» (`/:concello/xestion/persoal`, D4-14; especificación, bloque 7): a lista do persoal e
 * as desactivadas á parte, «Invitar» e, por fila, Reenviar, Cambiar o rol, Desactivar e Reactivar.
 * A xestión e a plataforma (`staffGuard`) fan o mesmo (ADR-0027, revisión do 2026-11-02).
 * Desactivar pide escribir «desactivar» e marcar a casiña do que pasa (non se borra nada); pasar de
 * Xestión a Recepción, escribir «recepción» (P7, 2026-10-30). O que sae ben, en toast; se o correo non
 * se puido enviar, aviso fixo; os erros (409, 429 co tempo de `Retry-After`) en `role="alert"`; un
 * 412 avisa e recarga a lista. O foco volve sempre a quen abriu a capa.
 * «Reenviar» queda desactivado coa conta atrás mentres a API di que hai que esperar; o reloxo só
 * corre mentres queda algunha conta e, cando unha remata, un único aviso en `role="status"`.
 */
@Component({
  selector: 'furar-staff-members-page',
  imports: [
    ButtonDirective,
    CalloutComponent,
    IconComponent,
    PageHeaderComponent,
    StaffMembersListComponent,
    StatusStateComponent,
    TranslocoPipe,
  ],
  providers: [StaffMembersStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './staff-members.page.html',
})
export class StaffMembersPage implements OnInit {
  /** Dentro da sección «Concello» (`data.section`): o h1 é o da sección. */
  protected readonly inSection = inject(ActivatedRoute).snapshot.data['section'] === true;

  protected readonly store = inject(StaffMembersStore);
  private readonly staff = inject(StaffAccessService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly dialogs = inject(DialogService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly toasts = inject(ToastService);
  private readonly document = inject(DOCUMENT);
  private readonly view = this.document.defaultView;

  /** A xestión do concello ou, se non o é, a plataforma (a garda xa deixou pasar só a estas). */
  protected readonly mode = computed<StaffMode>(() => (this.staff.canManage() ? 'manager' : 'platform'));
  protected readonly desktop = signal(this.view?.matchMedia?.(DESKTOP_QUERY).matches ?? true);
  /** Agora (ms): avanza cada segundo só mentres hai algunha conta atrás de «Reenviar». */
  protected readonly now = countdownClock(() => resendDeadlines(this.store.members()));
  /** «Xa podes reenviar a invitación a …»: unha vez por conta atrás, só para o lector. */
  protected readonly resendReady = signal('');
  /** Quen tiña a conta atrás en marcha na última volta do reloxo. */
  private readonly waiting = new Set<string>();
  protected readonly groups = computed(() => splitMembers(this.store.members()));
  /** A persoa coa que se está a facer algo. */
  protected readonly busyId = signal<string | null>(null);
  /** O correo da invitación non saíu: aviso fixo, con «Reenviar» na fila. */
  protected readonly warning = signal('');
  protected readonly alert = signal('');
  /** 412: alguén cambiou a persoa mentres tanto; a lista xa se pediu de novo. */
  protected readonly stale = signal(false);

  private readonly slug = computed(() => this.municipality.slug() ?? '');

  constructor() {
    const query = this.view?.matchMedia?.(DESKTOP_QUERY);
    const onChange = (event: MediaQueryListEvent) => this.desktop.set(event.matches);
    query?.addEventListener?.('change', onChange);
    inject(DestroyRef).onDestroy(() => query?.removeEventListener?.('change', onChange));
    effect(() => this.watchResends(this.now()));
  }

  /** Cando remata unha conta atrás (non cando se gastan os envíos), avísase unha vez. */
  private watchResends(now: number): void {
    for (const member of this.store.members()) {
      const block = resendBlock(member, now);
      if (block?.kind === 'wait') this.waiting.add(member.id);
      else if (this.waiting.delete(member.id) && !block) {
        this.resendReady.set(this.transloco.translate('staff.members.resendWait.ready', { name: memberLabel(member) }));
      }
    }
  }

  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    if (!this.slug()) return;
    await this.store.load(this.slug());
    this.now.set(Date.now());
  }

  /** «Invitar»: ao pechar, o foco volve ao botón da cabeceira e anúnciase a quen se enviou. */
  protected async openInvite(): Promise<void> {
    this.reset();
    const data: InviteStaffData = {
      language: this.municipality.value()?.defaultLanguage ?? this.language.lang(),
      invite: (body) => this.store.invite(this.slug(), body),
    };
    const ref = this.dialogs.open<InviteStaffResult, InviteStaffData>(InviteStaffDialogComponent, { data });
    const created = await firstValueFrom(ref.closed, { defaultValue: undefined });
    this.focusLater(() => this.inviteButton());
    if (!created) return;
    this.now.set(Date.now());
    this.announce(created.invitation?.delivery === 'FAILED' ? 'invitedFailed' : 'invited', created);
  }

  protected async run({ member, action }: MemberActionRequest): Promise<void> {
    if (this.busyId()) return;
    this.reset();
    // Quen pediu a acción («Reenviar» da fila) ou, se era o menú (que se pecha), o seu botón.
    const origin = this.document.activeElement as HTMLElement | null;
    if (action === 'resend') await this.resend(member);
    else if (action === 'role') await this.changeRole(member);
    else if (action === 'reactivate') await this.reactivate(member);
    else await this.deactivate(member);
    this.focusLater(() =>
      origin?.isConnected && !origin.closest('.cdk-overlay-container') ? origin : this.rowTrigger(member.id),
    );
  }

  private async resend(member: StaffMemberResponse): Promise<void> {
    this.busyId.set(member.id);
    try {
      const sent = await this.store.resend(this.slug(), member);
      this.now.set(Date.now());
      this.announce(sent.invitation?.delivery === 'FAILED' ? 'resentFailed' : 'resent', sent);
    } catch (error) {
      await this.fail(error);
    } finally {
      this.busyId.set(null);
    }
  }

  private async changeRole(member: StaffMemberResponse): Promise<void> {
    const data: ChangeRoleData = {
      member,
      change: (role) => this.store.changeRole(this.slug(), member, role),
    };
    const trigger = this.rowTrigger(member.id);
    const ref = this.dialogs.open<ChangeRoleResult, ChangeRoleData>(ChangeRoleDialogComponent, {
      data,
      ...(trigger ? { restoreFocus: trigger } : {}),
    });
    const result = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (result && 'failed' in result) await this.fail(result.failed);
    else if (result)
      this.announce('roleChanged', result, {
        role: this.transloco.translate(`staff.members.role.${result.role}`),
      });
  }

  /**
   * «Desactivar», co que perde, escribindo «desactivar» e coa casiña do que pasa: non se borra a
   * conta nin o historial. Despois, a fila pasa ás desactivadas.
   */
  private async deactivate(member: StaffMemberResponse): Promise<void> {
    const t = (key: string, params: Record<string, unknown> = {}) =>
      this.transloco.translate(`staff.members.deactivate.${key}`, params);
    const accepted = !!member.acceptedAt;
    const state = memberState(member);
    const loses = state === 'ACTIVE' ? `loses${member.role}` : 'losesInvitation';
    const details: ConfirmDetail[] = [
      { icon: 'user', title: memberLabel(member), text: member.name?.trim() ?? '', tone: 'primary' },
      { icon: 'warn', text: t(loses), tone: 'danger' },
      { icon: 'info', text: t(accepted ? 'again' : 'inviteAgain') },
    ];
    try {
      const done = await this.dialogs.confirm({
        heading: t('title'),
        details,
        confirmLabel: t('confirm'),
        busyLabel: t('busy'),
        tone: 'danger',
        typeToConfirm: t('phrase'),
        acknowledgement: t(accepted ? 'acknowledge' : 'acknowledgeInvitation'),
        action: () => this.store.deactivate(this.slug(), member),
      });
      if (done) this.announce('deactivated', member);
    } catch (error) {
      await this.fail(error);
    }
  }

  /** «Reactivar»: volve co mesmo rol, sen invitación nova. Despois, a fila volve ao persoal en uso. */
  private async reactivate(member: StaffMemberResponse): Promise<void> {
    const t = (key: string, params: Record<string, unknown> = {}) =>
      this.transloco.translate(`staff.members.reactivate.${key}`, params);
    const role = this.transloco.translate(`staff.members.role.${member.role}`);
    const details: ConfirmDetail[] = [
      { icon: 'user', title: memberLabel(member), text: member.name?.trim() ?? '', tone: 'primary' },
      { icon: 'undo', text: t('returns', { role }), tone: 'success' },
      { icon: 'info', text: t('signIn') },
    ];
    try {
      const done = await this.dialogs.confirm({
        heading: t('title'),
        details,
        confirmLabel: t('confirm'),
        busyLabel: t('busy'),
        action: () => this.store.reactivate(this.slug(), member),
      });
      if (done) this.announce('reactivated', member, { role });
    } catch (error) {
      await this.fail(error);
    }
  }

  /** O que saíu ben, en toast; se o correo non saíu (`…Failed`), aviso fixo na páxina. */
  private announce(key: string, member: StaffMemberResponse, params: Record<string, unknown> = {}): void {
    const all = { name: memberLabel(member), email: member.email, ...params };
    if (key.endsWith('Failed')) this.warning.set(this.transloco.translate(`staff.members.done.${key}`, all));
    else this.toasts.show(`staff.members.done.${key}`, all);
  }

  /** 412 e 404: aviso e lista ao día; 429: canto esperar; o resto, o texto do erro. */
  private async fail(error: unknown): Promise<void> {
    const problem = asProblem(error);
    if (problem.status === 412 || problem.status === 404) {
      this.stale.set(true);
      await this.load();
      return;
    }
    if (problem.code === RATE_LIMITED) {
      const wait = retryWait(problem);
      this.alert.set(
        this.transloco.translate(
          wait ? `staff.members.rateLimited.${wait.key}` : 'staff.members.rateLimited.spent',
          wait ? { count: wait.count } : {},
        ),
      );
      return;
    }
    this.alert.set(problem.translated || this.transloco.translate(problem.messageKey, problem.params));
  }

  private reset(): void {
    this.warning.set('');
    this.alert.set('');
    this.stale.set(false);
  }

  private rowTrigger(id: string): HTMLElement | null {
    return this.document.querySelector<HTMLElement>(`[data-actions="${id}"]`);
  }

  private inviteButton(): HTMLElement | null {
    return this.document.querySelector<HTMLElement>('[data-invite]');
  }

  /**
   * Foco despois de que a capa ou o menú devolvan o seu (CDK): se o elemento xa non está (a fila
   * pasou ás baixas), ao botón «Invitar».
   */
  private focusLater(pick: () => HTMLElement | null | undefined): void {
    this.view?.setTimeout(() => (pick() ?? this.inviteButton())?.focus(), 0);
  }
}
