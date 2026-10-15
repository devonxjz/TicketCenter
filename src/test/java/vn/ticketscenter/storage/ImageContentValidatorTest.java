package vn.ticketscenter.storage;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ImageContentValidatorTest {
    private byte[] image(String format) throws IOException {var out=new ByteArrayOutputStream();assertTrue(ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),format,out));return out.toByteArray();}
    @Test void realPngJpegAndWebpHeadersAreSupported() throws Exception {
        for(String format:List.of("png","jpeg")){byte[] bytes=image(format);assertArrayEquals(bytes,ImageContentValidator.readAndValidate(new ByteArrayInputStream(bytes)));}
        byte[] webp=Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        assertArrayEquals(webp,ImageContentValidator.readAndValidate(new ByteArrayInputStream(webp)));
    }
    @Test void fakeExtensionEmptyTruncatedAndUnsupportedImagesAreRejected() throws Exception {
        for(byte[] bytes:List.of("#!/bin/sh".getBytes(),new byte[0],new byte[]{(byte)0x89,80,78,71},image("gif")))
            assertThrows(Exception.class,()->ImageContentValidator.readAndValidate(new ByteArrayInputStream(bytes)));
    }
    @Test void byteAndPixelLimitsAreEnforced() throws Exception {
        byte[] bytes=image("png");assertThrows(IllegalArgumentException.class,()->ImageContentValidator.readAndValidate(new ByteArrayInputStream(bytes),bytes.length-1,12000000));
        assertThrows(IllegalArgumentException.class,()->ImageContentValidator.readAndValidate(new ByteArrayInputStream(bytes),5242880,1));
        assertThrows(IllegalArgumentException.class,()->ImageContentValidator.readAndValidate(new ByteArrayInputStream(new byte[5242881])));
    }
    @Test void webpRejectsTrailingPayloadBadFramingAndHugeDimensions() {
        byte[] valid=Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA");
        byte[] trailing=Arrays.copyOf(valid,valid.length+1);assertThrows(IllegalArgumentException.class,()->ImageContentValidator.readAndValidate(new ByteArrayInputStream(trailing)));
        byte[] bad=valid.clone();bad[23]=0;assertThrows(IllegalArgumentException.class,()->ImageContentValidator.readAndValidate(new ByteArrayInputStream(bad)));
        byte[] huge=valid.clone();huge[26]=(byte)255;huge[27]=63;huge[28]=(byte)255;huge[29]=63;
        assertThrows(IllegalArgumentException.class,()->ImageContentValidator.readAndValidate(new ByteArrayInputStream(huge)));
    }
}
