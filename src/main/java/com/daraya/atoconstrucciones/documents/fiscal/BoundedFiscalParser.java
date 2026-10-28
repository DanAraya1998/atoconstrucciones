package com.daraya.atoconstrucciones.documents.fiscal;
import com.daraya.atoconstrucciones.shared.ApiException;
import tools.jackson.databind.json.JsonMapper;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class BoundedFiscalParser {
    private BoundedFiscalParser() {}
    public static FiscalParser.Extracted parse(byte[] bytes) {
        boolean boot=FiscalParserMain.class.getResource("FiscalParserMain.class").toString().contains("BOOT-INF/classes");
        var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-Xmx192m","-XX:+ExitOnOutOfMemoryError"));
        if(boot)command.add("-Dloader.main="+FiscalParserMain.class.getName());
        command.addAll(List.of("-cp",System.getProperty("java.class.path"),boot?"org.springframework.boot.loader.launch.PropertiesLauncher":FiscalParserMain.class.getName()));
        var builder=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD);builder.environment().clear();Process process=null;
        try {
            process=builder.start();final Process child=process;
            try(var executor=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
                var writer=executor.submit(()->{try(var out=child.getOutputStream()){out.write(bytes);}return true;});
                var reader=executor.submit(()->child.getInputStream().readNBytes(6*1024*1024+1));
                if(!child.waitFor(15,TimeUnit.SECONDS)){child.destroyForcibly();writer.cancel(true);reader.cancel(true);throw new ApiException(503,"FISCAL_PARSER_UNAVAILABLE","La extracción fiscal está pendiente por límite de tiempo.");}
                byte[] result=reader.get(1,TimeUnit.SECONDS);if(result.length>6*1024*1024)throw new ApiException(422,"FISCAL_OUTPUT_LIMIT","El detalle fiscal excede el límite de extracción.");
                var json=JsonMapper.builder().build();
                if(child.exitValue()==2) {var error=json.readTree(result);throw new ApiException(422,error.get("error").asText(),error.get("detail").asText());}
                if(child.exitValue()!=0)throw new IllegalStateException("Fiscal parser exit");writer.get(1,TimeUnit.SECONDS);return json.readValue(result,FiscalParser.Extracted.class);
            }
        } catch(ApiException e) {throw e;}catch(Exception e) {throw new ApiException(503,"FISCAL_PARSER_UNAVAILABLE","La extracción fiscal está pendiente.");}
        finally {if(process!=null&&process.isAlive())process.destroyForcibly();}
    }
}
