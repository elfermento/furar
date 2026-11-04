// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.config;

import gal.furar.shared.audit.AuditRetention;
import java.time.Period;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Prazos do rexistro de actividade (ADR-0020, revisión do 2026-10-29; RGPD art. 5.1.e; LOPDGDD art.
 * 32). Un valor que non vale non arrinca ({@link AuditRetention}).
 *
 * @param visible canto se ve cada evento na web ({@code FURAR_AUDIT_VISIBLE}, ISO 8601: {@code
 *     P1Y}); despois queda bloqueado
 * @param retention canto se garda cada evento ({@code FURAR_AUDIT_RETENTION}, ISO 8601: {@code
 *     P3Y}); máis ca {@code visible} e polo menos un ano: a base non borra nada máis novo
 * @param purgeCron cando se borra o que pasou do prazo (unha vez ao día)
 */
@ConfigurationProperties("furar.audit")
public record AuditProperties(
    @DefaultValue("P1Y") Period visible,
    @DefaultValue("P3Y") Period retention,
    @DefaultValue("0 30 3 * * *") String purgeCron) {

  public AuditProperties {
    new AuditRetention(visible, retention);
  }

  /** Os prazos, para o resto da API. */
  public AuditRetention policy() {
    return new AuditRetention(visible, retention);
  }
}
