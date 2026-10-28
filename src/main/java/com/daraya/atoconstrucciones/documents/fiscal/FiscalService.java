package com.daraya.atoconstrucciones.documents.fiscal;

import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.costs.CostService;
import com.daraya.atoconstrucciones.documents.DocumentService;
import com.daraya.atoconstrucciones.finance.FinancePolicy;
import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.shared.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;

@Service
public class FiscalService {
    public record Import(@NotNull UUID projectId,@NotNull UUID documentId) {}
    public record Voucher(UUID id,UUID projectId,UUID documentId,String sha256,FiscalParser.Extracted extraction,UUID expenseId) {}
    private final JdbcTemplate jdbc;private final DocumentService documents;private final FinancePolicy policy;private final IdempotencyService commands;private final AuditService audit;private final JsonMapper json;
    public FiscalService(JdbcTemplate jdbc,DocumentService documents,FinancePolicy policy,IdempotencyService commands,AuditService audit,JsonMapper json) {this.jdbc=jdbc;this.documents=documents;this.policy=policy;this.commands=commands;this.audit=audit;this.json=json;}
    public Voucher read(Actor actor,UUID id) {
        var rows=jdbc.queryForList("SELECT * FROM fiscal_voucher WHERE id=?",id);if(rows.isEmpty())throw ApiException.missing();var row=rows.getFirst();UUID project=(UUID)row.get("project_id");policy.operations(actor,project);
        var expense=jdbc.queryForList("SELECT id FROM expense WHERE voucher_id=? OR fiscal_key=?",UUID.class,id,row.get("fiscal_key"));
        return new Voucher(id,project,(UUID)row.get("document_id"),(String)row.get("sha256"),json.readValue(row.get("extraction").toString(),FiscalParser.Extracted.class),expense.isEmpty()?null:expense.getFirst());
    }
    public List<Voucher> list(Actor actor,UUID p,int page,int size) {policy.operations(actor,p);CostService.pagination(page,size);return jdbc.queryForList("SELECT id FROM fiscal_voucher WHERE project_id=? ORDER BY imported_at,id LIMIT ? OFFSET ?",UUID.class,p,size,page*size).stream().map(id->read(actor,id)).toList();}
    @Transactional public Voucher importXml(Actor actor,String key,Import input) {
        policy.operations(actor,input.projectId());
        return commands.execute(actor.id(),"POST /fiscal-vouchers/imports",key,input,Voucher.class,()->{
            policy.lock(actor,List.of(input.projectId()),false);var doc=documents.read(actor,input.documentId());if(!doc.projectId().equals(input.projectId()))throw ApiException.missing();if(!doc.mediaType().equals("application/xml"))Versions.invalid("Seleccione un documento XML limpio.");
            var download=documents.download(actor,input.documentId());var extracted=BoundedFiscalParser.parse(download.bytes());
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"fiscal-identity:"+extracted.key());
            var existing=jdbc.queryForList("SELECT id,sha256,project_id FROM fiscal_voucher WHERE fiscal_key=?",extracted.key());UUID id;
            if(!existing.isEmpty()) {
                var row=existing.getFirst();policy.operations(actor,(UUID)row.get("project_id"));
                if(!row.get("sha256").equals(doc.sha256()))throw new ApiException(409,"DIVERGENT_FISCAL_KEY","La clave fiscal ya existe con bytes distintos; requiere revisión de integridad.");id=(UUID)row.get("id");
            } else {
                id=UUID.randomUUID();jdbc.update("INSERT INTO fiscal_voucher(id,project_id,fiscal_key,sha256,document_id,voucher_type,currency,net_amount,tax_amount,gross_amount,reference_key,extraction,bundle_hash,imported_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)",id,input.projectId(),extracted.key(),doc.sha256(),doc.id(),extracted.type(),extracted.currency(),new BigDecimal(extracted.net()),new BigDecimal(extracted.tax()),new BigDecimal(extracted.total()),extracted.referenceKey(),json.writeValueAsString(extracted),extracted.bundleHash(),actor.id());
            }
            documents.retain(actor,input.projectId(),doc.id(),"FISCAL_"+id);jdbc.update("INSERT INTO fiscal_voucher_document(voucher_id,document_id,project_id) VALUES (?,?,?) ON CONFLICT DO NOTHING",id,doc.id(),input.projectId());
            audit.record(actor.id(),"FISCAL_IMPORTED",input.projectId(),Map.of("voucherId",id,"documentId",doc.id(),"bundleHash",extracted.bundleHash()));return read(actor,id);
        });
    }
}
