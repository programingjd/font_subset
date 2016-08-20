package info.jdavid.font.subset;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.typography.font.sfntly.Font;
import com.google.typography.font.sfntly.FontFactory;
import com.google.typography.font.sfntly.Tag;
import com.google.typography.font.sfntly.table.core.CMapTable;
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

  public byte[] woff(final String str) throws IOException {
    //-w -h -e -b64 "abcdef" font.ttf
    // woff = true, strip = true, encode = true
    final List<CMapTable.CMapId> cmapIds = new ArrayList<>();
    cmapIds.add(CMapTable.CMapId.WINDOWS_BMP);
    final Subsetter glyphSubsetter = new RenumberingSubsetter(font, factory);
    glyphSubsetter.setCMaps(cmapIds, 1);
    glyphSubsetter.setGlyphs(GlyphCoverage.getGlyphCoverage(font, str));
    glyphSubsetter.setRemoveTables(GLYPH_REMOVABLE_TABLES);
    final Font subset = glyphSubsetter.subset().build();
    final Subsetter hintSubsetter = new HintStripper(subset, factory);
    hintSubsetter.setRemoveTables(HINT_REMOVABLE_TABLES);
    final Font stripped = hintSubsetter.subset().build();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    new WoffWriter().convert(stripped).copyTo(out);
    out.close();
    return out.toByteArray();
  }

  public byte[] ttf(final String str) throws IOException {
    //-h -e -b64 "abcdef" font.ttf
    // woff = false, strip = true, encode = true
    final List<CMapTable.CMapId> cmapIds = new ArrayList<>();
    cmapIds.add(CMapTable.CMapId.WINDOWS_BMP);
    final Subsetter glyphSubsetter = new RenumberingSubsetter(font, factory);
    glyphSubsetter.setCMaps(cmapIds, 1);
    glyphSubsetter.setGlyphs(GlyphCoverage.getGlyphCoverage(font, str));
    glyphSubsetter.setRemoveTables(GLYPH_REMOVABLE_TABLES);
    final Font subset = glyphSubsetter.subset().build();
    final Subsetter hintSubsetter = new HintStripper(subset, factory);
    hintSubsetter.setRemoveTables(HINT_REMOVABLE_TABLES);
    final Font stripped = hintSubsetter.subset().build();
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    factory.serializeFont(stripped, out);
    out.close();
    return out.toByteArray();
  }

  public static void main(final String[] args) throws IOException {
    final FileInputStream input = new FileInputStream(new File("DryBrush.ttf"));
    try {
      final byte[] buffer = new byte[4096];
      final ByteArrayOutputStream output = new ByteArrayOutputStream();
      int len;
      while ((len = input.read(buffer)) != -1) {
        output.write(buffer, 0, len);
      }
      output.close();
      final Extractor extractor = new Extractor(output.toByteArray());
      final byte[] bytes1 = extractor.woff("abcdefghijklmnopqrstuvwxyz");
      if (bytes1.length < 1024) throw new RuntimeException();
      System.out.println(bytes1.length);
      final FileOutputStream fos1 = new FileOutputStream("subset.woff");
      fos1.write(bytes1);
      fos1.close();

      final byte[] bytes2 = extractor.ttf("abcdefghijklmnopqrstuvwxyz");
      if (bytes2.length < 1024) throw new RuntimeException();
      System.out.println(bytes2.length);
      final FileOutputStream fos2 = new FileOutputStream("subset.ttf");
      fos2.write(bytes2);
      fos2.close();
    }
    finally {
      try {
        input.close();
      }
      catch (final IOException ignore) {}
    }
  }

}
