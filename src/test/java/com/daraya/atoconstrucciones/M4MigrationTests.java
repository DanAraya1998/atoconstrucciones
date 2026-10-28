package com.daraya.atoconstrucciones;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class M4MigrationTests {
    @Test void expandsV7AndPreservesContractsScheduleHistoryAndPermissions()throws Exception {
        try(var pg=EmbeddedPostgres.builder().setPort(0).start()) {
            String url=pg.getJdbcUrl("postgres","postgres");Flyway.configure().dataSource(url,"postgres","").target("7").load().migrate();UUID actor=UUID.randomUUID(),party=UUID.randomUUID(),p=UUID.randomUUID();
            try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()) {
                s.execute("INSERT INTO user_account(id,issuer,subject,email) VALUES ('"+actor+"','fixture','fixture','fixture@example.invalid')");s.execute("INSERT INTO user_role VALUES ('"+actor+"','ADMIN')");
                s.execute("INSERT INTO party(id,kind,name,email,phone,created_by) VALUES ('"+party+"','CLIENT','Fixture','fixture@example.invalid','20000000','"+actor+"')");
                s.execute("INSERT INTO project(id,code,name,client_id,location,contact_email,starts_on,estimated_ends_on,created_by) VALUES ('"+p+"','M4-UPGRADE','Fixture','"+party+"','private','fixture@example.invalid','2026-10-01','2026-12-01','"+actor+"')");
                s.execute("INSERT INTO contract(project_id,number,net_amount,tax_amount,currency,starts_on,ends_on,scope) VALUES ('"+p+"','M4-UPGRADE',1000,130,'CRC','2026-10-01','2026-12-01','Fixture')");
                s.execute("INSERT INTO schedule(project_id,revision) VALUES ('"+p+"',1)");
            }
            assertThat(Flyway.configure().dataSource(url,"postgres","").target("8").load().migrate().migrationsExecuted).isEqualTo(1);Flyway.configure().dataSource(url,"postgres","").target("8").load().validate();
            try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement();var r=s.executeQuery("SELECT (SELECT net_amount+tax_amount FROM contract),(SELECT revision FROM schedule),(SELECT role FROM user_role),(SELECT count(*) FROM publication),(SELECT count(*) FROM flyway_schema_history WHERE success)")) {assertThat(r.next()).isTrue();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("1130");assertThat(r.getInt(2)).isEqualTo(1);assertThat(r.getString(3)).isEqualTo("ADMIN");assertThat(r.getInt(4)).isZero();assertThat(r.getInt(5)).isEqualTo(8);}
        }
    }
}
