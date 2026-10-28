package com.daraya.atoconstrucciones.documents;

/** Entrada del parser con heap/tiempo acotados; no carga Spring ni credenciales. */
public final class FileInspectionMain {
    private FileInspectionMain() {}
    public static void main(String[] args) {
        try {
            if(args.length!=1)System.exit(2);
            byte[] bytes=System.in.readNBytes(Math.toIntExact(FileInspection.limit(args[0])+1));
            FileInspection.inspect(bytes,args[0]);
        } catch(Throwable invalid) { System.exit(2); }
    }
}
