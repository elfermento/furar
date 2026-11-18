<#--
SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
SPDX-FileCopyrightText: Red Hat, Inc. and/or other contributors (Keycloak)
SPDX-License-Identifier: EUPL-1.2 AND Apache-2.0

Substitúe login-config-totp.ftl de keycloak.v2 (Keycloak 26.8.1, Apache-2.0, modificado).
Base: sha256 182a0e44f35be72d5cca2446c275c722f4bd60dc0cb21b08314aa9bd5338be1d
(X09 avisa se a da versión fixada cambia). Alta da verificación en dous pasos (ADR-0029). Cambios:
- «Que móbil tes?» e un selector iPhone | Android sen ningún marcado; ao
  escoller, a nota dese móbil, tres pasos (o QR ao centro no paso 2, «Non podes escanear o
  código?» debaixo; no paso 3, de onde sae o código), o campo, «Enviar» e, debaixo, o aviso de non
  recargar. As aplicacións de Android, as da política do realm (totp.supportedApplications), nunha
  frase. Mellora progresiva: sen JS non hai selector e vense os textos dos dous móbiles (cada un co
  seu nome) cun só QR; tras un erro ou no modo manual non se pecha nada. O JS (no propio modelo,
  coma template.ftl) lembra o móbil escollido na sesión do navegador.
- «Cambiar de método» na cabeceira (sección headerAction), se a extensión de Furar di que hai a
  onde volver (furarSecondFactorBack, ADR-0029): envía «back» á propia acción.
- Sen o parágrafo do nome do dispositivo; o campo só sae se a conta xa ten outro código, cando
  Keycloak o pide (a primeira vez é opcional).
- As etiquetas ligadas aos seus campos (for="totp" e for="userLabel"; na base, a un id que non
  existe).
-->
<#import "template.ftl" as layout>
<#import "field.ftl" as field>
<#import "buttons.ftl" as buttons>
<#import "password-commons.ftl" as passwordCommons>
<#import "qr-code.ftl" as qr>
<@layout.registrationLayout displayRequiredFields=false displayMessage=!messagesPerField.existsError('totp','userLabel'); section>
<!-- template: login-config-totp.ftl -->

    <#if section = "header">
        ${msg("loginTotpTitle")}
    <#elseif section = "headerAction">
        <#if furarSecondFactorBack??>
            <form id="furar-second-factor-back" action="${url.loginAction}" method="post">
                <button type="submit" name="back" value="true" class="${properties.kcButtonLinkClass!} furar-back">${msg("furarSecondFactorBack")}</button>
            </form>
        </#if>
    <#elseif section = "form">
        <#assign furarApps = totp.supportedApplications?map(app -> msg(app))?sequence>
        <#assign furarOpen = (mode?? && mode = "manual") || messagesPerField.existsError('totp','userLabel')>
        <div class="furar-totp" id="furar-totp"<#if furarOpen> data-open</#if>>
        <div class="furar-totp__choice">
            <p class="furar-totp__question" id="furar-totp-phone">${msg("furarTotpPhone")}</p>
            <div class="f-segmented furar-wide" role="group" aria-labelledby="furar-totp-phone">
                <button type="button" aria-pressed="false" data-phone="ios">${msg("furarTotpIos")}</button>
                <button type="button" aria-pressed="false" data-phone="android">${msg("furarTotpAndroid")}</button>
            </div>
        </div>
        <script>
            document.getElementById('furar-totp').setAttribute('data-js', '');
        </script>
        <div class="furar-totp__after">
        <p class="furar-totp__note" data-for="ios"><b class="furar-totp__name">${msg("furarTotpIos")}: </b>${msg("furarTotpIosNote")}</p>
        <p class="furar-totp__note" data-for="android" id="kc-totp-supported-apps"><b class="furar-totp__name">${msg("furarTotpAndroid")}: </b><#if furarApps?size == 0>${msg("furarTotpAndroidNoteAny")}<#elseif furarApps?size == 1>${msg("furarTotpAndroidNote", furarApps[0])}<#else>${msg("furarTotpAndroidNote", furarApps[0..<furarApps?size - 1]?join(", ") + " " + msg("furarListOr") + " " + furarApps?last)}</#if></p>
        <ol id="kc-totp-settings" class="pf-v5-c-list pf-v5-u-mb-md">
            <li>
                <p data-for="ios"><b class="furar-totp__name">${msg("furarTotpIos")}: </b>${msg("furarTotpIosStep1")}</p>
                <p data-for="android"><b class="furar-totp__name">${msg("furarTotpAndroid")}: </b>${msg("furarTotpAndroidStep1")}</p>
            </li>
            <#if mode?? && mode = "manual">
                <li>
                    <p>${msg("loginTotpManualStep2")}</p>
                    <p><span id="kc-totp-secret-key">${totp.totpSecretEncoded}</span></p>
                    <p><a href="${totp.qrUrl}" id="mode-barcode">${msg("loginTotpScanBarcode")}</a></p>
                    <ul>
                        <li id="kc-totp-type">${msg("loginTotpType")}: ${msg("loginTotp." + totp.policy.type)}</li>
                        <li id="kc-totp-algorithm">${msg("loginTotpAlgorithm")}: ${totp.policy.getAlgorithmKey()}</li>
                        <li id="kc-totp-digits">${msg("loginTotpDigits")}: ${totp.policy.digits}</li>
                        <#if totp.policy.type = "totp">
                            <li id="kc-totp-period">${msg("loginTotpInterval")}: ${totp.policy.period}</li>
                        <#elseif totp.policy.type = "hotp">
                            <li id="kc-totp-counter">${msg("loginTotpCounter")}: ${totp.policy.initialCounter}</li>
                        </#if>
                    </ul>
                </li>
            <#else>
                <li>
                    <p data-for="ios"><b class="furar-totp__name">${msg("furarTotpIos")}: </b>${msg("furarTotpIosStep2")}</p>
                    <p data-for="android"><b class="furar-totp__name">${msg("furarTotpAndroid")}: </b>${msg("furarTotpAndroidStep2")}</p>
                    <div class="furar-totp__qr">
                        <@qr.qrCode id="kc-totp-secret-qr-code" content=totp.totpSecretQrCode alt=msg("loginTotpStep2")>
                            <a href="${totp.manualUrl}" id="mode-manual">${msg("loginTotpUnableToScan")}</a>
                        </@qr.qrCode>
                    </div>
                </li>
            </#if>
            <li>
                <p data-for="ios"><b class="furar-totp__name">${msg("furarTotpIos")}: </b>${msg("furarTotpIosStep3")}</p>
                <p data-for="android"><b class="furar-totp__name">${msg("furarTotpAndroid")}: </b>${msg("furarTotpAndroidStep3")}</p>
                <#if totp.policy.type = "totp">
                    <p class="furar-totp__sub">${msg("furarTotpCodeSource", totp.policy.period)}</p>
                </#if>
            </li>
        </ol>

        <form action="${url.loginAction}" class="${properties.kcFormClass!}" id="kc-totp-settings-form" method="post" novalidate="novalidate">
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelClass!}">
                    <label class="pf-v5-c-form__label" for="totp">
                        <span class="pf-v5-c-form__label-text">${msg("authenticatorCode")}</span>&nbsp;<span class="pf-v5-c-form__label-required" aria-hidden="true">&#42;</span>
                    </label>
                </div>
                <div class="${properties.kcInputClass!} <#if messagesPerField.existsError('totp')>pf-m-error</#if>">
                    <input type="text" required id="totp" name="totp" autocomplete="one-time-code"
                           aria-invalid="<#if messagesPerField.existsError('totp')>true</#if>"
                           inputmode="numeric"
                    />

                    <@field.errorIcon error=messagesPerField.get('totp')/>
                </div>
                <#if messagesPerField.existsError('totp')>
                    <span id="input-error-otp-code" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                        ${messagesPerField.get('totp')}
                    </span>
                </#if>
                <input type="hidden" id="totpSecret" name="totpSecret" value="${totp.totpSecret}" />
                <#if mode??><input type="hidden" id="mode" name="mode" value="${mode}"/></#if>
            </div>
            <#if totp.otpCredentials?size gte 1 || messagesPerField.existsError('userLabel')>
            <div class="${properties.kcFormGroupClass!}">
                <div class="${properties.kcLabelClass!}">
                    <label class="pf-v5-c-form__label" for="userLabel">
                        <span class="pf-v5-c-form__label-text">${msg("loginTotpDeviceName")}</span>&nbsp;<span class="pf-v5-c-form__label-required" aria-hidden="true">&#42;</span>
                    </label>
                </div>

                <div class="${properties.kcInputClass!} <#if messagesPerField.existsError('userLabel')>pf-m-error</#if>">
                    <input type="text" id="userLabel" name="userLabel" autocomplete="off"
                           aria-invalid="<#if messagesPerField.existsError('userLabel')>true</#if>"
                    />

                    <@field.errorIcon error=messagesPerField.get('userLabel')/>
                </div>
                <#if messagesPerField.existsError('userLabel')>
                    <span id="input-error-otp-label" class="${properties.kcInputErrorMessageClass!}" aria-live="polite">
                        ${messagesPerField.get('userLabel')}
                    </span>
                </#if>
            </div>
            </#if>

            <div class="${properties.kcFormGroupClass!}">
                <@passwordCommons.logoutOtherSessions/>
            </div>

            <#if isAppInitiatedAction??>
                <@buttons.actionGroup horizontal=true>
                    <@buttons.button id="saveTOTPBtn" label="doSubmit" />
                    <@buttons.button id="cancelTOTPBtn" name="cancel-aia" label="doCancel" type="secondary" value="true" />
                </@buttons.actionGroup>
            <#else>
                <@buttons.actionGroup>
                    <@buttons.button id="saveTOTPBtn" label="doSubmit" />
                </@buttons.actionGroup>
            </#if>
        </form>
        <p class="furar-totp__reload"><svg viewBox="0 -960 960 960" aria-hidden="true" focusable="false"><path d="m40-120 440-760 440 760H40Zm104-60h672L480-760 144-180Zm361.5-65.68q8.5-8.67 8.5-21.5 0-12.82-8.68-21.32-8.67-8.5-21.5-8.5-12.82 0-21.32 8.68-8.5 8.67-8.5 21.5 0 12.82 8.68 21.32 8.67 8.5 21.5 8.5 12.82 0 21.32-8.68ZM454-348h60v-224h-60v224Zm26-122Z"/></svg>${msg("furarTotpReload")}</p>
        </div>
        </div>
        <script>
            (function () {
                var root = document.getElementById('furar-totp');
                var key = 'furar.totpPhone';
                function choose(phone) {
                    root.setAttribute('data-phone', phone);
                    root.querySelectorAll('[data-phone]').forEach(function (b) {
                        if (b.tagName === 'BUTTON') b.setAttribute('aria-pressed', String(b.getAttribute('data-phone') === phone));
                    });
                    try { window.sessionStorage.setItem(key, phone); } catch (e) {}
                }
                root.querySelectorAll('button[data-phone]').forEach(function (b) {
                    b.addEventListener('click', function () { choose(b.getAttribute('data-phone')); });
                });
                var saved = null;
                try { saved = window.sessionStorage.getItem(key); } catch (e) {}
                if (saved === 'ios' || saved === 'android') choose(saved);
            })();
        </script>
    </#if>
</@layout.registrationLayout>
