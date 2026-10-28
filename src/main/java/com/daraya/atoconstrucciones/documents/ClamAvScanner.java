package com.daraya.atoconstrucciones.documents;
import com.daraya.atoconstrucciones.shared.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class ClamAvScanner implements ScannerPort {
    private final String host;private final int port;private final boolean requireFresh;private final int maxAgeHours;
    public ClamAvScanner(@Value("${ato.scanner.host:127.0.0.1}")String host,@Value("${ato.scanner.port:3310}")int port,
        @Value("${ato.scanner.require-fresh:true}")boolean requireFresh,@Value("${ato.scanner.max-age-hours:72}")int maxAgeHours){this.host=host;this.port=port;this.requireFresh=requireFresh;this.maxAgeHours=maxAgeHours;}
    private Socket connect()throws IOException{Socket socket=new Socket();socket.connect(new InetSocketAddress(host,port),2000);socket.setSoTimeout(15000);return socket;}
    private String response(InputStream in)throws IOException{var bytes=new ByteArrayOutputStream();for(int b;(b=in.read())!=-1&&b!=0&&b!='\n';){if(bytes.size()>=4096)throw new IOException("Invalid scanner response");bytes.write(b);}return bytes.toString(StandardCharsets.UTF_8);}
    private void freshness()throws IOException{
        if(!requireFresh)return;
        try(var socket=connect()){
            socket.getOutputStream().write("zVERSION\0".getBytes(StandardCharsets.US_ASCII));socket.getOutputStream().flush();
            String version=response(socket.getInputStream());String[] parts=version.split("/",3);if(parts.length!=3)throw new IOException("Signature date unavailable");
            var date=LocalDateTime.parse(parts[2].trim().replaceAll(" +"," "),DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy",Locale.ENGLISH)).toInstant(ZoneOffset.UTC);
            if(date.isBefore(Instant.now().minus(Duration.ofHours(maxAgeHours)))||date.isAfter(Instant.now().plusSeconds(300)))throw new IOException("Stale signatures");
        }catch(RuntimeException e){throw new IOException("Signature date invalid",e);}
    }
    public Result scan(byte[] bytes){
        try{freshness();try(var socket=connect()){
            var out=new DataOutputStream(socket.getOutputStream());out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            for(int offset=0;offset<bytes.length;){int count=Math.min(8192,bytes.length-offset);out.writeInt(count);out.write(bytes,offset,count);offset+=count;}
            out.writeInt(0);out.flush();String result=response(socket.getInputStream());
            if(result.endsWith(" FOUND"))return Result.INFECTED;
            if(result.equals("stream: OK"))return Result.CLEAN;
            throw new IOException("Scan incomplete");
        }}catch(IOException e){throw new ApiException(503,"SCANNER_UNAVAILABLE","El escaneo está pendiente; el archivo permanece bloqueado.");}
    }
}
