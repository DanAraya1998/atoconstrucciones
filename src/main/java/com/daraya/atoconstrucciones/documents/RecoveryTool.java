package com.daraya.atoconstrucciones.documents;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;

/** CLI de mantenimiento offline; no arranca HTTP, jobs ni Spring Security. */
public final class RecoveryTool {
    public record Entry(String file,String key,String version,long size,String sha256,String mediaType){}
    private RecoveryTool(){}
    private static byte[] key(Path file)throws Exception{
        if(!Files.getPosixFilePermissions(file).equals(PosixFilePermissions.fromString("rw-------")))throw new IllegalArgumentException("Key file requires mode 600");
        String hex=Files.readString(file).trim();if(!hex.matches("[a-fA-F0-9]{64}"))throw new IllegalArgumentException("Key requires 64 hex characters");return HexFormat.of().parseHex(hex);
    }
    private static String required(String name){String value=System.getenv(name);if(value==null||value.isBlank())throw new IllegalArgumentException("Missing configuration: "+name);return value;}
    public static void main(String[] args)throws Exception{
        if(args.length!=4)throw new IllegalArgumentException("export <dump> <new-directory> <key-file> | decrypt <encrypted> <new-file> <key-file> | verify <directory> <unused> <key-file>");
        byte[] secret=key(Path.of(args[3]));var json=new JsonMapper();
        switch(args[0]){
            case "encrypt" -> RecoveryArchive.encrypt(Path.of(args[1]),Path.of(args[2]),secret);
            case "decrypt" -> RecoveryArchive.decrypt(Path.of(args[1]),Path.of(args[2]),secret);
            case "export" -> {
                if(!"true".equals(System.getenv("ATO_BACKUP_WRITES_PAUSED")))throw new IllegalStateException("Pause application, writes and integrations before export");
                Path folder=Path.of(args[2]);Files.createDirectory(folder,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
                var datasource=new DriverManagerDataSource(required("ATO_DB_URL"),required("ATO_DB_USER"),required("ATO_DB_PASSWORD"));var jdbc=new JdbcTemplate(datasource);
                var storage=new GoogleDriveObjectStorage(true,required("ATO_DRIVE_FOLDER_ID"),required("ATO_DRIVE_CREDENTIALS_FILE"),System.getenv().getOrDefault("ATO_DRIVE_SCOPE","https://www.googleapis.com/auth/drive.file"),jdbc,new DataSourceTransactionManager(datasource),json);
                int legacy=jdbc.queryForObject("SELECT count(*) FROM document WHERE object_version IS NOT NULL AND storage_provider<>'GOOGLE_DRIVE'",Integer.class);if(legacy!=0)throw new IllegalStateException("Legacy originals need verified migration before complete backup");
                var refs=jdbc.query("""
                    SELECT object_key,object_version,expected_size AS size,expected_sha256 AS sha256,media_type FROM document WHERE object_version IS NOT NULL
                    UNION SELECT object_key,object_version,size,sha256,'image/jpeg' FROM document_variant ORDER BY object_key
                    """,(r,n)->new Entry(String.format(Locale.ROOT,"object-%06d.ato",n),r.getString("object_key"),r.getString("object_version"),r.getLong("size"),r.getString("sha256"),r.getString("media_type")));
                for(var entry:refs){Path temp=Files.createTempFile(folder,"private-",".tmp");Files.delete(temp);try{RecoveryArchive.exportObject(storage,entry.key(),entry.version(),entry.size(),entry.sha256(),temp);RecoveryArchive.encrypt(temp,folder.resolve(entry.file()),secret);}finally{Files.deleteIfExists(temp);}}
                Path manifest=Files.createTempFile(folder,"private-",".tmp");try{Files.writeString(manifest,json.writeValueAsString(refs));RecoveryArchive.encrypt(manifest,folder.resolve("manifest.ato"),secret);}finally{Files.deleteIfExists(manifest);}
                RecoveryArchive.encrypt(Path.of(args[1]),folder.resolve("database.ato"),secret);
                System.out.println("Encrypted DB and "+refs.size()+" verified object revisions. Keep key and IdP/credential escrow separately.");
            }
            case "verify" -> {
                Path folder=Path.of(args[1]),temp=Files.createTempDirectory("ato-recovery-verify-");
                try{Path manifest=temp.resolve("manifest.json");RecoveryArchive.decrypt(folder.resolve("manifest.ato"),manifest,secret);var refs=json.readValue(Files.readString(manifest),Entry[].class);
                    for(var entry:refs){if(!entry.file().matches("object-[0-9]{6}\\.ato"))throw new IllegalArgumentException("Invalid manifest path");Path plain=temp.resolve(entry.file());try{RecoveryArchive.decrypt(folder.resolve(entry.file()),plain,secret);byte[] bytes=Files.readAllBytes(plain);if(bytes.length!=entry.size()||!FileInspection.sha256(bytes).equals(entry.sha256()))throw new IllegalStateException("Backup object integrity failure");}finally{Files.deleteIfExists(plain);}}
                    Path dump=temp.resolve("database.dump");RecoveryArchive.decrypt(folder.resolve("database.ato"),dump,secret);Files.delete(dump);Files.delete(manifest);System.out.println("Authenticated DB and "+refs.length+" object copies verified; restore DB and resource permissions separately.");
                }finally{try(var files=Files.list(temp)){for(Path p:files.toList())Files.deleteIfExists(p);}Files.deleteIfExists(temp);}
            }
            default -> throw new IllegalArgumentException("Unknown recovery operation");
        }
        Arrays.fill(secret,(byte)0);
    }
}
