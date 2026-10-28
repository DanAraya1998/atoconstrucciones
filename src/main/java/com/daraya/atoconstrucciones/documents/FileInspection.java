package com.daraya.atoconstrucciones.documents;
import com.daraya.atoconstrucciones.shared.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.*;
import javax.imageio.ImageIO;
import javax.xml.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class FileInspection {
    private FileInspection(){}
    public static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    public static long limit(String type){return switch(type){case "application/pdf"->20L*1024*1024;case "image/png","image/jpeg"->10L*1024*1024;case "application/xml"->5L*1024*1024;default->throw new ApiException(422,"UNSUPPORTED_FILE_TYPE","Tipo de archivo no permitido.");};}
    public static void inspect(byte[] bytes,String type){
        if(bytes.length==0||bytes.length>limit(type))Versions.invalid("Tamaño de archivo inválido.");
        try{
            switch(type){
                case "application/pdf"->{
                    if(!new String(bytes,0,Math.min(5,bytes.length),StandardCharsets.US_ASCII).equals("%PDF-"))throw new IOException("PDF header");
                    try(var pdf=Loader.loadPDF(bytes)){
                        if(pdf.isEncrypted()||pdf.getNumberOfPages()<1||pdf.getNumberOfPages()>200)throw new IOException("PDF constraints");
                        // Evitar acciones activas y archivos anidados; no se renderiza el original en el navegador.
                        var seen=Collections.newSetFromMap(new IdentityHashMap<COSBase,Boolean>());
                        inspectCos(pdf.getDocumentCatalog().getCOSObject(),seen,0);
                    }
                }
                case "image/png","image/jpeg"->{
                    try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){
                        var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw new IOException("Image format");
                        var reader=readers.next();try{
                            String format=reader.getFormatName();if(!format.equalsIgnoreCase(type.equals("image/png")?"png":"jpeg"))throw new IOException("Image mismatch");
                            reader.setInput(input);long width=reader.getWidth(0),height=reader.getHeight(0);if(width<1||height<1||width*height>40000000)throw new IOException("Image dimensions");
                            if(reader.read(0)==null)throw new IOException("Image incomplete");
                        }finally{reader.dispose();}
                    }
                }
                case "application/xml"->{
                    var factory=XMLInputFactory.newFactory();factory.setProperty(XMLInputFactory.SUPPORT_DTD,false);factory.setProperty("javax.xml.stream.isSupportingExternalEntities",false);
                    factory.setXMLResolver((publicId,systemId,base,namespace)->{throw new XMLStreamException("External access prohibited");});
                    var reader=factory.createXMLStreamReader(new ByteArrayInputStream(bytes));int depth=0,count=0;
                    try{while(reader.hasNext()){int event=reader.next();if(event==XMLStreamConstants.DTD||event==XMLStreamConstants.ENTITY_REFERENCE)throw new XMLStreamException("DTD prohibited");if(event==XMLStreamConstants.START_ELEMENT){if(++depth>64||++count>100000)throw new XMLStreamException("XML limits");}else if(event==XMLStreamConstants.END_ELEMENT)depth--;}}finally{reader.close();}
                }
                default->throw new IOException("Unsupported");
            }
        }catch(Exception e){throw new ApiException(422,"INVALID_FILE_CONTENT","El contenido no cumple la política del tipo de archivo.");}
    }
    private static void inspectCos(COSBase node,Set<COSBase> seen,int depth)throws IOException{
        if(node==null||!seen.add(node))return;if(depth>100||seen.size()>100000)throw new IOException("PDF structure limits");
        if(node instanceof COSObject o)inspectCos(o.getObject(),seen,depth+1);
        if(node instanceof COSDictionary d){
            for(COSName key:d.keySet()){
                if(Set.of("JS","JavaScript","AA","OpenAction","EmbeddedFiles","EF","XFA","RichMedia","Launch").contains(key.getName()))throw new IOException("Active PDF");
                inspectCos(d.getItem(key),seen,depth+1);
            }
            if(d.getCOSName(COSName.S)!=null&&Set.of("JavaScript","Launch","SubmitForm","ImportData","GoToR","URI").contains(d.getCOSName(COSName.S).getName()))throw new IOException("External PDF action");
        }else if(node instanceof COSArray a)for(COSBase item:a)inspectCos(item,seen,depth+1);
    }
}
