// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.testsupport;

import gal.furar.customfields.application.port.out.CustomFieldDefinitionRepository;
import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.CustomFieldDefinitionId;
import gal.furar.customfields.domain.DefinitionSpec;
import gal.furar.customfields.domain.FieldType;
import gal.furar.customfields.domain.FieldUsage;
import gal.furar.customfields.domain.Visibility;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.tenancy.TenantAware;
import gal.furar.shared.tenancy.TenantContext;
import gal.furar.shared.tenancy.TenantRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.modulith.events.ApplicationModuleListener;

/**
 * Evento e escoitador asíncrono de proba para {@code AsyncListenerTenantIT}: le e escribe no
 * concello do evento, nun fío distinto e nunha transacción nova.
 */
public final class ProbeEvents {

  private ProbeEvents() {}

  /** Evento de proba cun concello. */
  public record ProbeEvent(UUID municipalityId, String code) implements TenantAware {}

  /** O que viu o escoitador. */
  public record Observation(UUID tenant, int definitionsBefore, String thread) {}

  @TestComponent
  public static class ProbeListener {

    public static final Map<String, Observation> SEEN = new ConcurrentHashMap<>();

    private final CustomFieldDefinitionRepository repository;

    public ProbeListener(CustomFieldDefinitionRepository repository) {
      this.repository = repository;
    }

    @ApplicationModuleListener
    void on(ProbeEvent event) {
      UUID tenant = TenantContext.current().map(TenantRef::id).orElse(null);
      int before = repository.findByTarget(CustomFieldTarget.MUNICIPALITY).size();
      repository.insert(
          CustomFieldDefinition.create(
              CustomFieldDefinitionId.newId(),
              event.code(),
              FieldType.BOOLEAN,
              new DefinitionSpec(
                  new LocalizedText("Proba", "Prueba"),
                  null,
                  null,
                  List.of(),
                  false,
                  List.of(new FieldUsage(CustomFieldTarget.MUNICIPALITY, false, Visibility.STAFF))),
              Instant.now()));
      SEEN.put(event.code(), new Observation(tenant, before, Thread.currentThread().getName()));
    }
  }
}
