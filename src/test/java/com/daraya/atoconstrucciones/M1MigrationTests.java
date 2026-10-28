package com.daraya.atoconstrucciones;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class M1MigrationTests {
    @Test void upgradeFromM1KeepsS3ReferencesAndUsesDriveForNewReservations()throws Exception{
        try(var pg=EmbeddedPostgres.builder().setPort(0).start()){
            String url=pg.getJdbcUrl("postgres","postgres");Flyway.configure().dataSource(url,"postgres","").target("4").load().migrate();
            UUID actor=UUID.randomUUID(),party=UUID.randomUUID(),project=UUID.randomUUID(),old=UUID.randomUUID();
            try(var c=DriverManager.getConnection(url,"postgres","");var sql=c.createStatement()){
                sql.execute("INSERT INTO user_account(id,issuer,subject,email)VALUES ('"+actor+"','synthetic','subject','synthetic@example.invalid')");
                sql.execute("INSERT INTO party(id,kind,name,email,phone,created_by)VALUES ('"+party+"','CLIENT','Fixture','synthetic@example.invalid','20000000','"+actor+"')");
                sql.execute("INSERT INTO project(id,code,name,client_id,location,contact_email,starts_on,estimated_ends_on,created_by)VALUES ('"+project+"','OB-UPGRADE','Fixture','"+party+"','synthetic','synthetic@example.invalid','2026-10-01','2026-12-01','"+actor+"')");
                sql.execute("INSERT INTO document(id,project_id,owner_id,filename,media_type,expected_size,expected_sha256,object_key,object_version,document_class)VALUES ('"+old+"','"+project+"','"+actor+"','original.xml','application/xml',1,'"+"a".repeat(64)+"','business/old','s3-original-version','EVIDENCE')");
            }
            assertThat(Flyway.configure().dataSource(url,"postgres","").target("5").load().migrate().migrationsExecuted).isEqualTo(1);
            try(var c=DriverManager.getConnection(url,"postgres","");var sql=c.createStatement()){
                try(var row=sql.executeQuery("SELECT storage_provider,object_version FROM document WHERE id='"+old+"'")){
                    assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("S3");assertThat(row.getString(2)).isEqualTo("s3-original-version");
                }
                sql.execute("INSERT INTO document(id,project_id,owner_id,filename,media_type,expected_size,expected_sha256,object_key,document_class)VALUES ('"+UUID.randomUUID()+"','"+project+"','"+actor+"','new.xml','application/xml',1,'"+"b".repeat(64)+"','business/new','EVIDENCE')");
                try(var row=sql.executeQuery("SELECT storage_provider FROM document WHERE object_key='business/new'")){
                    assertThat(row.next()).isTrue();assertThat(row.getString(1)).isEqualTo("GOOGLE_DRIVE");
                }
            }
        }
    }
    @Test void upgradeFromM0PreservesExistingIdentityAuditAndSyntheticResource()throws Exception{
        try(var pg=EmbeddedPostgres.builder().setPort(0).start()){
            String url=pg.getJdbcUrl("postgres","postgres");Flyway.configure().dataSource(url,"postgres","").target("2").load().migrate();UUID actor=UUID.randomUUID(),resource=UUID.randomUUID();
            try(var connection=DriverManager.getConnection(url,"postgres","")){
                try(var stmt=connection.prepareStatement("INSERT INTO user_account(id,issuer,subject,email)VALUES (?,'synthetic',?,'synthetic@example.invalid')")){stmt.setObject(1,actor);stmt.setString(2,actor.toString());stmt.executeUpdate();}
                try(var stmt=connection.prepareStatement("INSERT INTO foundation_resource(id,name,created_by)VALUES (?,'Recurso existente',?)")){stmt.setObject(1,resource);stmt.setObject(2,actor);stmt.executeUpdate();}
                try(var stmt=connection.prepareStatement("INSERT INTO audit_event(id,actor_id,action,resource_id,trace_id)VALUES (?,?,'FOUNDATION_CREATED',?,?)")){stmt.setObject(1,UUID.randomUUID());stmt.setObject(2,actor);stmt.setObject(3,resource);stmt.setString(4,UUID.randomUUID().toString());stmt.executeUpdate();}
            }
            assertThat(Flyway.configure().dataSource(url,"postgres","").target("5").load().migrate().migrationsExecuted).isEqualTo(3);
            try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement();var r=s.executeQuery("SELECT (SELECT count(*) FROM foundation_resource),(SELECT count(*) FROM audit_event),(SELECT count(*) FROM project),(SELECT count(*) FROM flyway_schema_history WHERE success)")){
                assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(1);assertThat(r.getInt(2)).isEqualTo(1);assertThat(r.getInt(3)).isZero();assertThat(r.getInt(4)).isEqualTo(5);
            }
            Flyway.configure().dataSource(url,"postgres","").target("5").load().validate();
        }
    }
}
