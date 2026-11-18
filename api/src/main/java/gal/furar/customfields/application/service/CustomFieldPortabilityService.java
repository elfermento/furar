// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.application.service;

import gal.furar.customfields.application.port.in.ExportCustomFieldDefinitionsUseCase;
import gal.furar.customfields.application.port.in.ImportCustomFieldDefinitionsUseCase;
import gal.furar.customfields.application.port.in.RestoreCustomFieldDefinitionCommand;
import gal.furar.customfields.application.port.out.CustomFieldDefinitionRepository;
import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.CustomFieldDefinitionId;
import gal.furar.customfields.domain.CustomFieldError;
import gal.furar.customfields.domain.Visibility;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.portability.PortabilityPermission;
import gal.furar.shared.security.RequiresPermission;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sección {@code customfields} do ficheiro {@code furar-export} (ADR-0018). */
@Service
public class CustomFieldPortabilityService
    implements ExportCustomFieldDefinitionsUseCase, ImportCustomFieldDefinitionsUseCase {

  private final CustomFieldDefinitionRepository repository;
  private final CustomFieldTargets targets;

  public CustomFieldPortabilityService(
      CustomFieldDefinitionRepository repository, CustomFieldTargets targets) {
    this.repository = repository;
    this.targets = targets;
  }

  @Override
  @Transactional(readOnly = true)
  @RequiresPermission(PortabilityPermission.Code.EXPORT)
  public List<CustomFieldDefinition> exportAll() {
    return repository.findAll();
  }

  /**
   * Cada definición pasa as comprobacións dunha nova (código, tipo, opcións, usos, RGPD) e o límite
   * de activas por destino; únese á transacción da importación do concello.
   */
  @Override
  @Transactional
  @RequiresPermission(PortabilityPermission.Code.IMPORT)
  public void importAll(List<RestoreCustomFieldDefinitionCommand> definitions) {
    Map<CustomFieldTarget, Integer> active = new HashMap<>();
    for (RestoreCustomFieldDefinitionCommand c : definitions) {
      CustomFieldDefinition definition =
          CustomFieldDefinition.restore(
              new CustomFieldDefinitionId(c.id()),
              c.code(),
              c.type(),
              c.spec(),
              c.status(),
              c.createdAt(),
              c.updatedAt());
      // Unha reserva non ten campos públicos (tamén a base o rexeita).
      for (var usage : definition.spec().usages()) {
        if (usage.visibility() == Visibility.PUBLIC && !targets.allowsPublic(usage.target())) {
          throw new DomainException(
              CustomFieldError.VISIBILITY_NOT_ALLOWED,
              Map.of("target", usage.target().code(), "visibility", usage.visibility().name()));
        }
      }
      if (definition.isActive()) {
        for (CustomFieldTarget target : definition.targets()) {
          if (active.merge(target, 1, Integer::sum)
              > CustomFieldDefinitionService.MAX_ACTIVE_DEFINITIONS) {
            throw new DomainException(
                CustomFieldError.LIMIT_EXCEEDED,
                Map.of("limit", CustomFieldDefinitionService.MAX_ACTIVE_DEFINITIONS));
          }
        }
      }
      repository.insert(definition);
    }
  }
}
