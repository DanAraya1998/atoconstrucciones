package com.daraya.atoconstrucciones.publication;

import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.documents.*;
import com.daraya.atoconstrucciones.identity.*;
import com.daraya.atoconstrucciones.projects.ProjectService;
import com.daraya.atoconstrucciones.shared.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;

@Service
public class PublicationService {
    public record PhotoInput(@NotNull UUID documentId,@NotNull UUID consentId,@NotBlank @Size(max=300) String alt){}
    public record Input(UUID projectId,@NotBlank @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max=100) String slug,
        @NotBlank @Size(max=160) String title,@NotBlank @Size(max=3000) String summary,
        @NotBlank @Size(max=100) String category,@NotNull @Size(max=100) String region,
        @Min(1900) @Max(2200) Integer year,@NotNull @Size(min=1,max=12) List<@Valid PhotoInput> photos){}
    public record Decision(@NotBlank @Size(max=1000) String reason,@AssertTrue boolean privacyReviewed){}
    public record Reason(@NotBlank @Size(max=1000) String reason){}
    public record Asset(UUID id,UUID documentId,UUID consentId,String alt,int width,int height){}
    public record Publication(UUID id,UUID projectId,String slug,String title,String summary,String category,String region,Integer year,String state,long version,List<Asset> photos,boolean publiclyAvailable){}
    public record PublicPhoto(String url,String alt,int width,int height){}
    public record PublicProject(String slug,String title,String summary,String category,String region,Integer year,List<PublicPhoto> photos){}
    public record PublicPage(List<PublicProject> items,int page,int size){}
    private final JdbcTemplate jdbc;private final ProjectService projects;private final PrivacyService privacy;
    private final PublicVariantService variants;private final AuditService audit;private final IdempotencyService commands;
    private final SensitiveAuthenticationPolicy sensitive;private final JsonMapper json;
    public PublicationService(JdbcTemplate jdbc,ProjectService projects,PrivacyService privacy,PublicVariantService variants,AuditService audit,IdempotencyService commands,SensitiveAuthenticationPolicy sensitive,JsonMapper json){this.jdbc=jdbc;this.projects=projects;this.privacy=privacy;this.variants=variants;this.audit=audit;this.commands=commands;this.sensitive=sensitive;this.json=json;}
    // Public projection has no joins to money, parties, location or source object identifiers.
    private static final String LIVE="""
      p.state='PUBLISHED' AND EXISTS(SELECT 1 FROM published_asset a WHERE a.publication_id=p.id)
      AND NOT EXISTS(SELECT 1 FROM published_asset a JOIN publication_consent c ON c.id=a.consent_id
        JOIN document d ON d.id=a.document_id JOIN privacy_notice n ON n.id=c.notice_id
        JOIN document e ON e.id=c.evidence_id WHERE a.publication_id=p.id AND
        (c.document_id<>d.id OR c.project_id<>d.project_id OR c.withdrawn_at IS NOT NULL
         OR c.expires_on<(now() AT TIME ZONE 'America/Costa_Rica')::date OR c.purpose<>'PORTFOLIO' OR c.channels<>'WEBSITE'
         OR n.state<>'APPROVED' OR d.state<>'CLEAN' OR e.state<>'CLEAN' OR d.document_class<>'PHOTO'))
      """;
    private boolean live(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM publication p WHERE p.id=? AND "+LIVE+")",Boolean.class,id));}
    private Publication load(UUID id,boolean lock){
        var rows=jdbc.query("SELECT * FROM publication WHERE id=?"+(lock?" FOR UPDATE":""),(r,n)->new Publication(r.getObject("id",UUID.class),r.getObject("project_id",UUID.class),r.getString("slug"),r.getString("title"),r.getString("summary"),r.getString("category"),r.getString("region"),(Integer)r.getObject("year"),r.getString("state"),r.getLong("version"),List.of(),false),id);
        if(rows.isEmpty())throw ApiException.missing();var p=rows.getFirst();
        var assets=jdbc.query("SELECT a.*,v.width,v.height FROM published_asset a JOIN document_variant v ON v.id=a.variant_id WHERE publication_id=? ORDER BY position",(r,n)->new Asset(r.getObject("id",UUID.class),r.getObject("document_id",UUID.class),r.getObject("consent_id",UUID.class),r.getString("alt"),r.getInt("width"),r.getInt("height")),id);
        return new Publication(p.id(),p.projectId(),p.slug(),p.title(),p.summary(),p.category(),p.region(),p.year(),p.state(),p.version(),assets,live(id));
    }
    private void access(Actor actor,Publication p){if(p.projectId()==null)actor.requireManager();else projects.operations(actor,p.projectId());}
    public Publication read(Actor actor,UUID id){var p=load(id,false);access(actor,p);return p;}
    public List<Publication> list(Actor actor,UUID projectId){
        if(projectId==null)actor.requireManager();else projects.operations(actor,projectId);
        return jdbc.queryForList("SELECT id FROM publication WHERE (?::uuid IS NULL OR project_id=?) ORDER BY created_at DESC LIMIT 100",UUID.class,projectId,projectId).stream().map(id->read(actor,id)).toList();
    }
    private void prepare(Actor actor,UUID id,Input input){
        if(input.photos().stream().map(PhotoInput::documentId).distinct().count()!=input.photos().size())Versions.invalid("No repita fotografías.");
        int position=0;
        for(var photo:input.photos()){
            var consent=privacy.requireWebsiteConsent(actor,photo.consentId(),photo.documentId());
            if(input.projectId()!=null&&!input.projectId().equals(consent.projectId()))throw ApiException.missing();
            var variant=variants.create(actor,photo.documentId());
            jdbc.update("INSERT INTO published_asset(id,publication_id,document_id,variant_id,consent_id,alt,position) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),id,photo.documentId(),variant.id(),photo.consentId(),photo.alt().trim(),position++);
        }
    }
    private void snapshot(Actor actor,UUID id,String action,String reason){var p=load(id,false);jdbc.update("INSERT INTO publication_revision(id,publication_id,version,action,snapshot,reason,actor_id) VALUES (?,?,?,?,?::jsonb,?,?)",UUID.randomUUID(),id,p.version(),action,json.writeValueAsString(p),reason,actor.id());audit.record(actor.id(),"PUBLICATION_"+action,id,Map.of("version",p.version(),"reason",reason));}
    @Transactional public Publication create(Actor actor,String key,Input input){
        if(input.projectId()==null)actor.requireManager();else projects.operations(actor,input.projectId());
        return commands.execute(actor.id(),"POST /publications",key,input,Publication.class,()->{
            if(input.projectId()!=null)projects.lock(actor,input.projectId());
            UUID id=UUID.randomUUID();
            slugAvailable(input.slug(),id);
            jdbc.update("INSERT INTO publication(id,project_id,slug,title,summary,category,region,year,created_by) VALUES (?,?,?,?,?,?,?,?,?)",id,input.projectId(),input.slug(),input.title().trim(),input.summary().trim(),input.category().trim(),input.region().trim(),input.year(),actor.id());
            prepare(actor,id,input);snapshot(actor,id,"DRAFTED","Preparación editorial");return load(id,false);
        });
    }
    private void slugAvailable(String slug,UUID id){jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,8))",(r,n)->1,slug);if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM publication WHERE slug=? AND id<>?)",Boolean.class,slug,id)))throw new ApiException(409,"DUPLICATE_SLUG","La URL ya pertenece a otra ficha.");}
    @Transactional public Publication edit(Actor actor,UUID id,String key,String etag,Input input){
        var current=read(actor,id);if(!Objects.equals(current.projectId(),input.projectId()))Versions.invalid("El origen de una ficha no se cambia.");
        return commands.execute(actor.id(),"PUT /publications/"+id,key,List.of(input,Objects.toString(etag,"")),Publication.class,()->{
            if(current.projectId()!=null)projects.lock(actor,current.projectId());
            var p=load(id,true);Versions.check(etag,p.version());if(p.state().equals("PUBLISHED"))Versions.conflict("Retire la ficha antes de editarla.");slugAvailable(input.slug(),id);
            jdbc.update("UPDATE publication SET slug=?,title=?,summary=?,category=?,region=?,year=?,state='DRAFT',version=version+1,approved_by=NULL,published_at=NULL WHERE id=?",input.slug(),input.title().trim(),input.summary().trim(),input.category().trim(),input.region().trim(),input.year(),id);
            jdbc.update("DELETE FROM published_asset WHERE publication_id=?",id);prepare(actor,id,input);snapshot(actor,id,"EDITED","Nueva revisión editorial");return load(id,false);
        });
    }
    @Transactional public Publication publish(Actor actor,UUID id,String key,String etag,Decision input){
        actor.requireManager();sensitive.require(actor);read(actor,id);
        if(!input.privacyReviewed())Versions.invalid("Confirme la revisión visual de personas, domicilio y datos privados.");
        return commands.execute(actor.id(),"POST /publications/"+id+"/publish",key,List.of(input,Objects.toString(etag,"")),Publication.class,()->{
            var p=load(id,true);Versions.check(etag,p.version());if(!p.state().equals("DRAFT"))Versions.conflict("Solo un borrador puede publicarse.");
            for(var photo:p.photos())privacy.requireWebsiteConsent(actor,photo.consentId(),photo.documentId());
            jdbc.update("UPDATE publication SET state='PUBLISHED',version=version+1,approved_by=?,published_at=now() WHERE id=?",actor.id(),id);
            if(!live(id))Versions.conflict("Una fotografía perdió la autorización.");snapshot(actor,id,"PUBLISHED",input.reason());return load(id,false);
        });
    }
    @Transactional public Publication withdraw(Actor actor,UUID id,String key,String etag,Reason input){
        actor.requireManager();sensitive.require(actor);read(actor,id);
        return commands.execute(actor.id(),"POST /publications/"+id+"/withdraw",key,List.of(input,Objects.toString(etag,"")),Publication.class,()->{var p=load(id,true);Versions.check(etag,p.version());if(!p.state().equals("PUBLISHED"))Versions.conflict("La ficha no está publicada.");jdbc.update("UPDATE publication SET state='WITHDRAWN',version=version+1 WHERE id=?",id);snapshot(actor,id,"WITHDRAWN",input.reason());return load(id,false);});
    }
    private static final String PUBLIC_SELECT="""
      SELECT p.slug,p.title,p.summary,p.category,p.region,p.year,
      (SELECT jsonb_agg(jsonb_build_object('url','/api/v1/public/assets/'||a.id,'alt',a.alt,'width',v.width,'height',v.height) ORDER BY a.position)::text
         FROM published_asset a JOIN document_variant v ON v.id=a.variant_id WHERE a.publication_id=p.id) AS photos_json
      FROM publication p WHERE
      """;
    private org.springframework.jdbc.core.RowMapper<PublicProject> publicMapper(){return (r,n)->new PublicProject(r.getString("slug"),r.getString("title"),r.getString("summary"),r.getString("category"),r.getString("region"),(Integer)r.getObject("year"),Arrays.asList(json.readValue(r.getString("photos_json"),PublicPhoto[].class)));}
    public PublicPage publicList(int page,int size){
        if(page<0||page>100000||size<1||size>100)throw new ApiException(400,"INVALID_PAGINATION","Paginación inválida.");
        return new PublicPage(jdbc.query(PUBLIC_SELECT+LIVE+" ORDER BY published_at DESC,p.id LIMIT ? OFFSET ?",publicMapper(),size,page*size),page,size);
    }
    public PublicProject publicRead(String slug){var rows=jdbc.query(PUBLIC_SELECT+LIVE+" AND slug=?",publicMapper(),slug);if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();}
    @Transactional public byte[] preview(Actor actor,UUID publicationId,UUID assetId){
        var p=read(actor,publicationId);if(p.photos().stream().noneMatch(a->a.id().equals(assetId)))throw ApiException.missing();
        UUID variant=jdbc.queryForObject("SELECT variant_id FROM published_asset WHERE id=? AND publication_id=?",UUID.class,assetId,publicationId);return variants.bytes(variant);
    }
    @Transactional public byte[] publicAsset(UUID id){
        var rows=jdbc.queryForList("SELECT publication_id,variant_id FROM published_asset WHERE id=?",id);if(rows.isEmpty())throw ApiException.missing();var row=rows.getFirst();UUID publication=(UUID)row.get("publication_id");
        // Retirada editorial y de consentimiento se serializan con una descarga en curso.
        jdbc.queryForList("SELECT id FROM publication WHERE id=? FOR SHARE",UUID.class,publication);
        jdbc.queryForList("SELECT c.id FROM publication_consent c JOIN published_asset a ON a.consent_id=c.id WHERE a.publication_id=? ORDER BY c.id FOR SHARE OF c",UUID.class,publication);
        jdbc.queryForList("SELECT d.id FROM document d WHERE d.id IN (SELECT document_id FROM published_asset WHERE publication_id=?) OR d.id IN (SELECT c.evidence_id FROM publication_consent c JOIN published_asset a ON a.consent_id=c.id WHERE a.publication_id=?) ORDER BY d.id FOR SHARE",UUID.class,publication,publication);
        if(!live(publication)||!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM published_asset WHERE id=? AND publication_id=?)",Boolean.class,id,publication)))throw ApiException.missing();return variants.bytes((UUID)row.get("variant_id"));
    }
}
