package com.daraya.atoconstrucciones;
import com.daraya.atoconstrucciones.documents.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class RecoveryArchiveTests {
    @TempDir Path dir;
    @Test void separateRecoveredKeyRestoresBytesAndTamperingOrWrongKeyNeverProducesPlaintext()throws Exception{
        byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);byte[] bytes="Evidencia y manifestación sintéticas".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path source=dir.resolve("source"),encrypted=dir.resolve("copy.ato"),recovered=dir.resolve("recovered");Files.write(source,bytes);
        RecoveryArchive.encrypt(source,encrypted,key);assertThat(new String(Files.readAllBytes(encrypted),java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(new String(bytes,java.nio.charset.StandardCharsets.ISO_8859_1));
        Path escrow=dir.resolve("separate-key");Files.write(escrow,key);RecoveryArchive.decrypt(encrypted,recovered,Files.readAllBytes(escrow));assertThat(Files.readAllBytes(recovered)).containsExactly(bytes);
        byte[] wrong=new byte[32];assertThatThrownBy(()->RecoveryArchive.decrypt(encrypted,dir.resolve("wrong"),wrong)).isInstanceOf(Exception.class);assertThat(dir.resolve("wrong")).doesNotExist();
        byte[] corrupted=Files.readAllBytes(encrypted);corrupted[20]^=1;Path tampered=dir.resolve("tampered.ato");Files.write(tampered,corrupted);assertThatThrownBy(()->RecoveryArchive.decrypt(tampered,dir.resolve("tampered-output"),key)).isInstanceOf(Exception.class);assertThat(dir.resolve("tampered-output")).doesNotExist();
    }
    @Test void objectBackupFailsOnHashOrSizeMismatchAndNeverCreatesUnverifiedCopy()throws Exception{
        ObjectStoragePort storage=new ObjectStoragePort(){public String put(String key,byte[] bytes,String type){return "revision";}public Stored get(String key,String version,long limit){return new Stored(new byte[]{1,2},version);}public void delete(String key,String version){}public boolean available(){return true;}};
        assertThatThrownBy(()->RecoveryArchive.exportObject(storage,"business/x","revision",3,"a".repeat(64),dir.resolve("bad"))).isInstanceOf(com.daraya.atoconstrucciones.shared.ApiException.class);assertThat(dir.resolve("bad")).doesNotExist();
        RecoveryArchive.exportObject(storage,"business/x","revision",2,FileInspection.sha256(new byte[]{1,2}),dir.resolve("good"));assertThat(Files.readAllBytes(dir.resolve("good"))).containsExactly(1,2);
    }
}
