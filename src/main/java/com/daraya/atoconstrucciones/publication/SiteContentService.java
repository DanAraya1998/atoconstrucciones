package com.daraya.atoconstrucciones.publication;
import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.identity.*;
import com.daraya.atoconstrucciones.shared.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
@Service
public class SiteContentService {
    public record Input(@NotBlank @Size(max=160)String heading,@NotBlank @Size(max=2000)String introduction,@NotBlank @Size(max=4000)String about,@NotNull @Size(min=1,max=8)List<@NotBlank @Size(max=160)String> services){}
    public record Content(String heading,String introduction,String about,List<String> services,String state,long version){}
    public record PublicContent(boolean approved,String heading,String introduction,String about,List<String> services){}
    private final JdbcTemplate jdbc;private final JsonMapper json;private final IdempotencyService commands;private final AuditService audit;private final SensitiveAuthenticationPolicy sensitive;
    private static final UUID RESOURCE=UUID.fromString("00000000-0000-0000-0000-000000000004");
    public SiteContentService(JdbcTemplate jdbc,JsonMapper json,IdempotencyService commands,AuditService audit,SensitiveAuthenticationPolicy sensitive){this.jdbc=jdbc;this.json=json;this.commands=commands;this.audit=audit;this.sensitive=sensitive;}
    private Content load(boolean lock){return jdbc.queryForObject("SELECT *,services::text AS service_json FROM public_site_content WHERE id=1"+(lock?" FOR UPDATE":""),(r,n)->new Content(r.getString("heading"),r.getString("introduction"),r.getString("about"),Arrays.asList(json.readValue(r.getString("service_json"),String[].class)),r.getString("state"),r.getLong("version")));}
    public Content read(Actor actor){actor.requireManager();return load(false);}
    public PublicContent publicRead(){var c=load(false);return c.state().equals("APPROVED")?new PublicContent(true,c.heading(),c.introduction(),c.about(),c.services()):new PublicContent(false,"","","",List.of());}
    @Transactional public Content edit(Actor actor,String key,String etag,Input input){actor.requireManager();return commands.execute(actor.id(),"PUT /publication-site",key,List.of(input,Objects.toString(etag,"")),Content.class,()->{var c=load(true);Versions.check(etag,c.version());jdbc.update("UPDATE public_site_content SET heading=?,introduction=?,about=?,services=?::jsonb,state='DRAFT',version=version+1,approved_by=NULL,approved_at=NULL WHERE id=1",input.heading().trim(),input.introduction().trim(),input.about().trim(),json.writeValueAsString(input.services()));audit.record(actor.id(),"SITE_CONTENT_DRAFTED",RESOURCE);return load(false);});}
    @Transactional public Content approve(Actor actor,String key,String etag){actor.requireManager();sensitive.require(actor);return commands.execute(actor.id(),"POST /publication-site/approve",key,Objects.toString(etag,""),Content.class,()->{var c=load(true);Versions.check(etag,c.version());if(!c.state().equals("DRAFT")||c.heading().isBlank()||c.services().isEmpty())Versions.conflict("Guarde los textos antes de aprobar.");jdbc.update("UPDATE public_site_content SET state='APPROVED',version=version+1,approved_by=?,approved_at=now() WHERE id=1",actor.id());audit.record(actor.id(),"SITE_CONTENT_APPROVED",RESOURCE,Map.of("version",c.version()+1));return load(false);});}
}
