package vn.ticketscenter.event.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

import javax.imageio.ImageIO;

public final class EventImagePlaceholders {
    private EventImagePlaceholders() {}

    public static byte[] png(String category) throws IOException {
        String slug = category == null ? "" : category.toLowerCase(Locale.ROOT);
        boolean music = slug.contains("music") || slug.contains("nhac");
        boolean sport =
                slug.contains("sport") || slug.contains("race") || slug.contains("the-thao");
        String label = music ? "MUSIC" : sport ? "SPORT & COMMUNITY" : "LIVE EXPERIENCES";
        Color start =
                music
                        ? new Color(75, 40, 125)
                        : sport ? new Color(14, 96, 104) : new Color(38, 59, 100);
        BufferedImage image = new BufferedImage(1200, 675, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setPaint(new GradientPaint(0, 0, start, 1200, 675, new Color(16, 20, 36)));
            graphics.fillRect(0, 0, 1200, 675);
            graphics.setColor(new Color(255, 255, 255, 24));
            for (int i = 0; i < 5; i++) graphics.fillOval(680 + i * 45, 90 + i * 35, 300, 300);
            graphics.setColor(new Color(163, 240, 214));
            graphics.setStroke(new BasicStroke(14, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (music) {
                graphics.drawLine(835, 220, 835, 415);
                graphics.drawLine(835, 220, 980, 195);
                graphics.drawLine(980, 195, 980, 390);
                graphics.fillOval(775, 390, 65, 40);
                graphics.fillOval(920, 365, 65, 40);
            } else if (sport) {
                graphics.drawOval(790, 210, 220, 220);
                graphics.drawLine(900, 210, 900, 430);
                graphics.drawLine(790, 320, 1010, 320);
            } else {
                graphics.drawRoundRect(785, 220, 245, 170, 25, 25);
                graphics.drawLine(950, 225, 950, 385);
            }
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
            graphics.drawString("TICKETCENTER", 82, 106);
            graphics.setColor(Color.WHITE);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 42));
            graphics.drawString(label, 82, 365);
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 23));
            graphics.drawString("Event cover coming soon", 85, 416);
            graphics.setColor(new Color(199, 207, 224));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            graphics.drawString("Category artwork", 85, 592);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output))
            throw new IOException("PNG encoder is unavailable");
        return output.toByteArray();
    }
}
