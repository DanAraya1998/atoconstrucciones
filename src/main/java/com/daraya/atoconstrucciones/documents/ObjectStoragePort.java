package com.daraya.atoconstrucciones.documents;
public interface ObjectStoragePort {
    record Stored(byte[] bytes,String version) {}
    String put(String key,byte[] bytes,String mediaType);
    default String put(String key,byte[] bytes,String mediaType,String filename) { return put(key,bytes,mediaType); }
    Stored get(String key,String version,long limit);
    void delete(String key,String version);
    boolean available();
}
