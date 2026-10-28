package com.daraya.atoconstrucciones.shared;
public final class Versions {
    private Versions() {}
    public static void check(String etag, long version) {
        if (etag == null) throw new ApiException(428,"PRECONDITION_REQUIRED","Incluya If-Match.");
        if (!etag.matches("\"[1-9][0-9]*\"")) throw new ApiException(400,"MALFORMED_REQUEST","ETag inválido.");
        if (!etag.equals("\""+version+"\"")) throw new ApiException(412,"VERSION_MISMATCH","El recurso cambió. Actualice antes de guardar.");
    }
    public static void invalid(String message) { throw new ApiException(422,"VALIDATION_FAILED",message); }
    public static void conflict(String message) { throw new ApiException(409,"INVALID_STATE",message); }
}
