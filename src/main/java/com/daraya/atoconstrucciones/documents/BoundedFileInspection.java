package com.daraya.atoconstrucciones.documents;

import com.daraya.atoconstrucciones.shared.ApiException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.io.IOException;

/** Separa la memoria del parser del servidor y no entrega variables de entorno al proceso. */
public final class BoundedFileInspection {
    private BoundedFileInspection() {}
    public static void inspect(byte[] bytes,String mediaType) {
        String javaBinary=Path.of(System.getProperty("java.home"),"bin","java").toString();
        String classpath=System.getProperty("java.class.path");
        boolean boot=FileInspectionMain.class.getResource("FileInspectionMain.class").toString().contains("BOOT-INF/classes");
        var command=new ArrayList<>(List.of(javaBinary,"-Xmx192m","-XX:+ExitOnOutOfMemoryError","-Djava.awt.headless=true"));
        if(boot)command.add("-Dloader.main="+FileInspectionMain.class.getName());
        command.addAll(List.of("-cp",classpath,boot?"org.springframework.boot.loader.launch.PropertiesLauncher":FileInspectionMain.class.getName(),mediaType));
        var builder=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear();
        Process process=null;
        try {
            process=builder.start();
            // Deadline empieza antes de transmitir: un parser que no lee no bloquea el worker.
            final Process child=process;
            try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()){
                var writer=executor.submit(()->{try(var out=child.getOutputStream()){out.write(bytes);}catch(IOException e){throw new java.io.UncheckedIOException(e);}});
                if(!process.waitFor(15,TimeUnit.SECONDS)){process.destroyForcibly();writer.cancel(true);throw new ApiException(503,"PARSER_UNAVAILABLE","Verificación pendiente por límite de tiempo.");}
                if(process.exitValue()==2)throw new ApiException(422,"INVALID_FILE_CONTENT","El contenido no cumple la política documental.");
                if(process.exitValue()!=0)throw new ApiException(503,"PARSER_UNAVAILABLE","La verificación de contenido está pendiente.");
                writer.get(1,TimeUnit.SECONDS);
            }
        } catch(ApiException e){throw e;}
        catch(Exception e){throw new ApiException(503,"PARSER_UNAVAILABLE","La verificación de contenido está pendiente.");}
        finally {if(process!=null&&process.isAlive())process.destroyForcibly();}
    }
}
