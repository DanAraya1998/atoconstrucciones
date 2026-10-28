package com.daraya.atoconstrucciones;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class AccountNameMigrationTests {
    @Test void expandsV8WithoutChangingIdentitiesRolesAndMemberships()throws Exception{
        try(var pg=EmbeddedPostgres.builder().setPort(0).start()){
            String url=pg.getJdbcUrl("postgres","postgres");Flyway.configure().dataSource(url,"postgres","").target("8").load().migrate();UUID actor=UUID.randomUUID(),party=UUID.randomUUID();
            try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){
                s.execute("INSERT INTO user_account(id,issuer,subject,email) VALUES ('"+actor+"','fixture','fixture','fixture@example.invalid')");s.execute("INSERT INTO user_role VALUES ('"+actor+"','CLIENTE')");
                s.execute("INSERT INTO party(id,kind,name,email,phone,created_by) VALUES ('"+party+"','CLIENT','Fixture','fixture@example.invalid','20000000','"+actor+"')");s.execute("INSERT INTO party_member VALUES ('"+party+"','"+actor+"',true)");
            }
            assertThat(Flyway.configure().dataSource(url,"postgres","").target("9").load().migrate().migrationsExecuted).isEqualTo(1);Flyway.configure().dataSource(url,"postgres","").target("9").load().validate();
            try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement();var r=s.executeQuery("SELECT u.display_name,u.email,u.issuer,u.subject,r.role,m.active FROM user_account u JOIN user_role r ON r.user_id=u.id JOIN party_member m ON m.user_id=u.id")){
                assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEmpty();assertThat(r.getString(2)).isEqualTo("fixture@example.invalid");assertThat(r.getString(3)).isEqualTo("fixture");assertThat(r.getString(4)).isEqualTo("fixture");assertThat(r.getString(5)).isEqualTo("CLIENTE");assertThat(r.getBoolean(6)).isTrue();
            }
        }
    }
}
