package com.daraya.atoconstrucciones.documents;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;

/** Reencode pixels only: no EXIF, GPS, comments, source names or profiles. No credentials. */
public final class ImageVariantMain {
    private ImageVariantMain() {}
    public static void main(String[] args) {
        try {
            ImageIO.setUseCache(false);
            byte[] bytes=System.in.readNBytes(10*1024*1024+1);
            FileInspection.inspect(bytes,args[0]);
            BufferedImage source=ImageIO.read(new java.io.ByteArrayInputStream(bytes));
            double ratio=Math.min(1d,1600d/Math.max(source.getWidth(),source.getHeight()));
            int width=Math.max(1,(int)(source.getWidth()*ratio)),height=Math.max(1,(int)(source.getHeight()*ratio));
            var clean=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
            var g=clean.createGraphics();g.setColor(Color.WHITE);g.fillRect(0,0,width,height);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(source,0,0,width,height,null);g.dispose();
            var out=new java.io.ByteArrayOutputStream();ImageIO.write(clean,"jpg",out);
            if(out.size()>5*1024*1024)System.exit(2);
            System.out.write(out.toByteArray());
        } catch(Throwable invalid){System.exit(2);}
    }
}
