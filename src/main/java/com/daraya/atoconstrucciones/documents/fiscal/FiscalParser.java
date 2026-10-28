package com.daraya.atoconstrucciones.documents.fiscal;

import com.daraya.atoconstrucciones.documents.FileInspection;
import com.daraya.atoconstrucciones.shared.*;
import org.w3c.dom.*;
import org.w3c.dom.ls.*;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** XSD local allowlisted. La firma y la aceptación tributaria se informan como no verificadas. */
public final class FiscalParser {
    public record Identity(String type,String number,String name) {}
    public record Line(String number,String description,String quantity,String unitPrice,String net,String tax,String total,String sourceXml) {}
    public record Extracted(String key,String type,String namespace,String issuedAt,Identity issuer,Identity receiver,String currency,
        String exchangeRate,String net,String tax,String total,String referenceKey,List<Line> lines,Map<String,String> totals,
        List<String> diagnostics,String bundleHash) {}
    private static final String BASE="/fiscal/v4.4/";
    private static final Map<String,Schema> SCHEMAS=new HashMap<>();
    private static final String BUNDLE_HASH;
    static {
        try {
            byte[] manifest=resource("manifest.json");BUNDLE_HASH=FileInspection.sha256(manifest);
            var tree=JsonMapper.builder().build().readTree(manifest);Map<String,byte[]> trusted=new HashMap<>();
            for(var f:tree.get("files")) {
                String name=f.get("file").asText();byte[] bytes=resource(name);
                if(!FileInspection.sha256(bytes).equals(f.get("sha256").asText()))throw new IllegalStateException("Fiscal bundle integrity");trusted.put(name,bytes);
            }
            // El XSD W3C de 2002 incluye DTD. Solo en este recurso confiable se retira la
            // declaración (no usa entidades); el hash se verifica sobre sus bytes originales.
            String signature=new String(trusted.get("xmldsig-core-schema.xsd"),StandardCharsets.UTF_8).replaceFirst("(?s)<!DOCTYPE.*?\\]>","");
            byte[] safeSignature=signature.getBytes(StandardCharsets.UTF_8);
            var ls=(DOMImplementationLS)DocumentBuilderFactory.newInstance().newDocumentBuilder().getDOMImplementation().getFeature("LS","3.0");
            for(var entry:trusted.entrySet()) {
                if(entry.getKey().equals("xmldsig-core-schema.xsd"))continue;
                SchemaFactory factory=SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
                factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
                factory.setResourceResolver((type,namespace,publicId,systemId,base)->{
                    if(!"http://www.w3.org/2000/09/xmldsig#".equals(namespace)||!"../../xmldsig-core-schema.xsd".equals(systemId))throw new IllegalStateException("Unapproved schema dependency");
                    LSInput input=ls.createLSInput();
                    input.setByteStream(new ByteArrayInputStream(safeSignature));input.setSystemId("xmldsig-core-schema.xsd");return input;
                });
                SCHEMAS.put(entry.getKey().replace(".xsd",""),factory.newSchema(new StreamSource(new ByteArrayInputStream(entry.getValue()))));
            }
        } catch(Exception e) {throw new ExceptionInInitializerError(e);}
    }
    private FiscalParser() {}
    private static byte[] resource(String name)throws IOException {try(var in=FiscalParser.class.getResourceAsStream(BASE+name)){if(in==null)throw new IOException("Missing bundle resource");return in.readAllBytes();}}
    public static Extracted parse(byte[] bytes) {
        FileInspection.inspect(bytes,"application/xml");
        try {
            var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);factory.setXIncludeAware(false);factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities",false);factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
            var builder=factory.newDocumentBuilder();builder.setErrorHandler(new DefaultHandler());builder.setEntityResolver((p,s)->{throw new org.xml.sax.SAXException("External access forbidden");});
            var root=builder.parse(new InputSource(new ByteArrayInputStream(bytes))).getDocumentElement();String type=root.getLocalName(),namespace=root.getNamespaceURI();
            if(!SCHEMAS.containsKey(type))throw new ApiException(422,"UNSUPPORTED_FISCAL_TYPE","Tipo fiscal pendiente de soporte; conserve el documento para revisión.");
            String expected="https://cdn.comprobanteselectronicos.go.cr/xml-schemas/v4.4/"+Character.toLowerCase(type.charAt(0))+type.substring(1);
            if(!expected.equals(namespace))throw new ApiException(422,"INVALID_FISCAL_NAMESPACE","Namespace o versión fiscal no admitidos.");
            Validator validator=SCHEMAS.get(type).newValidator();validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD,"");validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");validator.validate(new DOMSource(root));
            Element summary=child(root,"ResumenFactura"),currency=child(summary,"CodigoTipoMoneda");
            Map<String,String> totals=new LinkedHashMap<>();for(Element c:children(summary))if(c.getLocalName().startsWith("Total")&&!c.hasChildNodes()||c.getLocalName().startsWith("Total")&&children(c).isEmpty())totals.put(c.getLocalName(),c.getTextContent());
            BigDecimal sales=decimal(summary,"TotalVentaNeta"),tax=decimal(summary,"TotalImpuesto").subtract(decimal(summary,"TotalIVADevuelto")),net=sales.add(decimal(summary,"TotalOtrosCargos")),total=decimal(summary,"TotalComprobante");
            requireEqual(net.add(tax),total);
            requireEqual(decimal(summary,"TotalVenta").subtract(decimal(summary,"TotalDescuentos")),sales);
            if(net.signum()<0||tax.signum()<0||total.signum()<=0)Versions.invalid("El comprobante requiere revisión de importes.");
            List<Line> lines=new ArrayList<>();BigDecimal lineNet=BigDecimal.ZERO,lineTax=BigDecimal.ZERO;
            Element detail=child(root,"DetalleServicio");if(detail!=null)for(Element line:children(detail)) {
                if(!line.getLocalName().equals("LineaDetalle"))continue;BigDecimal ln=decimal(line,"SubTotal"),lt=decimal(line,"ImpuestoNeto"),gross=decimal(line,"MontoTotalLinea");requireEqual(ln.add(lt),gross);
                lineNet=lineNet.add(ln);lineTax=lineTax.add(lt);
                lines.add(new Line(text(line,"NumeroLinea"),text(line,"Detalle"),text(line,"Cantidad"),text(line,"PrecioUnitario"),ln.toPlainString(),lt.toPlainString(),gross.toPlainString(),xml(line)));
            }
            if(!lines.isEmpty()) {requireEqual(lineNet,sales);requireEqual(lineTax,decimal(summary,"TotalImpuesto"));}
            List<String> diagnostics=new ArrayList<>(List.of("STRUCTURE_VALID","SIGNATURE_NOT_VERIFIED","TAX_STATUS_NOT_VERIFIED","RECEIVER_REVIEW_REQUIRED"));
            String reference=null;var references=children(root).stream().filter(e->e.getLocalName().equals("InformacionReferencia")).toList();
            if(type.startsWith("Nota")) {
                if(references.size()!=1||!(text(references.getFirst(),"Numero").matches("[0-9]{50}")))throw new ApiException(422,"FISCAL_REFERENCE_REVIEW_REQUIRED","M2 requiere una referencia única al comprobante original.");
                reference=text(references.getFirst(),"Numero");
            }
            OffsetDateTime.parse(text(root,"FechaEmision"));
            return new Extracted(text(root,"Clave"),type,namespace,text(root,"FechaEmision"),identity(child(root,"Emisor")),identity(child(root,"Receptor")),text(currency,"CodigoMoneda"),text(currency,"TipoCambio"),net.toPlainString(),tax.toPlainString(),total.toPlainString(),reference,lines,totals,diagnostics,BUNDLE_HASH);
        } catch(ApiException e) {throw e;} catch(Exception e) {throw new ApiException(422,"INVALID_FISCAL_STRUCTURE","El XML no cumple el XSD local o sus importes; revise el comprobante.");}
    }
    private static Identity identity(Element e) {if(e==null)return null;var id=child(e,"Identificacion");return new Identity(text(id,"Tipo"),text(id,"Numero"),text(e,"Nombre"));}
    private static List<Element> children(Element e) {List<Element> result=new ArrayList<>();if(e!=null)for(Node n=e.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element element)result.add(element);return result;}
    private static Element child(Element e,String name) {return children(e).stream().filter(n->n.getLocalName().equals(name)&&Objects.equals(n.getNamespaceURI(),e.getNamespaceURI())).findFirst().orElse(null);}
    private static String text(Element e,String name) {var c=child(e,name);return c==null?"":c.getTextContent();}
    private static BigDecimal decimal(Element e,String name) {String value=text(e,name);return value.isEmpty()?BigDecimal.ZERO:new BigDecimal(value);}
    private static void requireEqual(BigDecimal a,BigDecimal b) {if(a.subtract(b).abs().compareTo(new BigDecimal("0.00001"))>0)throw new ApiException(422,"FISCAL_TOTAL_MISMATCH","Las sumas del XML requieren revisión; no se creó un gasto.");}
    private static String xml(Element e)throws Exception {var f=javax.xml.transform.TransformerFactory.newInstance();f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET,"");var t=f.newTransformer();t.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION,"yes");var out=new StringWriter();t.transform(new DOMSource(e),new javax.xml.transform.stream.StreamResult(out));return out.toString();}
}
