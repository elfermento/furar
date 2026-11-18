// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tarefas programadas da plataforma (hoxe, a conservación do rexistro de actividade). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {}
