package com.example.asd.document.diagram;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Renders a simple layered architecture flowchart as PNG so it displays correctly in Word and PDF
 * (Mermaid text alone does not render in those formats).
 */
public final class ArchitectureFlowchartRenderer {

  private static final int W = 920;
  private static final int H = 420;
  private static final Color BG = new Color(248, 250, 252);
  private static final Color BOX = new Color(31, 78, 121);
  private static final Color BOX_FILL = new Color(230, 240, 250);
  private static final Color ARROW = new Color(60, 60, 60);

  private ArchitectureFlowchartRenderer() {}

  public static byte[] renderPng() throws IOException {
    var img = new java.awt.image.BufferedImage(W, H, java.awt.image.BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setColor(BG);
    g.fillRect(0, 0, W, H);

    Font title = new Font("SansSerif", Font.BOLD, 18);
    Font label = new Font("SansSerif", Font.BOLD, 14);
    g.setFont(title);
    g.setColor(new Color(30, 41, 59));
    g.drawString("Inferred request / data flow (illustrative)", 32, 36);

    String[] nodes = {"HTTP / API clients", "Controllers / UI boundary", "Services / domain", "Data access / SQL", "External systems"};
    int n = nodes.length;
    int gap = 24;
    int boxW = (W - 64 - (n - 1) * gap) / n;
    int boxH = 88;
    int y = 110;
    int x0 = 32;

    for (int i = 0; i < n; i++) {
      int x = x0 + i * (boxW + gap);
      drawBox(g, x, y, boxW, boxH, nodes[i], label);
      if (i < n - 1) {
        int cx = x + boxW + 4;
        int nx = x + boxW + gap - 4;
        int cy = y + boxH / 2;
        drawArrow(g, cx, cy, nx);
      }
    }

    g.setFont(new Font("SansSerif", Font.PLAIN, 11));
    g.setColor(new Color(71, 85, 105));
    g.drawString("Polyglot repos: map concrete modules to these layers using inventory + SQL scan.", 32, H - 48);
    g.drawString("Not a runtime trace — documentation aid for architecture reviews.", 32, H - 28);

    g.dispose();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, "PNG", baos);
    return baos.toByteArray();
  }

  private static void drawBox(Graphics2D g, int x, int y, int w, int h, String text, Font font) {
    var shape = new RoundRectangle2D.Float(x, y, w, h, 16, 16);
    g.setColor(BOX_FILL);
    g.fill(shape);
    g.setColor(BOX);
    g.setStroke(new BasicStroke(2f));
    g.draw(shape);
    g.setFont(font);
    g.setColor(BOX);
    drawCenteredWrapped(g, text, x, y, w, h);
  }

  private static void drawCenteredWrapped(Graphics2D g, String text, int x, int y, int w, int h) {
    var fm = g.getFontMetrics();
    String[] words = text.split(" ");
    StringBuilder line = new StringBuilder();
    int lineY = y + h / 2 - fm.getHeight() / 2;
    for (String word : words) {
      String trial = line.isEmpty() ? word : line + " " + word;
      if (fm.stringWidth(trial) > w - 16) {
        int tw = fm.stringWidth(line.toString());
        g.drawString(line.toString(), x + (w - tw) / 2, lineY);
        line = new StringBuilder(word);
        lineY += fm.getHeight();
      } else {
        line = new StringBuilder(trial);
      }
    }
    if (!line.isEmpty()) {
      int tw = fm.stringWidth(line.toString());
      g.drawString(line.toString(), x + (w - tw) / 2, lineY);
    }
  }

  private static void drawArrow(Graphics2D g, int x1, int y, int x2) {
    g.setColor(ARROW);
    g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    int tip = x2;
    int shaftEnd = tip - 10;
    g.drawLine(x1, y, shaftEnd, y);
    g.fillPolygon(
        new int[] {tip, tip - 12, tip - 12},
        new int[] {y, y - 6, y + 6},
        3);
  }
}
