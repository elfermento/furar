// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.adapter.out.persistence;

import gal.furar.people.application.port.out.DisplayPreferenceRepository;
import gal.furar.people.domain.DisplayPreferences;
import gal.furar.shared.persistence.NativeSql;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * As preferencias de accesibilidade en SQL (ADR-0031, revisión do 2026-11-11). Táboa sen concello
 * ({@code TenancyAllowlist}): toda sentenza leva {@code account_subject = :subject}.
 */
@Component
@NativeSql(
    scope = NativeSql.Scope.GLOBAL,
    reason =
        "As preferencias de accesibilidade son da conta, común a todos os concellos: a táboa non"
            + " ten municipality_id e cada sentenza vai filtrada polo sub da conta")
class DisplayPreferenceSql implements DisplayPreferenceRepository {

  private final JdbcClient jdbc;

  DisplayPreferenceSql(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<DisplayPreferences> find(String subject) {
    return jdbc.sql(
            """
            select text_size, high_contrast, reduced_motion
            from account_display_preference
            where account_subject = :subject
            """)
        .param("subject", subject)
        .query(
            (rs, n) ->
                new DisplayPreferences(
                    rs.getString("text_size"),
                    rs.getObject("high_contrast", Boolean.class),
                    rs.getObject("reduced_motion", Boolean.class)))
        .optional();
  }

  @Override
  public void save(String subject, DisplayPreferences preferences, Instant now) {
    // Dúas sentenzas, coma no resto do proxecto: a garda do SQL nativo non le «on conflict do
    // update».
    int updated =
        jdbc.sql(
                """
                update account_display_preference
                set text_size = :size, high_contrast = :contrast, reduced_motion = :motion,
                    updated_at = :now
                where account_subject = :subject
                """)
            .param("size", preferences.textSize())
            .param("contrast", preferences.highContrast(), Types.BOOLEAN)
            .param("motion", preferences.reducedMotion(), Types.BOOLEAN)
            .param("now", now.atOffset(ZoneOffset.UTC))
            .param("subject", subject)
            .update();
    if (updated == 0) {
      jdbc.sql(
              """
              insert into account_display_preference
                (account_subject, text_size, high_contrast, reduced_motion, updated_at)
              values (:subject, :size, :contrast, :motion, :now)
              on conflict (account_subject) do nothing
              """)
          .param("subject", subject)
          .param("size", preferences.textSize())
          .param("contrast", preferences.highContrast(), Types.BOOLEAN)
          .param("motion", preferences.reducedMotion(), Types.BOOLEAN)
          .param("now", now.atOffset(ZoneOffset.UTC))
          .update();
    }
  }
}
