// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.application.port.in;

import gal.furar.event.domain.EventId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A persoa con conta e os eventos: apuntarse (ela ou unha persoa a cargo, ADR-0041) ou pedir praza,
 * entrar na lista de espera, confirmar a praza ofrecida, desapuntarse e as súas próximas.
 */
public interface JoinEventUseCase {

  /** A inscrición da conta nese evento para si ({@code dependentId} nulo) ou para esa persoa. */
  Optional<RegistrationView> mine(EventId id, @Nullable UUID dependentId);

  default Optional<RegistrationView> mine(EventId id) {
    return mine(id, null);
  }

  /** Todas as da conta nese evento: para si e para as súas persoas a cargo. */
  List<RegistrationView> mineAll(EventId id);

  /**
   * «Apuntarme» ou, «Con aprobación», «Pedir praza»; nun evento completo sen aprobación, á lista de
   * espera. Dúas persoas á vez non ocupan a última praza: a segunda recibe {@code event.full} ou
   * entra na lista.
   */
  RegistrationView join(EventId id, JoinCommand command);

  default RegistrationView join(EventId id) {
    return join(id, JoinCommand.SELF);
  }

  /** Confirma a praza ofrecida desde a lista de espera. */
  RegistrationView confirm(EventId id, @Nullable UUID dependentId);

  /**
   * «Desapuntarme» (tamén retira unha solicitude ou sae da lista de espera): libera a praza, que
   * pasa á primeira da lista. Ata que empeza (unha actividade, ata que remata).
   */
  void leave(EventId id, @Nullable UUID dependentId);

  default void leave(EventId id) {
    leave(id, null);
  }

  /**
   * As da conta (con praza, pedindo ou en espera; para si e as súas persoas a cargo) en eventos que
   * aínda non pasaron, por data.
   */
  List<RegistrationView> upcoming();

  /**
   * Quen se apunta e, nun evento con límite de idade, a súa data de nacemento.
   *
   * @param dependentId a persoa a cargo; nulo, a propia persoa
   * @param birthDate a data de nacemento da propia persoa (só se o evento ten límite: compróbase
   *     polo seu ano e gárdase só na inscrición, nunca na conta); nunha persoa a cargo, vale o ano
   *     da súa ficha
   */
  record JoinCommand(@Nullable UUID dependentId, @Nullable LocalDate birthDate) {

    public static final JoinCommand SELF = new JoinCommand(null, null);
  }
}
