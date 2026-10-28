package com.daraya.atoconstrucciones;

import com.daraya.atoconstrucciones.documents.ClamAvScanner;
import com.daraya.atoconstrucciones.documents.ScannerPort;
import com.daraya.atoconstrucciones.shared.ApiException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

/** Antivirus real local; las pruebas de protocolo Drive no utilizan una cuenta Google. */
@EnabledIfEnvironmentVariable(named="ATO_M1_INFRA",matches="1")
class ClamAvInfrastructureTests {
    @Test void realScannerAcceptsCleanBytesDetectsEicarAndFailsClosedWhenUnavailable() {
        var scanner=new ClamAvScanner("127.0.0.1",3310,true,72);
        assertThat(scanner.scan("<evidence>synthetic</evidence>".getBytes(StandardCharsets.UTF_8))).isEqualTo(ScannerPort.Result.CLEAN);
        String signature="X5O!P%@AP[4\\PZX54(P^)7CC)7}$"+"EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        assertThat(scanner.scan(signature.getBytes(StandardCharsets.US_ASCII))).isEqualTo(ScannerPort.Result.INFECTED);
        assertThatThrownBy(()->new ClamAvScanner("127.0.0.1",65534,true,72).scan(new byte[]{1})).isInstanceOf(ApiException.class);
    }
}
