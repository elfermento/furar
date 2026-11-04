// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility;

import static org.assertj.core.api.Assertions.assertThat;

import gal.furar.facility.application.service.FacilityService;
import gal.furar.shared.tenancy.MunicipalityPlaces;
import gal.furar.shared.tenancy.TenantSlugResolver;
import gal.furar.testsupport.PostgresContainers;
import gal.furar.testsupport.TestJwtConfig;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.test.ApplicationModuleTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * O módulo arrinca só, sen {@code booking} (ADR-0005, ADR-0022 punto 2): sen ningún bean de {@link
 * BookingImpact}, usa {@link NoBookingImpact}. Arrinca coas súas dependencias directas ({@code
 * customfields}, para os campos propios), nunca con {@code booking}. O resolvedor de slugs e o nome
 * e INE do concello son de {@code municipality}: aquí, baleiros.
 */
@ApplicationModuleTest(ApplicationModuleTest.BootstrapMode.DIRECT_DEPENDENCIES)
@ActiveProfiles("test")
@Import({TestJwtConfig.class, FacilityModuleIT.Slugs.class})
class FacilityModuleIT {

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    var postgres = PostgresContainers.shared();
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", PostgresContainers::appUser);
    registry.add("spring.datasource.password", () -> PostgresContainers.APP_PASSWORD);
    registry.add("spring.flyway.user", () -> PostgresContainers.OWNER_USER);
    registry.add("spring.flyway.password", () -> PostgresContainers.OWNER_PASSWORD);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Slugs {
    @Bean
    TenantSlugResolver slugs() {
      return slug -> Optional.empty();
    }

    @Bean
    MunicipalityPlaces places() {
      return id -> Optional.empty();
    }
  }

  @Autowired ApplicationContext context;

  @Test
  void startsAloneWithTheDefaultBookingImpact() {
    assertThat(context.getBeanProvider(BookingImpact.class).getIfAvailable()).isNull();
    assertThat(context.getBean(FacilityService.class)).isNotNull();
    assertThat(context.getBean(FacilityQueries.class)).isNotNull();
  }
}
