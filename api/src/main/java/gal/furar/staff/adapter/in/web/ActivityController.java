// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web;

import gal.furar.shared.audit.AuditCursor;
import gal.furar.shared.audit.AuditEvent.Outcome;
import gal.furar.shared.security.Role;
import gal.furar.staff.adapter.in.web.dto.ActivityActorKindValue;
import gal.furar.staff.adapter.in.web.dto.ActivityActorResponse;
import gal.furar.staff.adapter.in.web.dto.ActivityEventResponse;
import gal.furar.staff.adapter.in.web.dto.ActivityOutcomeValue;
import gal.furar.staff.adapter.in.web.dto.ActivityPageResponse;
import gal.furar.staff.adapter.in.web.dto.ActivityRoleValue;
import gal.furar.staff.adapter.in.web.dto.ActivityTargetKindValue;
import gal.furar.staff.adapter.in.web.dto.ActivityTargetResponse;
import gal.furar.staff.adapter.in.web.dto.BlockedActivityExportResponse;
import gal.furar.staff.adapter.in.web.dto.ExportBlockedActivityRequest;
import gal.furar.staff.application.port.in.ActivityEntry;
import gal.furar.staff.application.port.in.ActivityPage;
import gal.furar.staff.application.port.in.ActivityQuery;
import gal.furar.staff.application.port.in.BlockedActivityExport;
import gal.furar.staff.application.port.in.ExportBlockedActivityCommand;
import gal.furar.staff.application.port.in.ExportBlockedActivityCommand.DisclosureReason;
import gal.furar.staff.application.port.in.ExportBlockedActivityUseCase;
import gal.furar.staff.application.port.in.ReadActivityUseCase;
import gal.furar.staff.application.port.in.ReadPlatformActivityUseCase;
import gal.furar.staff.domain.StaffMemberId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rexistro de actividade (plan do dominio 4, §1.2.12 e §2.3): o do concello para a xestión e o da
 * plataforma. Controlador fino; o permiso vive no caso de uso. Páxinas de 50 por cursor opaco. As
 * listas só devolven o tramo visible ({@code FURAR_AUDIT_VISIBLE}); os eventos bloqueados só saen
 * pola exportación do xestor, cun motivo (ADR-0020, revisión do 2026-10-29).
 */
@RestController
@Tag(name = "activity", description = "Rexistro de actividade")
class ActivityController {

  private static final String PROBLEM = "#/components/schemas/Problem";
  private static final String FROM =
      "Comezo do período, incluído (ISO 8601). Sen el, 366 días antes do fin. O anterior ao tramo"
          + " visible (FURAR_AUDIT_VISIBLE, un ano) non se devolve: está bloqueado";
  private static final String TO = "Fin do período, excluído (ISO 8601). Sen el, agora";
  private static final String CURSOR = "O nextCursor da páxina anterior; sen el, a primeira";
  private static final String RANGE_INVALID =
      "audit.range-invalid: o período non comeza antes de rematar ou dura máis de 366 días";

  private final ReadActivityUseCase activity;
  private final ReadPlatformActivityUseCase platformActivity;
  private final ExportBlockedActivityUseCase exportBlocked;

  ActivityController(
      ReadActivityUseCase activity,
      ReadPlatformActivityUseCase platformActivity,
      ExportBlockedActivityUseCase exportBlocked) {
    this.activity = activity;
    this.platformActivity = platformActivity;
    this.exportBlocked = exportBlocked;
  }

  @GetMapping("/api/v1/municipalities/{slug}/audit-events")
  @Operation(
      operationId = "listActivity",
      summary =
          "O rexistro de actividade do concello, do máis novo ao máis vello: todo o que pasou"
              + " nel, tamén o que fixo a plataforma (actor.kind PLATFORM ou PLATFORM_ADMIN en"
              + " actor.roles)")
  @ApiResponse(responseCode = "200", description = "Unha páxina", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = RANGE_INVALID,
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  ActivityPageResponse list(
      @PathVariable String slug,
      @Parameter(description = FROM)
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          @Nullable Instant from,
      @Parameter(description = TO)
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          @Nullable Instant to,
      @Parameter(
              description = "Área: o prefixo da acción (staff, facility, booking, municipality)",
              schema = @Schema(pattern = "^[a-z]+(-[a-z]+)*$"))
          @RequestParam(required = false)
          @Nullable String area,
      @Parameter(description = "Só o que fixo a persoa desta pertenza (tamén desactivada)")
          @RequestParam(required = false)
          @Nullable UUID memberId,
      @RequestParam(required = false) @Nullable ActivityOutcomeValue outcome,
      @Parameter(description = CURSOR) @RequestParam(required = false) @Nullable String cursor) {
    return toResponse(
        activity.read(
            new ActivityQuery(
                from,
                to,
                area,
                memberId == null ? null : new StaffMemberId(memberId),
                outcome == null ? null : Outcome.valueOf(outcome.name()),
                cursor(cursor))));
  }

  @PostMapping("/api/v1/municipalities/{slug}/audit-events/blocked-exports")
  @Operation(
      operationId = "exportBlockedActivity",
      summary =
          "Exportar os eventos bloqueados do concello (os que xa non se ven e aínda se gardan) dun"
              + " período, do máis vello ao máis novo, coa identidade do persoal. Só o xestor e cun"
              + " motivo e unha referencia, que quedan no rexistro (audit.export-blocked)")
  @ApiResponse(responseCode = "200", description = "Os eventos", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description =
          RANGE_INVALID
              + "; audit.export-not-blocked: o período non ten tramo bloqueado;"
              + " audit.export-too-large: máis de 10000 eventos;"
              + " audit.export-reference-invalid: falta a referencia ou é longa de máis",
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  BlockedActivityExportResponse exportBlocked(
      @PathVariable String slug, @Valid @RequestBody ExportBlockedActivityRequest request) {
    BlockedActivityExport export =
        exportBlocked.export(
            new ExportBlockedActivityCommand(
                request.from(),
                request.to(),
                DisclosureReason.valueOf(request.reason().name()),
                request.reference()));
    return new BlockedActivityExportResponse(
        export.from(),
        export.to(),
        export.entries().stream().map(ActivityController::toResponse).toList());
  }

  @GetMapping("/api/v1/audit-events")
  @Operation(
      operationId = "listPlatformActivity",
      summary =
          "O rexistro de actividade da plataforma: as accións feitas como PLATFORM_ADMIN en"
              + " todos os concellos e as que non teñen concello, do máis novo ao máis vello")
  @ApiResponse(responseCode = "200", description = "Unha páxina", useReturnTypeSchema = true)
  @ApiResponse(
      responseCode = "400",
      description = RANGE_INVALID,
      content = @Content(mediaType = "application/problem+json", schema = @Schema(ref = PROBLEM)))
  ActivityPageResponse listPlatform(
      @Parameter(description = FROM)
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          @Nullable Instant from,
      @Parameter(description = TO)
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          @Nullable Instant to,
      @Parameter(description = CURSOR) @RequestParam(required = false) @Nullable String cursor) {
    return toResponse(
        platformActivity.readPlatform(ActivityQuery.period(from, to, cursor(cursor))));
  }

  private static @Nullable AuditCursor cursor(@Nullable String cursor) {
    return cursor == null || cursor.isBlank() ? null : AuditCursor.parse(cursor);
  }

  private static ActivityPageResponse toResponse(ActivityPage page) {
    return new ActivityPageResponse(
        page.entries().stream().map(ActivityController::toResponse).toList(),
        page.next() == null ? null : page.next().token());
  }

  private static ActivityEventResponse toResponse(ActivityEntry e) {
    ActivityEntry.Actor actor = e.actor();
    ActivityEntry.Target target = e.target();
    return new ActivityEventResponse(
        e.id(),
        e.at(),
        e.action(),
        ActivityOutcomeValue.valueOf(e.outcome().name()),
        new ActivityActorResponse(
            ActivityActorKindValue.valueOf(actor.kind().name()),
            actor.name(),
            actor.email(),
            actor.roles().stream()
                .sorted()
                .map(Role::name)
                .map(ActivityRoleValue::valueOf)
                .toList(),
            actor.blocked()),
        target == null
            ? null
            : new ActivityTargetResponse(
                ActivityTargetKindValue.valueOf(target.kind().name()),
                target.id(),
                target.memberEmail()),
        e.municipalitySlug(),
        e.requestId());
  }
}
