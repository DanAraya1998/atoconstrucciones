package com.daraya.atoconstrucciones.documents;

import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.shared.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import javax.imageio.ImageIO;

@Service
public class PublicVariantService {
    public record Variant(UUID id,UUID documentId,String key,String objectVersion,String sha256,long size,int width,int height){}
    private final DocumentService documents;private final ObjectStoragePort storage;private final JdbcTemplate jdbc;
    public PublicVariantService(DocumentService documents,ObjectStoragePort storage,JdbcTemplate jdbc){this.documents=documents;this.storage=storage;this.jdbc=jdbc;}
    @Transactional public Variant create(Actor actor,UUID documentId){
        var source=documents.download(actor,documentId);
        if(!source.document().documentClass().equals("PHOTO"))Versions.invalid("Seleccione una fotografía limpia.");
        byte[] bytes=convert(source.bytes(),source.document().mediaType());
        UUID id=UUID.randomUUID();String key="business/variants/"+id;
        String version=storage.put(key,bytes,"image/jpeg","portfolio.jpg");
        try {
            var decoded=ImageIO.read(new ByteArrayInputStream(bytes));
            var v=new Variant(id,documentId,key,version,FileInspection.sha256(bytes),bytes.length,decoded.getWidth(),decoded.getHeight());
            jdbc.update("INSERT INTO document_variant(id,document_id,object_key,object_version,sha256,size,width,height) VALUES (?,?,?,?,?,?,?,?)",id,documentId,key,version,v.sha256(),v.size(),v.width(),v.height());return v;
        }catch(IOException e){throw new ApiException(503,"VARIANT_UNAVAILABLE","No se pudo preparar la imagen.");}
    }
    public Variant read(UUID id){var rows=jdbc.query("SELECT * FROM document_variant WHERE id=?",(r,n)->new Variant(r.getObject("id",UUID.class),r.getObject("document_id",UUID.class),r.getString("object_key"),r.getString("object_version"),r.getString("sha256"),r.getLong("size"),r.getInt("width"),r.getInt("height")),id);if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();}
    /** Solo el servicio editorial invoca esta lectura después de comprobar autorización pública. */
    public byte[] bytes(UUID id){var v=read(id);byte[] bytes=storage.get(v.key(),v.objectVersion(),v.size()).bytes();if(bytes.length!=v.size()||!FileInspection.sha256(bytes).equals(v.sha256()))throw new ApiException(503,"OBJECT_INTEGRITY_FAILURE","La variante no pasó la verificación de integridad.");return bytes;}
    static byte[] convert(byte[] bytes,String type){
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx384m","-XX:+ExitOnOutOfMemoryError","-Djava.awt.headless=true"));
        boolean boot=ImageVariantMain.class.getResource("ImageVariantMain.class").toString().contains("BOOT-INF/classes");
        if(boot)command.add("-Dloader.main="+ImageVariantMain.class.getName());
        command.addAll(List.of("-cp",System.getProperty("java.class.path"),boot?"org.springframework.boot.loader.launch.PropertiesLauncher":ImageVariantMain.class.getName(),type));
        var builder=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);builder.environment().clear();Process child=null;
        try {
            child=builder.start();final Process p=child;
            try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
                var write=executor.submit(()->{try(var out=p.getOutputStream()){out.write(bytes);}return true;});
                var read=executor.submit(()->p.getInputStream().readNBytes(5*1024*1024+1));
                if(!p.waitFor(20,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("deadline");}
                byte[] result=read.get(1,TimeUnit.SECONDS);write.get(1,TimeUnit.SECONDS);
                if(p.exitValue()!=0||result.length==0||result.length>5*1024*1024)throw new IOException("invalid output");return result;
            }
        }catch(Exception e){throw new ApiException(503,"VARIANT_UNAVAILABLE","Conversión pendiente por límites de contenido, memoria o tiempo.");}
        finally{if(child!=null&&child.isAlive())child.destroyForcibly();}
    }
}
