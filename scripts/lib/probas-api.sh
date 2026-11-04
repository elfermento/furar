# shellcheck shell=bash
# SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
# SPDX-License-Identifier: EUPL-1.2
#
# Contrato da API que usan as probas de despregue (D31, D33, D38, D41).
# Se cambia a API (api/openapi.json), axústase só aquí.

# shellcheck disable=SC2034  # úsano os scripts que cargan este ficheiro
FURAR_API_CREAR_CONCELLO=/api/v1/municipalities

# Campos personalizados dun concello: POST crea (xestor), GET /{id} require sesión.
furar_ruta_campos() { printf '/api/v1/municipalities/%s/custom-fields' "$1"; }

# Corpo dunha definición de campo (createCustomField).
furar_corpo_campo() { # código
  jq -nc --arg c "$1" '{code:$c, type:"TEXT",
    label:{gl:"Proba de despregue", es:"Prueba de despliegue"},
    usages:[{target:"MUNICIPALITY", required:false, visibility:"STAFF"}]}'
}

# Corpo mínimo para crear un concello (createMunicipality).
furar_corpo_concello() { # slug
  jq -nc --arg s "$1" '{slug:$s, name:("Concello de proba " + $s), languages:["gl","es"], defaultLanguage:"gl"}'
}
