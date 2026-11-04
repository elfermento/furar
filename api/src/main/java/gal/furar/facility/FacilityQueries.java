// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * API pública síncrona do módulo para outros módulos (ADR-0005, ARQ-3; ADR-0022): lecturas dentro
 * do concello actual, con records propios da raíz e nunca o dominio (A19). Non comproba permisos:
 * xa os comprobou o caso de uso que chama; nunca se expón por HTTP.
 */
public interface FacilityQueries {

  /**
   * Días como moito, os dous extremos incluídos, nunha lectura de {@link #openingIntervals} ou
   * {@link #openingIntervalsByFacility}.
   */
  int MAX_OPENING_DAYS = 400;

  /**
   * Días como moito en {@link #longOpeningIntervalsByFacility}: cinco veces {@link
   * #MAX_OPENING_DAYS}, o que mira a evolución anual dos indicadores (cinco anos, ADR-0026).
   */
  int MAX_LONG_OPENING_DAYS = 5 * MAX_OPENING_DAYS;

  Optional<FacilitySummary> find(UUID id);

  /**
   * A instalación se a ve a veciñanza (ADR-0022, punto 3): nin en borrador nin de baixa. As
   * pechadas temporalmente e as que non se reservan por internet si saen.
   */
  Optional<FacilitySummary> findPublic(UUID id);

  /**
   * Os textos de pago e devolución que o concello lle puxo á instalación (D2-11), aínda que estea
   * de baixa; baleiro se non existe no concello actual.
   */
  Optional<FacilityPaymentTexts> paymentTexts(UUID id);

  /** As que existan, na orde pedida; os ids doutro concello ou inexistentes non aparecen. */
  List<FacilitySummary> summary(List<UUID> ids);

  /**
   * Todas as instalacións do concello actual, tamén en borrador e de baixa ({@link
   * FacilitySummary#status()}), por data de alta, cun número fixo de lecturas: os filtros e os
   * nomes dos indicadores (ADR-0026).
   */
  List<FacilitySummary> all();

  /**
   * Todos os usos do concello actual, tamén os arquivados, por data de alta e cun número fixo de
   * lecturas (sen N+1): o filtro «Uso» dos indicadores (ADR-0026).
   */
  List<UseSummary> allUses();

  /**
   * Toma o bloqueo da instalación ({@code for no key update}) ata o fin da transacción en curso: a
   * orde é sempre instalación → ocupación (ADR-0022, punto 4). Pide unha transacción aberta.
   *
   * @return falso se a instalación non existe no concello actual
   */
  boolean lockFacility(UUID id);

  /**
   * Os tramos abertos da instalación entre as dúas datas (incluídas), en orde: tempada → festivo →
   * excepción (a excepción manda). Non mira o estado da instalación nin os usos que bloquean (iso
   * compróbao quen reserva baixo {@link #lockFacility}) e sen as horas dos bloqueos: un tramo que
   * un bloqueo corta sae en anacos, cada un co {@link OpeningInterval#alignFromMinute} do tramo
   * enteiro. Baleiro se a instalación non existe no concello actual.
   *
   * @throws IllegalArgumentException se {@code to} é anterior a {@code from} ou o rango pasa de
   *     {@value #MAX_OPENING_DAYS} días
   */
  List<OpeningInterval> openingIntervals(UUID facilityId, LocalDate from, LocalDate to);

  /**
   * Os tramos abertos de {@link #openingIntervals(UUID, LocalDate, LocalDate)} de varias
   * instalacións á vez, cun número fixo de lecturas (sen N+1): o calendario de xestión pinta
   * pechadas as horas de fóra (maqueta 27). Sen {@code facilityId}, todas as do concello actual que
   * non están de baixa; con el, só esa (se existe). As que non abren no período saen cunha lista
   * baleira.
   *
   * @throws IllegalArgumentException nas mesmas datas ca {@link #openingIntervals(UUID, LocalDate,
   *     LocalDate)}
   */
  Map<UUID, List<OpeningInterval>> openingIntervalsByFacility(
      @Nullable UUID facilityId, LocalDate from, LocalDate to);

  /**
   * O mesmo ca {@link #openingIntervalsByFacility(UUID, LocalDate, LocalDate)} nun rango de ata
   * {@value #MAX_LONG_OPENING_DAYS} días e coas mesmas lecturas: o denominador da ocupación dos
   * indicadores, que miran ata cinco anos (ADR-0026). Só para lecturas internas cun rango xa
   * acoutado; o que chega por HTTP usa {@link #openingIntervalsByFacility}.
   *
   * @throws IllegalArgumentException se {@code to} é anterior a {@code from} ou o rango pasa de
   *     {@value #MAX_LONG_OPENING_DAYS} días
   */
  Map<UUID, List<OpeningInterval>> longOpeningIntervalsByFacility(
      @Nullable UUID facilityId, LocalDate from, LocalDate to);

  /**
   * O uso vivo da instalación que bloquea os demais ese día (especificación, bloque 1: «bloquea os
   * demais»): nas súas datas e nun dos seus días da semana. Como moito un: dous usos que bloquean
   * non se pisan (P2).
   */
  Optional<UUID> exclusiveUseOn(UUID facilityId, LocalDate date);

  /** Un uso do concello actual, tamén arquivado ({@link UseSummary#archived()}). */
  Optional<UseSummary> use(UUID useId);

  /**
   * Os usos do concello actual con eses ids, tamén os arquivados, nunha lectura (sen N+1): os nomes
   * das listas de reservas. Os que non existan non aparecen; sen orde garantida.
   */
  List<UseSummary> usesById(Collection<UUID> useIds);

  /** Os usos vivos da instalación, por orde de alta; baleira se non existe no concello actual. */
  List<UseSummary> uses(UUID facilityId);

  /**
   * A portada de cada instalación do concello actual con eses ids, por lote (sen N+1): a miniatura
   * das tarxetas de «As miñas reservas». As que non existan non aparecen; sen orde garantida.
   */
  List<FacilityCover> covers(Collection<UUID> facilityIds);

  /**
   * Unha páxina das instalacións que se reservan por internet e se ven na portada: nin en borrador
   * nin de baixa nin sen reserva por internet (as pechadas temporalmente si, mira {@link
   * FacilitySummary#status()}). Orde alfabética en galego e despois por id; a páxina empeza en 0.
   *
   * @throws IllegalArgumentException se {@code page} é negativa ou {@code size} non vai de 1 a 100
   */
  List<UUID> visibleIds(int page, int size);

  /** As de {@code ids} que cumpren o mesmo ca {@link #visibleIds(int, int)}, na orde pedida. */
  List<UUID> visibleIds(Collection<UUID> ids);
}
