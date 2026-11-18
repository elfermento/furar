// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gal.furar.customfields.application.port.in.ChangeCustomFieldStatusUseCase;
import gal.furar.customfields.application.port.in.CreateCustomFieldDefinitionCommand;
import gal.furar.customfields.application.port.in.CreateCustomFieldDefinitionUseCase;
import gal.furar.customfields.application.port.in.CustomFieldInUse;
import gal.furar.customfields.application.port.in.GetCustomFieldDefinitionUseCase;
import gal.furar.customfields.application.port.in.GetCustomFieldInUseUseCase;
import gal.furar.customfields.application.port.in.GetCustomFieldSchemaUseCase;
import gal.furar.customfields.application.port.in.ListCustomFieldDefinitionsUseCase;
import gal.furar.customfields.application.port.in.UpdateCustomFieldDefinitionCommand;
import gal.furar.customfields.application.port.in.UpdateCustomFieldDefinitionUseCase;
import gal.furar.customfields.domain.CustomFieldDefinition;
import gal.furar.customfields.domain.CustomFieldDefinitionId;
import gal.furar.customfields.domain.DefinitionSpec;
import gal.furar.customfields.domain.DefinitionStatus;
import gal.furar.customfields.domain.Definitions;
import gal.furar.customfields.domain.FieldType;
import gal.furar.customfields.domain.FieldUsage;
import gal.furar.customfields.domain.TextFormat;
import gal.furar.customfields.domain.Visibility;
import gal.furar.shared.domain.CustomFieldTarget;
import gal.furar.shared.domain.LocalizedText;
import gal.furar.shared.security.config.WebTestConfig;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Contrato HTTP e seguridade das definicións de campos personalizados. */
@WebMvcTest(CustomFieldDefinitionController.class)
@Import(WebTestConfig.class)
@ActiveProfiles("test")
class CustomFieldDefinitionWebTest {

  static final String BASE = "/api/v1/municipalities/naron/custom-fields";

  @Autowired MockMvc mvc;
  @MockitoBean CreateCustomFieldDefinitionUseCase create;
  @MockitoBean UpdateCustomFieldDefinitionUseCase update;
  @MockitoBean ChangeCustomFieldStatusUseCase changeStatus;
  @MockitoBean GetCustomFieldDefinitionUseCase getUseCase;
  @MockitoBean ListCustomFieldDefinitionsUseCase list;
  @MockitoBean GetCustomFieldSchemaUseCase schema;
  @MockitoBean GetCustomFieldInUseUseCase inUse;

  static CustomFieldDefinition definition(long version) {
    DefinitionSpec spec =
        new DefinitionSpec(
            new LocalizedText("Teléfono", "Teléfono"),
            new LocalizedText("Con prefixo", "Con prefijo"),
            Definitions.text(null, 20, TextFormat.PHONE),
            List.of(),
            true,
            List.of(new FieldUsage(CustomFieldTarget.MUNICIPALITY, true, Visibility.OWNER)));
    CustomFieldDefinition d =
        CustomFieldDefinition.create(
            new CustomFieldDefinitionId(UUID.fromString("019a0b6e-0000-7000-8000-0000000000aa")),
            "phone",
            FieldType.TEXT,
            spec,
            Definitions.NOW);
    return CustomFieldDefinition.rehydrate(
        d.id(),
        d.code(),
        d.type(),
        d.spec(),
        DefinitionStatus.ACTIVE,
        version,
        d.createdAt(),
        d.updatedAt());
  }

  @Test
  void listIsPublicAndIncludeInactiveUsesTheManagementUseCase() throws Exception {
    when(list.listVisible(CustomFieldTarget.MUNICIPALITY)).thenReturn(List.of(definition(1)));
    mvc.perform(get(BASE).param("target", "MUNICIPALITY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("phone"))
        .andExpect(jsonPath("$[0].label.es").value("Teléfono"))
        .andExpect(jsonPath("$[0].rules.format").value("PHONE"))
        .andExpect(jsonPath("$[0].rules.maxLength").value(20))
        .andExpect(jsonPath("$[0].usages.length()").value(1))
        .andExpect(jsonPath("$[0].usages[0].target").value("MUNICIPALITY"))
        .andExpect(jsonPath("$[0].usages[0].required").value(true))
        .andExpect(jsonPath("$[0].usages[0].visibility").value("OWNER"))
        .andExpect(jsonPath("$[0].personalData").value(true))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$[0].hasValues").doesNotExist())
        .andExpect(jsonPath("$[0].version").value(1));
    when(list.listAll(CustomFieldTarget.MUNICIPALITY)).thenReturn(List.of(definition(1)));
    when(inUse.inUse(any()))
        .thenReturn(Map.of(definition(1).id(), new CustomFieldInUse(true, java.util.Set.of())));
    mvc.perform(get(BASE).param("target", "MUNICIPALITY").param("includeInactive", "true"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].usages.length()").value(1))
        .andExpect(jsonPath("$[0].hasValues").value(true));
    verify(list).listAll(CustomFieldTarget.MUNICIPALITY);
    // Sen destino: todos os campos do concello (editor de Axustes).
    when(list.listAll()).thenReturn(List.of());
    mvc.perform(get(BASE)).andExpect(status().isOk());
    verify(list).listAll();
    // Formato mal: 400 no campo target. Un destino ben formado sen rexistrar rexéitao o caso de
    // uso (CustomFieldTargets, CustomFieldDefinitionServiceTest).
    mvc.perform(get(BASE).param("target", "planet"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation.failed"))
        .andExpect(jsonPath("$.errors[0].field").value("target"));
  }

  @Test
  void schemaIsPublic() throws Exception {
    when(schema.schema(CustomFieldTarget.MUNICIPALITY)).thenReturn(Map.of("type", "object"));
    mvc.perform(get(BASE + "/MUNICIPALITY/schema"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.type").value("object"));
  }

  @Test
  void writesNeedATokenAndMapTheBody() throws Exception {
    String body =
        """
        {"code":"zone","type":"SELECT","label":{"gl":"Zona","es":"Zona"},
         "options":[{"code":"north","label":{"gl":"Norte","es":"Norte"}}],
         "usages":[{"target":"MUNICIPALITY","visibility":"PUBLIC","required":true}]}
        """;
    mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnauthorized());

    when(create.create(any())).thenReturn(definition(0));
    mvc.perform(post(BASE).with(jwt()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(header().string("ETag", "\"0\""))
        .andExpect(header().string("Location", BASE + "/019a0b6e-0000-7000-8000-0000000000aa"));
    ArgumentCaptor<CreateCustomFieldDefinitionCommand> command =
        ArgumentCaptor.forClass(CreateCustomFieldDefinitionCommand.class);
    verify(create).create(command.capture());
    assertThat(command.getValue().type()).isEqualTo(FieldType.SELECT);
    assertThat(command.getValue().spec().options()).hasSize(1);
    assertThat(command.getValue().spec().usages())
        .containsExactly(new FieldUsage(CustomFieldTarget.MUNICIPALITY, true, Visibility.PUBLIC));

    // Un campo, un sitio: dous usos non pasan a validación da petición.
    mvc.perform(
            post(BASE)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    body.replace(
                        "\"required\":true}]",
                        "\"required\":true},{\"target\":\"FACILITY\",\"visibility\":\"STAFF\"}]")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("usages"));

    mvc.perform(
            post(BASE)
                .with(jwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"code\":\"Bad\",\"type\":\"COLOUR\",\"label\":{\"gl\":\"\",\"es\":\"x\"},\"usages\":[{\"target\":\"MUNICIPALITY\",\"visibility\":\"ALL\"}]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("validation.failed"))
        .andExpect(jsonPath("$.errors.length()").value(4));
  }

  @Test
  void updateDeactivateActivateAndDeleteUseIfMatch() throws Exception {
    UUID id = UUID.fromString("019a0b6e-0000-7000-8000-0000000000aa");
    when(update.update(any())).thenReturn(definition(2));
    mvc.perform(
            put(BASE + "/" + id)
                .with(jwt())
                .header("If-Match", "\"1\"")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"label\":{\"gl\":\"Tel\",\"es\":\"Tel\"},\"usages\":[{\"target\":\"MUNICIPALITY\",\"visibility\":\"STAFF\"}],\"rules\":{\"maxLength\":10,\"format\":\"PHONE\"},\"help\":{\"gl\":\"a\",\"es\":\"b\"}}"))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"2\""));
    ArgumentCaptor<UpdateCustomFieldDefinitionCommand> command =
        ArgumentCaptor.forClass(UpdateCustomFieldDefinitionCommand.class);
    verify(update).update(command.capture());
    assertThat(command.getValue().expectedVersion()).isEqualTo(1);
    assertThat(command.getValue().spec().usages().getFirst().visibility())
        .isEqualTo(Visibility.STAFF);
    assertThat(command.getValue().spec().rules().format()).isEqualTo(TextFormat.PHONE);

    when(changeStatus.deactivate(any(), anyLong())).thenReturn(definition(3));
    mvc.perform(post(BASE + "/" + id + "/deactivate").with(jwt()).header("If-Match", "\"2\""))
        .andExpect(status().isOk());
    verify(changeStatus).deactivate(new CustomFieldDefinitionId(id), 2);
    when(changeStatus.activate(any(), anyLong())).thenReturn(definition(4));
    mvc.perform(post(BASE + "/" + id + "/activate").with(jwt()).header("If-Match", "W/\"3\""))
        .andExpect(status().isOk());
    verify(changeStatus).activate(eq(new CustomFieldDefinitionId(id)), eq(3L));

    mvc.perform(delete(BASE + "/" + id).with(jwt()).header("If-Match", "\"4\""))
        .andExpect(status().isNoContent());
    verify(changeStatus).delete(new CustomFieldDefinitionId(id), 4);
    org.mockito.Mockito.doThrow(
            new gal.furar.shared.domain.DomainException(
                gal.furar.customfields.domain.CustomFieldError.HAS_VALUES))
        .when(changeStatus)
        .delete(any(), eq(5L));
    mvc.perform(delete(BASE + "/" + id).with(jwt()).header("If-Match", "\"5\""))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("customfield.has-values"));

    mvc.perform(post(BASE + "/" + id + "/deactivate").with(jwt()))
        .andExpect(status().isPreconditionRequired())
        .andExpect(jsonPath("$.code").value("common.precondition-required"));
    mvc.perform(post(BASE + "/not-a-uuid/deactivate").with(jwt()).header("If-Match", "\"1\""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("common.invalid-parameter"));

    when(getUseCase.get(any())).thenReturn(definition(4));
    mvc.perform(get(BASE + "/" + id).with(jwt()))
        .andExpect(status().isOk())
        .andExpect(header().string("ETag", "\"4\""))
        .andExpect(jsonPath("$.help.gl").value("Con prefixo"));
  }
}
