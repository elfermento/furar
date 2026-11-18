// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.people.application.service;

import gal.furar.people.application.port.in.AccountDeletionUseCase;
import gal.furar.people.application.port.in.AccountDeletionView;
import gal.furar.people.application.port.out.AccountClosures;
import gal.furar.people.domain.AccountDeletionBlocker;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.AuthError;
import gal.furar.shared.security.CurrentActor;
import gal.furar.shared.security.PersonUseCase;
import gal.furar.shared.security.PublicUseCase;
import gal.furar.shared.tenancy.MunicipalityDirectory;
import gal.furar.shared.tenancy.MunicipalityPlaces;
import gal.furar.shared.tenancy.TenantRef;
import java.text.Collator;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Se se pode eliminar a conta (ADR-0043). Non se pode mentres a persoa ten algo pendente con algún
 * concello: reservas que aínda non remataron, partidas abertas que organiza, inscricións en eventos
 * ou actividades que aínda non remataron, ou se é persoal activo dun concello ou administración da
 * plataforma. Primeiro hai que cancelalo (ou pedir que a quiten do persoal): eliminar a conta non
 * cancela nada, así que ningún concello queda cunha hora reservada sen ninguén detrás.
 *
 * <ul>
 *   <li><b>A web</b> pregunta coa sesión ({@link #mine()}) antes de ir a Keycloak e lista o que hai
 *       que cancelar, por concello, coas ligazóns.
 *   <li><b>Keycloak</b> volve preguntar ({@link #allowed(String)}) polo porto interno e coa clave
 *       xusto antes de mostrar a confirmación e outra vez antes de borrar: se a API non responde,
 *       non borra.
 * </ul>
 *
 * Unha soa lectura en todos os concellos ({@code account_deletion_blockers}, só tipos e cifras); o
 * nome do concello e o slug saen dos portos comúns.
 */
@Service
public class AccountDeletionService implements AccountDeletionUseCase {

  private static final Comparator<AccountDeletionView> ORDER =
      Comparator.comparing(AccountDeletionView::kind)
          .thenComparing(
              v -> Objects.requireNonNullElse(v.municipalityName(), ""),
              Collator.getInstance(Locale.forLanguageTag("gl")))
          .thenComparing(
              v -> Objects.requireNonNullElse(v.eventNameGl(), ""),
              Collator.getInstance(Locale.forLanguageTag("gl")));

  private final AccountClosures closures;
  private final CurrentActor currentActor;
  private final MunicipalityDirectory directory;
  private final MunicipalityPlaces places;

  public AccountDeletionService(
      AccountClosures closures,
      CurrentActor currentActor,
      MunicipalityDirectory directory,
      MunicipalityPlaces places) {
    this.closures = closures;
    this.currentActor = currentActor;
    this.directory = directory;
    this.places = places;
  }

  @Override
  @Transactional(readOnly = true)
  @PersonUseCase
  public List<AccountDeletionView> mine() {
    String subject =
        currentActor
            .current()
            .orElseThrow(() -> new DomainException(AuthError.UNAUTHENTICATED))
            .subject();
    List<AccountDeletionBlocker> blockers = closures.blockers(subject);
    if (blockers.isEmpty()) {
      return List.of();
    }
    Map<UUID, String> slugs =
        directory.all().stream()
            .filter(t -> t.slug() != null)
            .collect(Collectors.toMap(TenantRef::id, TenantRef::slug, (a, b) -> a));
    Map<UUID, String> names = new HashMap<>();
    Function<UUID, String> name =
        id ->
            names.computeIfAbsent(
                id, m -> places.place(m).map(MunicipalityPlaces.Place::name).orElse(null));
    return blockers.stream().map(b -> view(b, slugs, name)).sorted(ORDER).toList();
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public boolean allowed(String subject) {
    return closures.blockers(subject).isEmpty();
  }

  static AccountDeletionView view(
      AccountDeletionBlocker blocker, Map<UUID, String> slugs, Function<UUID, String> names) {
    UUID municipality = blocker.municipalityId();
    return new AccountDeletionView(
        blocker.kind(),
        municipality == null ? null : slugs.get(municipality),
        municipality == null ? null : names.apply(municipality),
        blocker.eventId(),
        blocker.eventNameGl(),
        blocker.eventNameEs(),
        blocker.count());
  }
}
