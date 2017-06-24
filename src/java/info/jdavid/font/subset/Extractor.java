package info.jdavid.font.subset;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.typography.font.sfntly.Font;
import com.google.typography.font.sfntly.FontFactory;
import com.google.typography.font.sfntly.Tag;
import com.google.typography.font.sfntly.table.core.CMap;
import com.google.typography.font.sfntly.table.core.CMapTable;
import com.google.typography.font.sfntly.table.truetype.Glyph;
import com.google.typography.font.sfntly.table.truetype.GlyphTable;
import com.google.typography.font.sfntly.table.truetype.LocaTable;
import com.google.typography.font.tools.conversion.woff.WoffWriter;
import com.google.typography.font.tools.sfnttool.GlyphCoverage;
import com.google.typography.font.tools.subsetter.HintStripper;
import com.google.typography.font.tools.subsetter.RenumberingSubsetter;
import com.google.typography.font.tools.subsetter.Subsetter;


public class Extractor {

  private final FontFactory factory = FontFactory.getInstance();
  private final Font font;

  private static Set<Integer> GLYPH_REMOVABLE_TABLES = new HashSet<>(Arrays.asList(
    Tag.GDEF, Tag.GPOS, Tag.GSUB, Tag.kern, Tag.hdmx, Tag.vmtx, Tag.VDMX, Tag.LTSH, Tag.DSIG,
    Tag.intValue(new byte[] { 'm', 'o', 'r', 't' }),
    Tag.intValue(new byte[] { 'm', 'o', 'r', 'x' })
  ));

  private static Set<Integer> HINT_REMOVABLE_TABLES = new HashSet<>(Arrays.asList(
    Tag.fpgm, Tag.prep, Tag.cvt
  ));

  public Extractor(final byte[] bytes) throws IOException {
    font = factory.loadFonts(bytes)[0];
  }

  private Font strip(final String str) throws IOException {
    final List<CMapTable.CMapId> cmapIds = new ArrayList<>();
    cmapIds.add(CMapTable.CMapId.WINDOWS_BMP);
    final Font subset;
    if (str != null) {
      final Subsetter glyphSubsetter = new RenumberingSubsetter(font, factory);
      glyphSubsetter.setCMaps(cmapIds, 1);
      glyphSubsetter.setGlyphs(GlyphCoverage.getGlyphCoverage(font, str));
      glyphSubsetter.setRemoveTables(GLYPH_REMOVABLE_TABLES);
      subset = glyphSubsetter.subset().build();
    }
    else {
      final Subsetter glyphSubsetter = new HintStripper(font, factory);
      glyphSubsetter.setRemoveTables(GLYPH_REMOVABLE_TABLES);
      subset = glyphSubsetter.subset().build();
    }
    final Subsetter hintSubsetter = new HintStripper(subset, factory);
    hintSubsetter.setRemoveTables(HINT_REMOVABLE_TABLES);
    return hintSubsetter.subset().build();
  }

  public byte[] woff(final String str) throws IOException {
    //-w -h -e -b64 "abcdef" font.ttf
    // woff = true, strip = true, encode = true
    final Font stripped = strip(str);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    new WoffWriter().convert(stripped).copyTo(out);
    out.close();
    return out.toByteArray();
  }

  public byte[] woff2(final String str) throws IOException {
    //-w -h -e -b64 "abcdef" font.ttf
    // woff = true, strip = true, encode = true
    final Font stripped = strip(str);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    new Woff2Writer().convert(stripped).copyTo(out);
    out.close();
    return out.toByteArray();
  }

  private static CMap getBestCMap(CMapTable cmapTable) {
    for (CMap cmap : cmapTable) {
      if (cmap.format() == CMap.CMapFormat.Format12.value()) {
        return cmap;
      }
    }
    for (CMap cmap : cmapTable) {
      if (cmap.format() == CMap.CMapFormat.Format4.value()) {
        return cmap;
      }
    }
    return null;
  }

  public String glyphs() throws IOException {
    final CMapTable cmapTable = font.getTable(Tag.cmap);
    final CMap cmap = getBestCMap(cmapTable);
    if (cmap == null) return "";

    final Map<Integer, Character> map = new HashMap<>(1024);
    final Iterator<Integer> iterator = cmap.iterator();
    while (iterator.hasNext()) {
      final int c = iterator.next();
      final int glyphId = cmap.glyphId(c);
      if (glyphId > 0) map.put(glyphId, (char)c);
    }

    final GlyphTable glyphTable = font.getTable(Tag.glyf);
    final LocaTable locaTable = font.getTable(Tag.loca);
    if (glyphTable == null || locaTable == null) return "";

    final StringBuilder builder = new StringBuilder(1024);

    for (int i=0; i<locaTable.numGlyphs(); ++i) {
      int offset = locaTable.glyphOffset(i);
      int length = locaTable.glyphLength(i);
      Glyph glyph = glyphTable.glyph(offset, length);
      if (glyph != null) {
        if (glyph.readFontData().size() > 0) {
          final Character c = map.get(i);
          if (c != null) builder.append(c);
        }
      }
    }
    return builder.toString();
  }

//  public byte[] woff2(final String str) throws IOException {
//    final Font stripped = strip(str);
//    final ByteArrayOutputStream out = new ByteArrayOutputStream();
//    new Woff2Writer().convert(stripped).copyTo(out);
//    out.close();
//    return out.toByteArray();
//  }

  public byte[] ttf(final String str) throws IOException {
    //-h -e -b64 "abcdef" font.ttf
    // woff = false, strip = true, encode = true
    final Font stripped = strip(str);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    factory.serializeFont(stripped, out);
    out.close();
    return out.toByteArray();
  }

  public static void main(final String[] args) throws IOException {
    final FileInputStream input = new FileInputStream(new File("m:/exclam.ttf"));
    try {
      final byte[] buffer = new byte[4096];
      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      int len;
      while ((len = input.read(buffer)) != -1) {
        output.write(buffer, 0, len);
      }
      output.close();
      final Extractor extractor = new Extractor(output.toByteArray());
      final byte[] bytes1 = extractor.woff2("!"); //abcdefghijklmnopqrstuvwxyz");
      //if (bytes1.length < 1024) throw new RuntimeException();
      System.out.println(bytes1.length);
      final FileOutputStream fos1 = new FileOutputStream("m:/exclam.woff2");
      fos1.write(bytes1);
      fos1.close();
/*
      final byte[] bytes2 = extractor.ttf("abcdefghijklmnopqrstuvwxyz");
      //if (bytes2.length < 1024) throw new RuntimeException();
      System.out.println(bytes2.length);
      final FileOutputStream fos2 = new FileOutputStream("subset.ttf");
      fos2.write(bytes2);
      fos2.close();
      if (!new Extractor(bytes2).glyphs().equals("abcdefghijklmnopqrstuvwxyz")) throw new AssertionError();

//      final byte[] bytes3 = extractor.woff2("abcdefghijklmnopqrstuvwxyz");
//      if (bytes3.length < 1024) throw new RuntimeException();
//      System.out.println(bytes3.length);
//      final FileOutputStream fos3 = new FileOutputStream("subset.woff2");
//      fos3.write(bytes3);
//      fos3.close();
*/
//      final byte[] bytes4 = extractor.woff(null);
//      //if (bytes4.length < 1024) throw new RuntimeException();
//      System.out.println(bytes4.length);
//      final FileOutputStream fos4 = new FileOutputStream("m:/exclam.woff");
//      fos4.write(bytes4);
//      fos4.close();

//      final byte[] bytes5 = extractor.woff2(null);
//      if (bytes5.length < 1024) throw new RuntimeException();
//      System.out.println(bytes5.length);
//      final FileOutputStream fos5 = new FileOutputStream("converted.woff2");
//      fos5.write(bytes5);
//      fos5.close();
    }
    finally {
      try {
        input.close();
      }
      catch (final IOException ignore) {}
    }
  }

}
