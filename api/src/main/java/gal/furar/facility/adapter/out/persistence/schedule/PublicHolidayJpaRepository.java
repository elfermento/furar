// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence.schedule;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Catálogo de plataforma: sen concello. */
public interface PublicHolidayJpaRepository extends JpaRepository<PublicHolidayEntity, LocalDate> {

  List<PublicHolidayEntity> findByDateBetweenOrderByDateAsc(LocalDate from, LocalDate to);
}
