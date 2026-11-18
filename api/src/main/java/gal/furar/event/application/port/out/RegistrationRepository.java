// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.out;

import gal.furar.event.domain.EventId;
import gal.furar.event.domain.Registration;
import gal.furar.event.domain.RegistrationId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * As inscricións nos eventos, en SQL (ADR-0038, ADR-0041). Sempre no concello actual e dentro dunha
 * transacción.
 */
public interface RegistrationRepository {

  /**
   * Toma o bloqueo da fila do evento ata o fin da transacción: quen confirma ou avisa dunha praza
   * bloquea antes de contar, así dúas persoas non ocupan a última.
   *
   * @return falso se o evento non existe no concello actual
   */
  boolean lockEvent(EventId id);

  /** Cantas hai en cada estado en cada evento (os que non teñen ningunha non saen). */
  Map<UUID, Counts> counts(Collection<EventId> ids);

  Optional<Registration> find(RegistrationId id);

  /** A da conta para si ({@code dependentId} nulo) ou para esa persoa a cargo. */
  Optional<Registration> findByPerson(EventId event, String subject, @Nullable UUID dependentId);

  /** A da conta para si. */
  default Optional<Registration> findByPerson(EventId event, String subject) {
    return findByPerson(event, subject, null);
  }

  /** As da conta nese evento (para si e para as súas persoas a cargo), pola orde de chegada. */
  List<Registration> listByAccount(EventId event, String subject);

  /** As dun evento, pola orde en que chegaron. */
  List<Registration> listForEvent(EventId event);

  /** A lista de espera dun evento (esperando ou avisadas), pola orde de chegada. */
  List<Registration> waitlist(EventId event);

  /**
   * As da conta (para si e as súas persoas a cargo) con praza, pedíndoa ou en espera, en calquera
   * evento do concello.
   */
  List<Registration> listForPerson(String subject);

  /** Cantas hai nun evento, en calquera estado. */
  long countForEvent(EventId event);

  /** Solicitudes pendentes en eventos sen cancelar que empezan despois de {@code now}. */
  long countPendingFrom(Instant now);

  /** As esperas da conta en eventos sen cancelar que aínda admiten inscricións. */
  int countActiveWaits(String subject, Instant now);

  /** Os avisos de praza libre cuxo correo xa toca e aínda non saíu. */
  List<Registration> noticesToMail(Instant now);

  /** O correo dese aviso xa saíu. */
  void markNoticeMailed(RegistrationId id, Instant at);

  /** Os eventos abertos con alguén na lista de espera. */
  List<EventId> eventsWithWaiting(Instant now);

  /**
   * Borra as esperas que non chegaron a praza dos eventos pechados ou cancelados antes de {@code
   * cancelledBefore}.
   *
   * @return cantas
   */
  int deleteClosedWaits(Instant now, Instant cancelledBefore);

  Registration insert(Registration registration);

  /**
   * Garda estado, pago, data de decisión e oferta se a versión é a lida; se non, {@code
   * common.stale-version}.
   */
  Registration update(Registration registration);

  void delete(RegistrationId id);

  /**
   * Conservación: quita a persoa (tamén a persoa a cargo e o ano de nacemento) das inscricións dos
   * eventos que remataron antes do corte.
   *
   * @return cantas
   */
  int anonymizeEndedBefore(Instant cutoff, Instant now);

  /**
   * Cantas hai en cada estado dun evento.
   *
   * @param notified avisadas dunha praza libre desde a lista de espera (non a ocupan ata confirmar)
   * @param waiting esperando na lista sen aviso
   */
  record Counts(int confirmed, int pending, int notified, int waiting) {

    public static final Counts NONE = new Counts(0, 0, 0, 0);

    public Counts(int confirmed, int pending) {
      this(confirmed, pending, 0, 0);
    }

    /** Cantas hai na lista de espera (esperando e avisadas). */
    public int waitlisted() {
      return notified + waiting;
    }
  }
}
