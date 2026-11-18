// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import { StaffAccessService } from '../../core/auth/staff-access.service';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { LanguageService } from '../../core/i18n/language.service';
import {
  activityDates,
  ageText,
  eventWhen,
  hasAgeLimit,
  inLang,
  priceText,
  scheduleWhen,
} from '../../shared/events/event-format';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { DialogComponent } from '../../shared/ui/dialog/dialog.component';
import { DialogService } from '../../shared/ui/dialog/dialog.service';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { MenuDirective, MenuItemDirective, MenuTriggerDirective } from '../../shared/ui/menu/menu.directives';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import type { SelectOption } from '../../shared/ui/select/select.component';
import { TabDirective, TabsComponent } from '../../shared/ui/tabs/tabs.component';
import { ToastService } from '../../shared/ui/toast/toast.service';
import type { ValueListItem } from '../../shared/ui/value-list/value-list.component';
import { ValueListComponent } from '../../shared/ui/value-list/value-list.component';
import type { EndActivityData } from './end-activity-dialog.component';
import { EventPeopleComponent } from './event-people.component';
import { EventSessionsComponent } from './event-sessions.component';
import { isOpen, placeText, statusOf } from './events';
import type { EventResponse, EventSessionResponse } from './events.store';
import { EventsStore } from './events.store';

export interface EventDetailData {
  readonly slug: string;
  readonly event: EventResponse;
  /** As instalacións do concello, para «Editar» e «Duplicar». */
  readonly facilities: readonly SelectOption<string>[];
  /** A pestana coa que se abre. */
  readonly tab?: DetailTab;
}

export type DetailTab = 'data' | 'people' | 'sessions';

/** Abre o detalle dun evento ou dunha actividade e resolve ao pechalo. */
export async function openEventDetail(dialogs: DialogService, data: EventDetailData): Promise<void> {
  const ref = dialogs.open<unknown, EventDetailData>(EventDetailDialogComponent, { data });
  await new Promise<void>((resolve) => ref.closed.subscribe({ complete: resolve }));
}

/**
 * O detalle dun evento nun diálogo grande (a pantalla enteira no móbil): o nome e, debaixo,
 * cando e onde; pestanas «Datos · Persoas · Sesións» (sen «Sesións» nun evento dun día; sen
 * «Persoas» para quen non ve datos da veciñanza) e o menú «⋯» con «Duplicar» e «Cancelar o evento»
 * ou «Eliminar» (sen ninguén apuntado). Ao pé, segundo a pestana: «Editar» (Datos) ou «Rematar a
 * actividade…» (Sesións). Recepción leo sen cambiar nada e pasa lista.
 */
@Component({
  selector: 'furar-event-detail-dialog',
  imports: [
    ButtonDirective,
    DialogComponent,
    EventPeopleComponent,
    EventSessionsComponent,
    IconComponent,
    MenuDirective,
    MenuItemDirective,
    MenuTriggerDirective,
    ProblemAlertComponent,
    TabDirective,
    TabsComponent,
    TranslocoPipe,
    ValueListComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './event-detail-dialog.component.html',
})
export class EventDetailDialogComponent {
  protected readonly data = inject<EventDetailData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<unknown>>(DialogRef);
  private readonly store = inject(EventsStore);
  private readonly dialogs = inject(DialogService);
  private readonly toasts = inject(ToastService);
  private readonly transloco = inject(TranslocoService);
  private readonly language = inject(LanguageService);
  private readonly staff = inject(StaffAccessService);
  private readonly router = inject(Router);

  protected readonly event = signal<EventResponse>(this.data.event);
  protected readonly tab = signal<DetailTab>(this.data.tab ?? 'data');
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly sessions = signal<readonly EventSessionResponse[]>([]);

  /** `event.manage`: Xestión. */
  protected readonly canManage = computed(() => this.staff.role() === 'MANAGER');
  /** Ver as persoas e pasar lista: Xestión e Recepción (a plataforma non ve persoas). */
  protected readonly seesPeople = computed(() => this.staff.role() !== null);
  protected readonly scheduled = computed(() => this.event().kind === 'SCHEDULED');
  protected readonly open = computed(() => isOpen(this.event()));
  /** Aínda non rematou antes de tempo. */
  protected readonly endable = computed(
    () => this.scheduled() && this.open() && this.canManage() && !this.event().schedule?.endedFrom,
  );
  protected readonly hasPeople = computed(() => {
    const event = this.event();
    return event.confirmed + event.pending + (event.waiting ?? 0) + (event.notified ?? 0) > 0;
  });
  protected readonly name = computed(() => inLang(this.event().name, this.language.lang()));

  private readonly t = (key: string, params?: Record<string, unknown>) => this.transloco.translate(key, params);

  /** «Luns e mércores · 17:00–18:00 · Piscina municipal» ou «domingo, 20 de decembro · 11:00 · …». */
  protected readonly subheading = computed(() => {
    const event = this.event();
    const lang = this.language.lang();
    const when = event.schedule ? scheduleWhen(event.schedule, lang, this.t) : eventWhen(event.startsAt, lang);
    return [when, event.facility?.name ?? event.place].filter(Boolean).join(' · ');
  });

  protected readonly rows = computed<ValueListItem[]>(() => {
    const event = this.event();
    const lang = this.language.lang();
    const t = this.t;
    const label = (key: string) => t('event.detail.' + key);
    const rows: ValueListItem[] = [];
    if (event.schedule) {
      const count = this.sessions().filter((s) => !s.cancelledAt).length;
      const dates = activityDates(event, lang, t);
      rows.push(
        { id: 'when', label: label('when'), value: scheduleWhen(event.schedule, lang, t) },
        {
          id: 'dates',
          label: label('dates'),
          value: count ? `${dates} · ${t('event.detail.sessionsCount', { count })}` : dates,
        },
      );
    } else {
      rows.push({ id: 'when', label: label('when'), value: eventWhen(event.startsAt, lang) });
    }
    rows.push(
      { id: 'place', label: label('place'), value: placeText(event) },
      {
        id: 'seats',
        label: label('seats'),
        value: event.capacity ? String(event.capacity) : t('event.public.noLimit'),
      },
      { id: 'price', label: label('price'), value: priceText(event, t) || t('event.public.freeOfCharge') },
    );
    if (hasAgeLimit(event)) rows.push({ id: 'age', label: label('age'), value: ageText(event, t) });
    rows.push({ id: 'status', label: label('status'), value: t('event.status.' + statusOf(event)) });
    return rows;
  });

  protected async edit(copy = false): Promise<void> {
    const { openEventDialog } = await import('./event-dialog.component');
    const saved = await openEventDialog(this.dialogs, {
      slug: this.data.slug,
      event: this.event(),
      copy,
      facilities: this.data.facilities,
    });
    if (!saved) return;
    if (copy) this.ref.close();
    else this.event.set(saved);
  }

  protected async end(): Promise<void> {
    const { EndActivityDialogComponent } = await import('./end-activity-dialog.component');
    const data: EndActivityData = {
      slug: this.data.slug,
      event: this.event(),
      name: this.name(),
      sessions: this.sessions(),
    };
    const ref = this.dialogs.open<EventResponse, EndActivityData>(EndActivityDialogComponent, { data });
    const saved = await firstValueFrom(ref.closed, { defaultValue: undefined });
    if (saved) {
      this.event.set(saved);
      this.toasts.show('event.end.done');
    }
  }

  /** «Pasar lista»: pecha o detalle e vai á súa pantalla (pensada para o móbil). */
  protected async attend(session: EventSessionResponse): Promise<void> {
    this.ref.close();
    await this.router.navigate(['/', this.data.slug, 'xestion', 'eventos', this.event().id, 'sesions', session.id]);
  }

  /** Aceptar ou rexeitar cambia os números do evento: lense da lista xa recargada. */
  protected peopleChanged(): void {
    const fresh = this.store.items().find((item) => item.id === this.event().id);
    if (fresh) this.event.set(fresh);
  }

  protected async cancelEvent(): Promise<void> {
    const event = this.event();
    const count = event.confirmed + event.pending + (event.waiting ?? 0) + (event.notified ?? 0);
    try {
      const done = await this.dialogs.confirm({
        heading: this.t('event.form.cancelConfirm.title', { name: this.name() }),
        message: this.t('event.form.cancelConfirm.text', { count }),
        confirmLabel: this.t(this.scheduled() ? 'event.form.cancelActivity' : 'event.form.cancelEvent'),
        tone: 'danger',
        action: () => this.store.cancel(this.data.slug, event),
      });
      if (done) {
        this.toasts.show('event.form.cancelled');
        this.ref.close();
      }
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }

  protected async remove(): Promise<void> {
    const event = this.event();
    try {
      const done = await this.dialogs.confirm({
        heading: this.t('event.form.deleteConfirm.title', { name: this.name() }),
        message: this.t('event.form.deleteConfirm.text'),
        confirmLabel: this.t('event.form.delete'),
        tone: 'danger',
        action: () => this.store.remove(this.data.slug, event),
      });
      if (done) {
        this.toasts.show('event.form.deleted');
        this.ref.close();
      }
    } catch (error) {
      this.problem.set(asProblem(error));
    }
  }
}
