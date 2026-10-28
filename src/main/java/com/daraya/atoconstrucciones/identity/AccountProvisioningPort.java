package com.daraya.atoconstrucciones.identity;

/** Solo alta nueva y activación de cuentas creadas por ATO; nunca contraseñas ni impersonación. */
public interface AccountProvisioningPort {
    record Provisioned(String issuer,String subject) {}
    boolean configured();
    Provisioned create(String reference,String fingerprint,String name,String email);
    void sendActivation(String issuer,String subject);
}
