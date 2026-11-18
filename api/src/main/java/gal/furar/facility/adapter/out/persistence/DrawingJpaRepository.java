// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Catálogo da plataforma: sen concello (ADR-0006). */
public interface DrawingJpaRepository extends JpaRepository<DrawingEntity, String> {}
