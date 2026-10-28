package com.daraya.atoconstrucciones;
import com.daraya.atoconstrucciones.documents.fiscal.*;
import com.daraya.atoconstrucciones.shared.ApiException;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;
class FiscalParserTests {
    static String fixture(String type) {try(var in=FiscalParserTests.class.getResourceAsStream("/fiscal/"+type+".xml")){return new String(in.readAllBytes(),StandardCharsets.UTF_8);}catch(Exception e){throw new IllegalStateException(e);}}
    @Test void validatesOfficialSchemasForEverySupportedTypeAndPreservesPrecision() {
        for(String type:java.util.List.of("FacturaElectronica","TiqueteElectronico","FacturaElectronicaCompra","NotaCreditoElectronica","NotaDebitoElectronica")) {
            var e=FiscalParser.parse(fixture(type).getBytes(StandardCharsets.UTF_8));assertThat(e.type()).isEqualTo(type);assertThat(e.total()).isEqualTo("1130000.00000");assertThat(e.lines().getFirst().quantity()).isEqualTo("1.00000");assertThat(e.lines().getFirst().sourceXml()).contains("CodigoTarifaIVA");assertThat(e.diagnostics()).contains("SIGNATURE_NOT_VERIFIED","TAX_STATUS_NOT_VERIFIED");assertThat(e.bundleHash()).hasSize(64);
        }
    }
    @Test void blocksHostileAndInvalidFiscalDocumentsWithoutRemoteParsing() {
        String valid=fixture("FacturaElectronica");
        for(String xml:java.util.List.of("<!DOCTYPE x [<!ENTITY ex SYSTEM 'http://127.0.0.1:9/secret'>]><x>&ex;</x>","<!DOCTYPE x [<!ENTITY a 'aaaa'><!ENTITY b '&a;&a;&a;'>]><x>&b;</x>","<x>".repeat(65)+"</x>".repeat(65),valid.replace("v4.4/facturaElectronica","v4.3/facturaElectronica"),valid.replace("<TotalComprobante>1130000.00000","<TotalComprobante>999.00"),valid.replace("<Clave>","<Unknown>"),valid.replace("<ds:Signature ","<ds:NotSignature ")))assertThatThrownBy(()->FiscalParser.parse(xml.getBytes(StandardCharsets.UTF_8))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->FiscalParser.parse("<ReciboElectronicoPago/>".getBytes())).isInstanceOf(ApiException.class).extracting(e->((ApiException)e).code()).isEqualTo("UNSUPPORTED_FISCAL_TYPE");
    }
    @Test void subprocessUsesBoundedParserAndDoesNotLoadRemoteSchemaHints() {
        String xml=fixture("FacturaElectronica").replace("<FacturaElectronica xmlns=","<FacturaElectronica xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"https://cdn.comprobanteselectronicos.go.cr/xml-schemas/v4.4/facturaElectronica http://127.0.0.1:9/evil.xsd\" xmlns=");
        assertThat(BoundedFiscalParser.parse(xml.getBytes()).total()).isEqualTo("1130000.00000");
    }
}
