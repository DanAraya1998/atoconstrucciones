package com.daraya.atoconstrucciones;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class SupplierCategoryMigrationTests {
    @Test void v10UpgradePreservesDetailsVersionsAndUnassignedSuppliers()throws Exception{
        try(var pg=EmbeddedPostgres.builder().setPort(0).start()){
            String url=pg.getJdbcUrl("postgres","postgres");Flyway.configure().dataSource(url,"postgres","").target("10").load().migrate();
            UUID actor=UUID.randomUUID();
            try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement()){
                s.execute("INSERT INTO user_account(id,issuer,subject,email) VALUES ('"+actor+"','fixture','fixture','fixture@example.invalid')");
                for(String kind:new String[]{"CLIENT","SUPPLIER"})s.execute("INSERT INTO party(id,kind,name,email,phone,category,version,created_by) VALUES ('"+UUID.randomUUID()+"','"+kind+"','Fixture','fixture@example.invalid','20000000','Detalle original',7,'"+actor+"')");
            }
            assertThat(Flyway.configure().dataSource(url,"postgres","").load().migrate().migrationsExecuted).isEqualTo(1);Flyway.configure().dataSource(url,"postgres","").load().validate();
            try(var c=DriverManager.getConnection(url,"postgres","");var s=c.createStatement();var rows=s.executeQuery("SELECT kind,category,supplier_category,version FROM party ORDER BY kind")){
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("CLIENT");assertThat(rows.getString(3)).isNull();assertThat(rows.getLong(4)).isEqualTo(7);
                assertThat(rows.next()).isTrue();assertThat(rows.getString(1)).isEqualTo("SUPPLIER");assertThat(rows.getString(2)).isEqualTo("Detalle original");assertThat(rows.getString(3)).isEqualTo("OTHER");assertThat(rows.getLong(4)).isEqualTo(7);assertThat(rows.next()).isFalse();
                assertThatThrownBy(()->s.execute("UPDATE party SET supplier_category='INVALID' WHERE kind='SUPPLIER'")).isInstanceOf(SQLException.class);
                assertThatThrownBy(()->s.execute("UPDATE party SET supplier_category=NULL WHERE kind='SUPPLIER'")).isInstanceOf(SQLException.class);
            }
        }
    }
}
