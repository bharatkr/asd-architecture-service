package com.example.asd.document.diagram;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Renders an inferred ER-style diagram (tables + REFERENCES edges) as PNG for Word/PDF. */
public final class ErDiagramRenderer {

  private static final int MAX_TABLES_DRAWN = 36;
  private static final Color BG = new Color(252, 252, 255);
  private static final Color BOX_BORDER = new Color(30, 64, 175);
  private static final Color BOX_FILL = new Color(238, 242, 255);
  private static final Color EDGE = new Color(71, 85, 105);
  private static final int PAD = 28;

  private ErDiagramRenderer() {}

  /**
   * @return PNG bytes, or {@code null} when there is nothing to draw (no tables inferred).
   */
  public static byte[] renderPngOrNull(SqlErModel model) throws IOException {
    if (model == null || !model.hasTables()) {
      return null;
    }
    List<String> tables = model.tables().size() > MAX_TABLES_DRAWN
        ? model.tables().subList(0, MAX_TABLES_DRAWN)
        : model.tables();

    int n = tables.size();
    int cols = (int) Math.ceil(Math.sqrt(n));
    int rows = (int) Math.ceil(n / (double) cols);

    Font tableFont = new Font("SansSerif", Font.BOLD, 13);
    Font titleFont = new Font("SansSerif", Font.BOLD, 17);
    Font noteFont = new Font("SansSerif", Font.PLAIN, 11);

    int cellW = 200;
    int cellH = 96;
    int boxPad = 10;
    int w = PAD * 2 + cols * cellW;
    int h = PAD * 2 + rows * cellH + 56;

    var img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setColor(BG);
    g.fillRect(0, 0, w, h);

    g.setFont(titleFont);
    g.setColor(new Color(15, 23, 42));
    g.drawString("Inferred ER diagram (from SQL DDL heuristics)", PAD, PAD + 8);

    Map<String, TableGeom> geom = new HashMap<>();
    for (int i = 0; i < n; i++) {
      int col = i % cols;
      int row = i / cols;
      int x = PAD + col * cellW + boxPad;
      int y = PAD + 44 + row * cellH + boxPad;
      String name = tables.get(i);
      drawTableBox(g, tableFont, x, y, cellW - 2 * boxPad, cellH - 2 * boxPad, name);
      geom.put(SqlErModel.canonical(name), new TableGeom(x, y, cellW - 2 * boxPad, cellH - 2 * boxPad, name));
    }

    g.setColor(EDGE);
    g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    for (SqlErModel.Relationship r : model.relationships()) {
      TableGeom a = geom.get(SqlErModel.canonical(r.fromTable()));
      TableGeom b = geom.get(SqlErModel.canonical(r.toTable()));
      if (a == null || b == null) {
        continue;
      }
      drawEdge(g, a, b);
    }

    g.setFont(noteFont);
    g.setColor(new Color(100, 116, 139));
    String note = "Edges: REFERENCES / ALTER TABLE … FOREIGN KEY … REFERENCES (heuristic).";
    if (model.tables().size() > MAX_TABLES_DRAWN) {
      note += " Showing first " + MAX_TABLES_DRAWN + " of " + model.tables().size() + " tables.";
    }
    g.drawString(note, PAD, h - 14);

    g.dispose();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(img, "PNG", baos);
    return baos.toByteArray();
  }

  private static void drawTableBox(Graphics2D g, Font font, int x, int y, int bw, int bh, String text) {
    var shape = new RoundRectangle2D.Float(x, y, bw, bh, 12, 12);
    g.setColor(BOX_FILL);
    g.fill(shape);
    g.setColor(BOX_BORDER);
    g.setStroke(new BasicStroke(2f));
    g.draw(shape);
    g.setFont(font);
    g.setColor(BOX_BORDER);
    drawCenteredClipped(g, text, x, y, bw, bh);
  }

  private static void drawCenteredClipped(Graphics2D g, String text, int x, int y, int bw, int bh) {
    FontMetrics fm = g.getFontMetrics();
    String display = text.length() > 28 ? text.substring(0, 25) + "…" : text;
    int tw = fm.stringWidth(display);
    int th = fm.getAscent();
    int cx = x + (bw - tw) / 2;
    int cy = y + (bh + th) / 2 - 4;
    g.drawString(display, Math.max(x + 6, cx), cy);
  }

  private static void drawEdge(Graphics2D g, TableGeom a, TableGeom b) {
    double x1 = a.cx();
    double y1 = a.cy();
    double x2 = b.cx();
    double y2 = b.cy();
    double dx = x2 - x1;
    double dy = y2 - y1;
    double len = Math.hypot(dx, dy);
    if (len < 4) {
      return;
    }
    dx /= len;
    dy /= len;
    double insetA = Math.min(a.w, a.h) * 0.38;
    double insetB = Math.min(b.w, b.h) * 0.38;
    double sx = x1 + dx * insetA;
    double sy = y1 + dy * insetA;
    double ex = x2 - dx * insetB;
    double ey = y2 - dy * insetB;
    g.draw(new Line2D.Double(sx, sy, ex, ey));

    double ang = Math.atan2(ey - sy, ex - sx);
    int tip = 11;
    double bx = ex - Math.cos(ang) * tip;
    double by = ey - Math.sin(ang) * tip;
    double perp = ang + Math.PI / 2;
    double aw = 6;
    g.fillPolygon(
        new int[] {(int) Math.round(ex), (int) Math.round(bx + Math.cos(perp) * aw), (int) Math.round(bx - Math.cos(perp) * aw)},
        new int[] {(int) Math.round(ey), (int) Math.round(by + Math.sin(perp) * aw), (int) Math.round(by - Math.sin(perp) * aw)},
        3);
  }

  private record TableGeom(int x, int y, int w, int h, String name) {
    double cx() {
      return x + w / 2.0;
    }

    double cy() {
      return y + h / 2.0;
    }
  }
}
