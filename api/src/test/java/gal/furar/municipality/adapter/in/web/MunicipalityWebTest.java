// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gal.furar.municipality.application.port.in.CreateMunicipalityCommand;
import gal.furar.municipality.application.port.in.CreateMunicipalityUseCase;
import gal.furar.municipality.application.port.in.GetMunicipalityUseCase;
import gal.furar.municipality.application.port.in.ListMunicipalitiesUseCase;
import gal.furar.municipality.application.port.in.MunicipalityView;
import gal.furar.municipality.application.port.in.UpdateMunicipalityCommand;
import gal.furar.municipality.application.port.in.UpdateMunicipalityUseCase;
import gal.furar.municipality.domain.LegalDetails;
import gal.furar.municipality.domain.Municipality;
import gal.furar.municipality.domain.MunicipalityId;
import gal.furar.municipality.domain.Slug;
import gal.furar.municipality.domain.Theme;
import gal.furar.shared.domain.CustomFieldValue;
import gal.furar.shared.domain.CustomFieldValues;
import gal.furar.shared.domain.Language;
import gal.furar.shared.domain.PageResult;
import gal.furar.shared.security.config.WebTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Contrato HTTP e seguridade do controlador de concellos, cos casos de uso substituídos. */
@WebMvcTest(MunicipalityController.class)
@Import(WebTestConfig.class)
@ActiveProfiles("test")
class MunicipalityWebTest {

  static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

  @Autowired MockMvc mvc;
  @MockitoBean CreateMunicipalityUseCase create;
  @MockitoBean GetMunicipalityUseCase getUseCase;
  @MockitoBean ListMunicipalitiesUseCase list;
  @MockitoBean UpdateMunicipalityUseCase update;

  static MunicipalityView view(long version) {
    Municipality m =
        Municipality.rehydrate(
            new MunicipalityId(WebTestConfig.NARON),
            new Slug("naron"),
            "Narón",
            List.of(Language.GL, Language.ES),
            Language.GL,
            Theme.DEFAULT,
            LegalDetails.EMPTY,
            version,
            NOW,
            NOW);
    return new MunicipalityView(
        m,
        CustomFieldValues.trusted(Map.of("web", new CustomFieldValue.Text("https://naron.gal"))));
  }

  @Test
  void publicDetailHasTheContractShapeAndETag() throws Exception {
    when(getUseCase.getCurrent()).thenReturn(view(3));
    mvc.perform(get("/api/v1/municipalities/naron"))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"3\""))
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.slug").value("naron"))
        .andExpect(jsonPath("$.languages[1]").value("es"))
        .andExpect(jsonPath("$.defaultLanguage").value("gl"))
        .andExpect(jsonPath("$.theme.onPrimary").value("#ffffff"))
        .andExpect(jsonPath("$.customFields.web").value("https://naron.gal"))
        .andExpect(jsonPath("$.version").value(3))
        .andExpect(jsonPath("$.createdAt").value("2026-10-22T10:00:00Z"));
    mvc.perform(get("/api/v1/municipalities/naron/theme"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.primary").value("#0b4f8a"));
  }

  @Test
  void unknownSlugIs404FromTheFilter() throws Exception {
    mvc.perform(get("/api/v1/municipalities/nowhere"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("municipality.not-found"));
    verifyNoInteractions(getUseCase);
  }

  @Test
  void listIsPublicAndPaged() throws Exception {
    when(list.list(anyInt(), anyInt()))
        .thenReturn(new PageResult<>(List.of(view(0).municipality()), 0, 20, 1));
    mvc.perform(get("/api/v1/municipalities"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].slug").value("naron"))
        .andExpect(jsonPath("$.totalItems").value(1));
    mvc.perform(get("/api/v1/municipalities?size=500"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation.failed"));
  }

  @Test
  void createNeedsATokenAndValidBody() throws Exception {
    String body =
        """
        {"slug":"lugo","name":"Lugo","languages":["gl"],"defaultLanguage":"gl"}
        """;
    mvc.perform(
            post("/api/v1/municipalities").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("auth.unauthenticated"));

    when(create.create(any())).thenReturn(view(0));
    mvc.perform(
            post("/api/v1/municipalities")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/municipalities/naron"))
        .andExpect(header().string("ETag", "\"0\""));
    ArgumentCaptor<CreateMunicipalityCommand> command =
        ArgumentCaptor.forClass(CreateMunicipalityCommand.class);
    verify(create).create(command.capture());
    assertThat(command.getValue().slug()).isEqualTo("lugo");
    assertThat(command.getValue().theme()).isNull();

    mvc.perform(
            post("/api/v1/municipalities")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"slug\":\"UP\",\"name\":\"\",\"languages\":[],\"defaultLanguage\":\"fr\",\"extra\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("common.malformed-json"));
    mvc.perform(
            post("/api/v1/municipalities")
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"slug\":\"UP\",\"name\":\"\",\"languages\":[],\"defaultLanguage\":\"fr\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation.failed"))
        .andExpect(jsonPath("$.errors.length()").value(4));
  }

  @Test
  void updatePassesIfMatchAndCustomFields() throws Exception {
    when(update.update(any())).thenReturn(view(5));
    mvc.perform(
            put("/api/v1/municipalities/naron")
                .with(jwt().jwt(j -> j.claim("furar_roles", List.of("naron:MANAGER"))))
                .header("If-Match", "\"4\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name":"Narón","languages":["gl","es"],"defaultLanguage":"gl",
                     "theme":{"primary":"#0b4f8a","onPrimary":"#ffffff","accent":"#b3541e","background":"#ffffff","text":"#1a1a1a"},
                     "customFields":{"web":"https://naron.gal","capacity":12.5}}
                    """))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"5\""));
    ArgumentCaptor<UpdateMunicipalityCommand> command =
        ArgumentCaptor.forClass(UpdateMunicipalityCommand.class);
    verify(update).update(command.capture());
    assertThat(command.getValue().expectedVersion()).isEqualTo(4);
    assertThat(command.getValue().customFields())
        .containsEntry("web", "https://naron.gal")
        .containsEntry("capacity", new java.math.BigDecimal("12.5"));
  }
}
