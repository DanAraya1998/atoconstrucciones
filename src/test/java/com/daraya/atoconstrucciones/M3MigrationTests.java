package com.daraya.atoconstrucciones;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class M3MigrationTests {
    @Test void expandsM2WithoutChangingIdentityContractDriveOrTreasury()throws Exception {
        try(var pg=EmbeddedPostgres.builder().setPort(0).start()) {
            String url=pg.getJdbcUrl("postgres","postgres");Flyway.configure().dataSource(url,"postgres","").target("6").load().migrate();
            UUID actor=UUID.randomUUID(),party=UUID.randomUUID(),project=UUID.randomUUID(),doc=UUID.randomUUID();
            try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()) {
                s.execute("INSERT INTO user_account(id,issuer,subject,email) VALUES ('"+actor+"','synthetic','synthetic','synthetic@example.invalid')");
                s.execute("INSERT INTO party(id,kind,name,email,phone,created_by) VALUES ('"+party+"','CLIENT','Fixture','synthetic@example.invalid','20000000','"+actor+"')");
                s.execute("INSERT INTO project(id,code,name,client_id,location,contact_email,starts_on,estimated_ends_on,created_by) VALUES ('"+project+"','UPGRADE-M3','Fixture','"+party+"','synthetic','synthetic@example.invalid','2026-10-01','2026-12-01','"+actor+"')");
                s.execute("INSERT INTO document(id,project_id,owner_id,filename,media_type,expected_size,expected_sha256,object_key,object_version,document_class) VALUES ('"+doc+"','"+project+"','"+actor+"','original.xml','application/xml',1,'"+"a".repeat(64)+"','drive-durable-id','drive-revision','EVIDENCE')");
                s.execute("INSERT INTO contract(project_id,number,net_amount,tax_amount,currency,starts_on,ends_on,scope) VALUES ('"+project+"','UPGRADE-M3',1000,130,'CRC','2026-10-01','2026-12-01','Fixture')");
            }
            try(var c=DriverManager.getConnection(url,"postgres","");var statement=c.createStatement()) {
                statement.execute("INSERT INTO treasury_account(id,name,kind,currency,created_by) VALUES ('"+UUID.randomUUID()+"','Cuenta M2 conservada','BANK','CRC','"+actor+"')");
            }
            assertThat(Flyway.configure().dataSource(url,"postgres","").target("7").load().migrate().migrationsExecuted).isEqualTo(1);
            Flyway.configure().dataSource(url,"postgres","").target("7").load().validate();
            try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement();var r=s.executeQuery("SELECT (SELECT count(*) FROM user_account),(SELECT net_amount+tax_amount FROM contract),(SELECT object_version FROM document),(SELECT count(*) FROM treasury_movement),(SELECT count(*) FROM flyway_schema_history WHERE success)")) {
                assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(1);assertThat(r.getBigDecimal(2)).isEqualByComparingTo("1130");assertThat(r.getString(3)).isEqualTo("drive-revision");assertThat(r.getInt(4)).isZero();assertThat(r.getInt(5)).isEqualTo(7);
                try(var check=c.createStatement();var rows=check.executeQuery("SELECT (SELECT count(*) FROM treasury_account),(SELECT count(*) FROM schedule)")){assertThat(rows.next()).isTrue();assertThat(rows.getInt(1)).isEqualTo(1);assertThat(rows.getInt(2)).isZero();}
            }
        }
    }
}
