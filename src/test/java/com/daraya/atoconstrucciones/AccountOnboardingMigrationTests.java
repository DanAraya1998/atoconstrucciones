package com.daraya.atoconstrucciones;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AccountOnboardingMigrationTests {
    @Test void upgradePreservesExistingPermissionsDisabledAccountsAndOidcIdentity()throws Exception{
        try(var pg=EmbeddedPostgres.builder().setPort(0).start()){
            String url=pg.getJdbcUrl("postgres","postgres");Flyway.configure().dataSource(url,"postgres","").target("9").load().migrate();
            UUID active=UUID.randomUUID(),disabled=UUID.randomUUID(),pending=UUID.randomUUID(),party=UUID.randomUUID();
            try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){
                for(UUID id:new UUID[]{active,disabled,pending})s.execute("INSERT INTO user_account(id,issuer,subject,email,display_name,enabled) VALUES ('"+id+"','fixture','"+id+"','fixture@example.invalid','Nombre conservado',"+!id.equals(disabled)+")");
                s.execute("INSERT INTO user_role VALUES ('"+active+"','CLIENTE'),('"+disabled+"','PROVEEDOR')");
                s.execute("INSERT INTO party(id,kind,name,email,phone,created_by) VALUES ('"+party+"','CLIENT','Cliente demo','fixture@example.invalid','20000000','"+active+"')");s.execute("INSERT INTO party_member VALUES ('"+party+"','"+active+"',true)");
            }
            assertThat(Flyway.configure().dataSource(url,"postgres","").target("10").load().migrate().migrationsExecuted).isEqualTo(1);Flyway.configure().dataSource(url,"postgres","").target("10").load().validate();
            try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement();var r=s.executeQuery("SELECT id,subject,display_name,enabled,access_state,email_verified,access_version FROM user_account")){
                int count=0;while(r.next()){count++;UUID id=r.getObject(1,UUID.class);assertThat(r.getString(2)).isEqualTo(id.toString());assertThat(r.getString(3)).isEqualTo("Nombre conservado");assertThat(r.getBoolean(4)).isEqualTo(!id.equals(disabled));assertThat(r.getString(5)).isEqualTo(id.equals(pending)?"PENDING":"APPROVED");assertThat(r.getBoolean(6)).isTrue();assertThat(r.getLong(7)).isEqualTo(1);}assertThat(count).isEqualTo(3);
                try(var q=s.executeQuery("SELECT count(*) FROM party_member WHERE active")){q.next();assertThat(q.getInt(1)).isEqualTo(1);}
            }
        }
    }
}
