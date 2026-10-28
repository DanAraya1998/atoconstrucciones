<#-- Native Keycloak extension point. Forms, actions and scripts remain inherited. -->
<#macro content>
    <div class="ato-story" aria-hidden="true">
        <p class="ato-eyebrow">ATO CONSTRUCCIONES</p>
        <p class="ato-story-title">${msg("atoStoryTitle")}<br><em>${msg("atoStoryEmphasis")}</em></p>
        <p class="ato-story-copy">${msg("atoStoryCopy")}</p>
        <span class="ato-story-line"></span>
        <p class="ato-concept-caption">${msg("atoConceptCaption")}</p>
    </div>
    <div class="ato-auth-footer">
        <p>${msg("atoApprovalNotice")}</p>
        <#if client?? && client.baseUrl?has_content>
            <a class="ato-home-link" href="${client.baseUrl}"><span aria-hidden="true">&#8592;</span> ${msg("atoBackHome")}</a>
        </#if>
        <span class="ato-footer-brand">ATO Construcciones</span>
    </div>
</#macro>
