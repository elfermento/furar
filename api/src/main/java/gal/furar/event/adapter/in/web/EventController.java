// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web;

import gal.furar.event.adapter.in.web.dto.EndEventRequest;
import gal.furar.event.adapter.in.web.dto.EventAttendanceRequest;
import gal.furar.event.adapter.in.web.dto.EventAttendanceResponse;
import gal.furar.event.adapter.in.web.dto.EventAttendanceSummaryResponse;
import gal.furar.event.adapter.in.web.dto.EventCountResponse;
import gal.furar.event.adapter.in.web.dto.EventPaymentRequest;
import gal.furar.event.adapter.in.web.dto.EventRegistrationResponse;
import gal.furar.event.adapter.in.web.dto.EventRequest;
import gal.furar.event.adapter.in.web.dto.EventResponse;
import gal.furar.event.adapter.in.web.dto.EventSessionResponse;
import gal.furar.event.adapter.in.web.dto.EventSessionsPreviewRequest;
import gal.furar.event.adapter.in.web.dto.EventSessionsPreviewResponse;
import gal.furar.event.adapter.in.web.dto.EventTodaySessionResponse;
import gal.furar.event.adapter.in.web.dto.JoinEventRequest;
import gal.furar.event.adapter.in.web.dto.MyEventRegistrationResponse;
import gal.furar.event.adapter.in.web.dto.PublicEventResponse;
import gal.furar.event.application.port.in.EventAttendanceUseCase;
import gal.furar.event.application.port.in.EventRegistrationsUseCase;
import gal.furar.event.application.port.in.EventView;
import gal.furar.event.application.port.in.JoinEventUseCase;
import gal.furar.event.application.port.in.ManageActivityUseCase;
import gal.furar.event.application.port.in.ManageEventsUseCase;
import gal.furar.event.application.port.in.PublicEventsUseCase;
import gal.furar.event.domain.Event;
import gal.furar.event.domain.EventId;
import gal.furar.event.domain.PaymentStatus;
import gal.furar.event.domain.Registration;
import gal.furar.event.domain.RegistrationId;
import gal.furar.shared.security.PublicEndpoint;
import gal.furar.shared.web.ETags;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Eventos do concello (ADR-0038). Controlador fino: DTO ↔ comando e caso de uso; o permiso vive no
 * caso de uso. Públicos ({@link PublicEndpoint}): os próximos e a ficha. Da persoa: apuntarse,
 * desapuntarse e as súas. Da xestión ({@code /manage}): a lista, o detalle, alta e edición con
 * {@code If-Match}, cancelar, eliminar, as persoas, aceptar, rexeitar e o pago. Desde ADR-0041,
 * tamén a persoa a cargo e a lista de espera ({@code dependentId}, confirmar a praza), a vista
 * previa das sesións dunha actividade, as sesións, cancelar unha, rematar antes, pasar lista e as
 * sesións de hoxe.
 */
@RestController
@RequestMapping("/api/v1/municipalities/{slug}/events")
@Tag(name = "events", description = "Eventos do concello")
class EventController {

  private final ManageEventsUseCase manage;
  private final PublicEventsUseCase published;
  private final JoinEventUseCase join;
  private final EventRegistrationsUseCase registrations;
  private final ManageActivityUseCase activity;
  private final EventAttendanceUseCase attendance;
  private final Clock clock;

  EventController(
      ManageEventsUseCase manage,
      PublicEventsUseCase published,
      JoinEventUseCase join,
      EventRegistrationsUseCase registrations,
      ManageActivityUseCase activity,
      EventAttendanceUseCase attendance,
      Clock clock) {
    this.manage = manage;
    this.published = published;
    this.join = join;
    this.registrations = registrations;
    this.activity = activity;
    this.attendance = attendance;
    this.clock = clock;
  }

  // --- Veciñanza ---

  @GetMapping
  @PublicEndpoint
  @Operation(operationId = "listUpcomingEvents", summary = "Próximos eventos publicados")
  List<PublicEventResponse> upcoming(@PathVariable String slug) {
    return published.upcoming().stream()
        .map(v -> EventWebMapper.toPublic(v, slug, clock.instant()))
        .toList();
  }

  @GetMapping("/{id}")
  @PublicEndpoint
  @Operation(operationId = "getPublicEvent", summary = "Ficha dun evento publicado")
  PublicEventResponse get(@PathVariable String slug, @PathVariable UUID id) {
    return EventWebMapper.toPublic(published.published(new EventId(id)), slug, clock.instant());
  }

  @GetMapping("/{id}/registration")
  @Operation(
      operationId = "getMyEventRegistration",
      summary = "A miña inscrición nun evento (ou a dunha persoa a cargo)")
  MyEventRegistrationResponse mine(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestParam(required = false) @Nullable UUID dependentId) {
    EventId event = new EventId(id);
    return join.mine(event, dependentId)
        .map(r -> EventWebMapper.mine(r, slug, clock.instant()))
        .orElseGet(() -> EventWebMapper.none(published.published(event), slug, clock.instant()));
  }

  @GetMapping("/{id}/registrations/mine")
  @Operation(
      operationId = "listMyEventRegistrations",
      summary = "As inscricións da miña conta nun evento (eu e as persoas a cargo)")
  List<MyEventRegistrationResponse> mineAll(@PathVariable String slug, @PathVariable UUID id) {
    return join.mineAll(new EventId(id)).stream()
        .map(r -> EventWebMapper.mine(r, slug, clock.instant()))
        .toList();
  }

  @PostMapping("/{id}/registration")
  @Operation(
      operationId = "joinEvent",
      summary = "Apuntarme, pedir praza ou entrar na lista de espera (eu ou unha persoa a cargo)")
  @ApiResponse(responseCode = "201", description = "Apuntada", useReturnTypeSchema = true)
  ResponseEntity<MyEventRegistrationResponse> joinEvent(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestBody(required = false) @Nullable JoinEventRequest request) {
    JoinEventUseCase.JoinCommand command =
        request == null
            ? JoinEventUseCase.JoinCommand.SELF
            : new JoinEventUseCase.JoinCommand(request.dependentId(), request.birthDate());
    return ResponseEntity.status(201)
        .body(EventWebMapper.mine(join.join(new EventId(id), command), slug, clock.instant()));
  }

  @PostMapping("/{id}/registration/confirm")
  @Operation(
      operationId = "confirmEventSeat",
      summary = "Confirmar a praza libre da que me avisaron desde a lista de espera")
  MyEventRegistrationResponse confirm(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestParam(required = false) @Nullable UUID dependentId) {
    return EventWebMapper.mine(join.confirm(new EventId(id), dependentId), slug, clock.instant());
  }

  @DeleteMapping("/{id}/registration")
  @Operation(operationId = "leaveEvent", summary = "Desapuntarme ou saír da lista de espera")
  @ApiResponse(responseCode = "204", description = "Desapuntada")
  ResponseEntity<Void> leave(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestParam(required = false) @Nullable UUID dependentId) {
    join.leave(new EventId(id), dependentId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/mine")
  @Operation(
      operationId = "listMyEvents",
      summary = "As miñas inscricións en eventos que aínda non empezaron")
  List<MyEventRegistrationResponse> myEvents(@PathVariable String slug) {
    return join.upcoming().stream()
        .map(r -> EventWebMapper.mine(r, slug, clock.instant()))
        .toList();
  }

  // --- Xestión ---

  @GetMapping("/manage")
  @Operation(operationId = "listManagedEvents", summary = "Todos os eventos (xestión)")
  List<EventResponse> list(@PathVariable String slug) {
    return manage.list().stream().map(EventWebMapper::toResponse).toList();
  }

  @GetMapping("/manage/pending-count")
  @Operation(operationId = "countPendingEventRequests", summary = "Solicitudes pendentes")
  EventCountResponse pendingCount(@PathVariable String slug) {
    return new EventCountResponse(registrations.pendingCount());
  }

  @PostMapping("/manage/sessions-preview")
  @Operation(
      operationId = "previewEventSessions",
      summary = "Vista previa das sesións dunha actividade con horario")
  EventSessionsPreviewResponse preview(
      @PathVariable String slug, @Valid @RequestBody EventSessionsPreviewRequest request) {
    return EventWebMapper.preview(
        activity.preview(request.facilityId(), EventWebMapper.schedule(request.schedule())));
  }

  @GetMapping("/manage/sessions/today")
  @Operation(
      operationId = "listTodayEventSessions",
      summary = "As sesións de hoxe do concello, para pasar lista")
  List<EventTodaySessionResponse> today(@PathVariable String slug) {
    return attendance.today().stream().map(EventWebMapper::today).toList();
  }

  @GetMapping("/manage/{id}/sessions")
  @Operation(operationId = "listEventSessions", summary = "As sesións dunha actividade")
  List<EventSessionResponse> sessions(@PathVariable String slug, @PathVariable UUID id) {
    return activity.sessions(new EventId(id)).stream().map(EventWebMapper::session).toList();
  }

  @PostMapping("/manage/{id}/sessions/{sessionId}/cancel")
  @Operation(operationId = "cancelEventSession", summary = "Cancelar unha sesión")
  EventSessionResponse cancelSession(
      @PathVariable String slug, @PathVariable UUID id, @PathVariable UUID sessionId) {
    return EventWebMapper.session(activity.cancelSession(new EventId(id), sessionId));
  }

  @PostMapping("/manage/{id}/end")
  @Operation(operationId = "endEvent", summary = "Rematar antes unha actividade")
  ResponseEntity<EventResponse> end(
      @PathVariable String slug,
      @PathVariable UUID id,
      @Valid @RequestBody EndEventRequest request) {
    return withETag(ResponseEntity.ok(), activity.end(new EventId(id), request.from()));
  }

  @GetMapping("/manage/{id}/sessions/{sessionId}/attendance")
  @Operation(operationId = "getEventAttendance", summary = "A lista dunha sesión")
  EventAttendanceResponse attendance(
      @PathVariable String slug, @PathVariable UUID id, @PathVariable UUID sessionId) {
    return EventWebMapper.attendance(attendance.attendance(new EventId(id), sessionId));
  }

  @PutMapping("/manage/{id}/sessions/{sessionId}/attendance")
  @Operation(operationId = "markEventAttendance", summary = "Gardar a lista dunha sesión")
  EventAttendanceResponse mark(
      @PathVariable String slug,
      @PathVariable UUID id,
      @PathVariable UUID sessionId,
      @Valid @RequestBody EventAttendanceRequest request) {
    Map<UUID, @Nullable Boolean> marks = new HashMap<>();
    request.marks().forEach(m -> marks.put(m.registrationId(), m.present()));
    return EventWebMapper.attendance(attendance.mark(new EventId(id), sessionId, marks));
  }

  @GetMapping("/manage/{id}/attendance")
  @Operation(operationId = "getEventAttendanceSummary", summary = "Resumo da asistencia por persoa")
  List<EventAttendanceSummaryResponse> attendanceSummary(
      @PathVariable String slug, @PathVariable UUID id) {
    return attendance.summary(new EventId(id)).stream().map(EventWebMapper::summary).toList();
  }

  @GetMapping("/manage/{id}")
  @Operation(operationId = "getManagedEvent", summary = "Detalle (xestión)")
  ResponseEntity<EventResponse> detail(@PathVariable String slug, @PathVariable UUID id) {
    return withETag(ResponseEntity.ok(), manage.get(new EventId(id)));
  }

  @PostMapping("/manage")
  @Operation(operationId = "createEvent", summary = "Novo evento")
  @ApiResponse(responseCode = "201", description = "Creado", useReturnTypeSchema = true)
  ResponseEntity<EventResponse> create(
      @PathVariable String slug, @Valid @RequestBody EventRequest request) {
    EventView created = manage.create(EventWebMapper.toCommand(request));
    URI location =
        URI.create(
            "/api/v1/municipalities/" + slug + "/events/manage/" + created.event().id().value());
    return withETag(ResponseEntity.created(location), created);
  }

  @PutMapping("/manage/{id}")
  @Operation(operationId = "updateEvent", summary = "Editar (If-Match)")
  ResponseEntity<EventResponse> update(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch,
      @Valid @RequestBody EventRequest request) {
    EventView updated =
        manage.update(
            new EventId(id), ETags.parseIfMatch(ifMatch), EventWebMapper.toCommand(request));
    return withETag(ResponseEntity.ok(), updated);
  }

  @PostMapping("/manage/{id}/cancel")
  @Operation(operationId = "cancelEvent", summary = "Cancelar o evento (If-Match)")
  ResponseEntity<EventResponse> cancel(
      @PathVariable String slug,
      @PathVariable UUID id,
      @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
    return withETag(
        ResponseEntity.ok(), manage.cancel(new EventId(id), ETags.parseIfMatch(ifMatch)));
  }

  @DeleteMapping("/manage/{id}")
  @Operation(operationId = "deleteEvent", summary = "Eliminar (sen ninguén apuntado)")
  @ApiResponse(responseCode = "204", description = "Eliminado")
  ResponseEntity<Void> delete(@PathVariable String slug, @PathVariable UUID id) {
    manage.delete(new EventId(id));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/manage/{id}/registrations")
  @Operation(operationId = "listEventRegistrations", summary = "As persoas dun evento")
  List<EventRegistrationResponse> registrations(@PathVariable String slug, @PathVariable UUID id) {
    EventId event = new EventId(id);
    Event found = manage.get(event).event();
    int position = 0;
    List<EventRegistrationResponse> out = new ArrayList<>();
    for (Registration r : registrations.list(event)) {
      Integer place = null;
      if (r.status().waitlisted()) {
        place = ++position;
      }
      out.add(EventWebMapper.registration(r, found, place));
    }
    return out;
  }

  @PostMapping("/manage/{id}/registrations/{registrationId}/accept")
  @Operation(operationId = "acceptEventRequest", summary = "Aceptar unha solicitude")
  EventRegistrationResponse accept(
      @PathVariable String slug, @PathVariable UUID id, @PathVariable UUID registrationId) {
    EventId event = new EventId(id);
    return EventWebMapper.registration(
        registrations.accept(event, new RegistrationId(registrationId)),
        manage.get(event).event(),
        null);
  }

  @PostMapping("/manage/{id}/registrations/{registrationId}/reject")
  @Operation(operationId = "rejectEventRequest", summary = "Rexeitar unha solicitude")
  EventRegistrationResponse reject(
      @PathVariable String slug, @PathVariable UUID id, @PathVariable UUID registrationId) {
    EventId event = new EventId(id);
    return EventWebMapper.registration(
        registrations.reject(event, new RegistrationId(registrationId)),
        manage.get(event).event(),
        null);
  }

  @PutMapping("/manage/{id}/registrations/{registrationId}/payment")
  @Operation(operationId = "setEventPayment", summary = "Marcar o pago")
  EventRegistrationResponse pay(
      @PathVariable String slug,
      @PathVariable UUID id,
      @PathVariable UUID registrationId,
      @Valid @RequestBody EventPaymentRequest request) {
    EventId event = new EventId(id);
    return EventWebMapper.registration(
        registrations.pay(
            event,
            new RegistrationId(registrationId),
            PaymentStatus.valueOf(request.status().name())),
        manage.get(event).event(),
        null);
  }

  private static ResponseEntity<EventResponse> withETag(
      ResponseEntity.BodyBuilder builder, EventView view) {
    Long version = view.event().version();
    return builder
        .eTag(ETags.of(version == null ? 0 : version))
        .body(EventWebMapper.toResponse(view));
  }
}
