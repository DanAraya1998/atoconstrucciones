package com.daraya.atoconstrucciones.documents;

import com.daraya.atoconstrucciones.shared.ApiException;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.io.*;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.zip.*;

/** Copia cifrada autenticada. La clave se custodia separadamente, nunca dentro del archivo. */
public final class RecoveryArchive {
    private RecoveryArchive(){}
    public static void encrypt(Path plain,Path encrypted,byte[] key)throws Exception{
        if(key.length!=32)throw new IllegalArgumentException("AES-256 key required");
        if(Files.size(plain)>256L*1024*1024)throw new IOException("Use separate archives up to 256 MiB");
        byte[] nonce=new byte[12];new SecureRandom().nextBytes(nonce);
        var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,nonce));
        try(var out=Files.newOutputStream(encrypted,StandardOpenOption.CREATE_NEW);var input=Files.newInputStream(plain)){
            out.write("ATO4".getBytes(java.nio.charset.StandardCharsets.US_ASCII));out.write(nonce);
            try(var crypt=new CipherOutputStream(out,cipher)){input.transferTo(crypt);}
        }
    }
    public static void decrypt(Path encrypted,Path plain,byte[] key)throws Exception{
        if(key.length!=32)throw new IllegalArgumentException("AES-256 key required");
        // SunJCE GCM decryption buffers until authentication; limitar archivo y memoria del CLI.
        if(Files.size(encrypted)>256L*1024*1024)throw new IOException("Use separate archives up to 256 MiB");
        byte[] content=Files.readAllBytes(encrypted);if(content.length<32||!new String(content,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("ATO4"))throw new IOException("Archive format");
        var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,Arrays.copyOfRange(content,4,16)));
        byte[] authenticated=cipher.doFinal(content,16,content.length-16);
        Files.write(plain,authenticated,StandardOpenOption.CREATE_NEW);
        Files.setPosixFilePermissions(plain,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"));
    }
    /** Exporta solo bytes verificados de referencias fijadas, sin credenciales ni enlaces públicos. */
    public static void exportObject(ObjectStoragePort storage,String key,String version,long size,String sha,Path path)throws IOException{
        byte[] bytes=storage.get(key,version,size).bytes();if(bytes.length!=size||!FileInspection.sha256(bytes).equals(sha))throw new ApiException(503,"OBJECT_INTEGRITY_FAILURE","La copia de recuperación no pasó integridad.");Files.write(path,bytes,StandardOpenOption.CREATE_NEW);
    }
}
