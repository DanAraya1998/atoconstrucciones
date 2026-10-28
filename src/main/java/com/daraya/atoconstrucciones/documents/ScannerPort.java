package com.daraya.atoconstrucciones.documents;
public interface ScannerPort {
    enum Result { CLEAN, INFECTED }
    Result scan(byte[] bytes);
}
