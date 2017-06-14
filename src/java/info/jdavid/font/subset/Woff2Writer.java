package info.jdavid.font.subset;

// Adapted from chromium's font-compression-reference code by Raph Levien
// https://chromium.googlesource.com/external/font-compression-reference/+/
// 5ce8fad3ab9824f9f4d5fb4768c313b6309e94e3/src/com/google/typography/font/compression/Woff2Writer.java

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import SevenZip.Compression.LZMA.Encoder;
import com.google.typography.font.sfntly.Font;
import com.google.typography.font.sfntly.Tag;
import com.google.typography.font.sfntly.data.WritableFontData;
import com.google.typography.font.sfntly.table.Table;
import com.google.typography.font.sfntly.table.core.FontHeaderTable;

public class Woff2Writer {
  private static final long SIGNATURE = 0x774f4632;
  private static final int WOFF2_HEADER_SIZE = 44;
  private static final int TABLE_ENTRY_SIZE = 5 * 4;
  private static final int FLAG_CONTINUE_STREAM = 1 << 4;
  private static final int FLAG_APPLY_TRANSFORM = 1 << 5;
  private final boolean longForm = false;

  private static final Map<Integer, Integer> TRANSFORM_MAP = createTransformMap();
  private static Map<Integer, Integer> createTransformMap() {
    final Map<Integer, Integer> map = new HashMap<>(2);
    map.put(Tag.glyf, Tag.intValue(new byte[]{ 'g', 'l', 'z', '1' }));
    map.put(Tag.loca, Tag.intValue(new byte[]{ 'l', 'o', 'c', 'z' }));
    return map;
  }

  private static final Map<Integer, Integer> KNOWN_TABLES = createKnownTables();
  private static Map<Integer, Integer> createKnownTables() {
    final Map<Integer, Integer> map = new HashMap<>(30);
    map.put(Tag.intValue(new byte[] {'c', 'm', 'a', 'p' }), 0);
    map.put(Tag.intValue(new byte[]{ 'h', 'e', 'a', 'd' }), 1);
    map.put(Tag.intValue(new byte[]{ 'h', 'h', 'e', 'a' }), 2);
    map.put(Tag.intValue(new byte[]{ 'h', 'm', 't', 'x' }), 3);
    map.put(Tag.intValue(new byte[]{ 'm', 'a', 'x', 'p' }), 4);
    map.put(Tag.intValue(new byte[]{ 'n', 'a', 'm', 'e' }), 5);
    map.put(Tag.intValue(new byte[]{ 'O', 'S', '/', '2' }), 6);
    map.put(Tag.intValue(new byte[]{ 'p', 'o', 's', 't' }), 7);
    map.put(Tag.intValue(new byte[]{ 'c', 'v', 't', ' ' }), 8);
    map.put(Tag.intValue(new byte[]{ 'f', 'p', 'g', 'm' }), 9);
    map.put(Tag.intValue(new byte[]{ 'g', 'l', 'y', 'f' }), 10);
    map.put(Tag.intValue(new byte[]{ 'l', 'o', 'c', 'a' }), 11);
    map.put(Tag.intValue(new byte[]{ 'p', 'r', 'e', 'p' }), 12);
    map.put(Tag.intValue(new byte[]{ 'C', 'F', 'F', ' ' }), 13);
    map.put(Tag.intValue(new byte[]{ 'V', 'O', 'R', 'G' }), 14);
    map.put(Tag.intValue(new byte[]{ 'E', 'B', 'D', 'T' }), 15);
    map.put(Tag.intValue(new byte[]{ 'E', 'B', 'L', 'C' }), 16);
    map.put(Tag.intValue(new byte[]{ 'g', 'a', 's', 'p' }), 17);
    map.put(Tag.intValue(new byte[]{ 'h', 'd', 'm', 'x' }), 18);
    map.put(Tag.intValue(new byte[]{ 'k', 'e', 'r', 'n' }), 19);
    map.put(Tag.intValue(new byte[]{ 'L', 'T', 'S', 'H' }), 20);
    map.put(Tag.intValue(new byte[]{ 'P', 'C', 'L', 'T' }), 21);
    map.put(Tag.intValue(new byte[]{ 'V', 'D', 'M', 'X' }), 22);
    map.put(Tag.intValue(new byte[]{ 'v', 'h', 'e', 'a' }), 23);
    map.put(Tag.intValue(new byte[]{ 'v', 'm', 't', 'x' }), 24);
    map.put(Tag.intValue(new byte[]{ 'B', 'A', 'S', 'E' }), 25);
    map.put(Tag.intValue(new byte[]{ 'G', 'D', 'E', 'F' }), 26);
    map.put(Tag.intValue(new byte[]{ 'G', 'P', 'O', 'S' }), 27);
    map.put(Tag.intValue(new byte[]{ 'G', 'S', 'U', 'B' }), 28);
    return map;
  }

  public Woff2Writer() {}

  public WritableFontData convert(final Font font) {
    final List<TableDirectoryEntry> entries = createTableDirectoryEntries(font);
    final int size = computeCompressedFontSize(entries);
    final WritableFontData writableFontData = WritableFontData.createWritableFontData(size);
    int index = 0;
    final FontHeaderTable head = font.getTable(Tag.head);
    index += writeWoff2Header(writableFontData, entries, font.sfntVersion(), size, head.fontRevision());
    index += writeDirectory(writableFontData, index, entries);
    /*index +=*/ writeTables(writableFontData, index, entries);
    return writableFontData;
  }

  private List<TableDirectoryEntry> createTableDirectoryEntries(final Font font) {
    final List<TableDirectoryEntry> entries = new ArrayList<>();
    final TreeSet<Integer> tags = new TreeSet<>(font.tableMap().keySet());
    for (int tag: tags) {
      final Table table = font.getTable(tag);
      final byte[] uncompressedBytes = bytesFromTable(table);
      if (TRANSFORM_MAP.containsValue(tag)) {
        // Don't store the intermediate transformed tables under the nonstandard tags.
        continue;
      }
      final byte[] transformedBytes;
      if (TRANSFORM_MAP.containsKey(tag)) {
        int transformedTag = TRANSFORM_MAP.get(tag);
        Table transformedTable = font.getTable(transformedTag);
        if (transformedTable != null) {
          transformedBytes = bytesFromTable(transformedTable);
        }
        else {
          transformedBytes = null;
        }
      }
      else {
        transformedBytes = null;
      }
      if (transformedBytes == null) {
        entries.add(new TableDirectoryEntry(tag, uncompressedBytes));
      }
      else {
        entries.add(new TableDirectoryEntry(tag, uncompressedBytes, transformedBytes, FLAG_APPLY_TRANSFORM));
      }
    }
    return entries;
  }

  private byte[] bytesFromTable(final Table table) {
    final int length = table.dataLength();
    final byte[] bytes = new byte[length];
    table.readFontData().readBytes(0, bytes, 0, length);
    return bytes;
  }

  private int writeWoff2Header(final WritableFontData writableFontData,
                               final List<TableDirectoryEntry> entries,
                               final int flavor, final int length, final int version) {
    int index = 0;
    index += writableFontData.writeULong(index, SIGNATURE);
    index += writableFontData.writeULong(index, flavor);
    index += writableFontData.writeULong(index, length);
    index += writableFontData.writeUShort(index, entries.size());  // numTables
    index += writableFontData.writeUShort(index, 0);  // reserved
    int uncompressedFontSize = computeUncompressedSize(entries);
    index += writableFontData.writeULong(index, uncompressedFontSize);
    int compressedFontSize = computeCompressedFontSize(entries);
    index += writableFontData.writeULong(index, compressedFontSize);
    index += writableFontData.writeFixed(index, version);
    index += writableFontData.writeULong(index, 0);  // metaOffset
    index += writableFontData.writeULong(index, 0);  // metaLength
    index += writableFontData.writeULong(index, 0);  // metaOrigLength
    index += writableFontData.writeULong(index, 0);  // privOffset
    index += writableFontData.writeULong(index, 0);  // privLength
    return index;
  }

  private int writeDirectory(final WritableFontData writableFontData, final int offset,
                             final List<TableDirectoryEntry> entries) {
    final int directorySize = computeDirectoryLength(entries);
    int index = offset;
    for (final TableDirectoryEntry entry: entries) {
      index += entry.writeEntry(writableFontData, index);
    }
    return directorySize;
  }

  private int writeTables(final WritableFontData writableFontData, final int offset,
                          final List<TableDirectoryEntry> entries) {
    int index = offset;
    for (final TableDirectoryEntry entry: entries) {
      index += entry.writeData(writableFontData, index);
      index = align4(index);
    }
    return index - offset;
  }

  private int computeDirectoryLength(final List<TableDirectoryEntry> entries) {
    if (longForm) {
      return TABLE_ENTRY_SIZE * entries.size();
    }
    else {
      int index = 0;
      for (final TableDirectoryEntry entry: entries) {
        index += entry.writeEntry(null, index);
      }
      return index;
    }
  }

  private int align4(final int value) {
    return (value + 3) & -4;
  }

  private int computeUncompressedSize(final List<TableDirectoryEntry> entries) {
    int index = 20 + 16 * entries.size();  // sfnt header length
    for (final TableDirectoryEntry entry: entries) {
      index += entry.origLength;
      index = align4(index);
    }
    return index;
  }

  private int computeCompressedFontSize(final List<TableDirectoryEntry> entries) {
    int index = WOFF2_HEADER_SIZE;
    index += computeDirectoryLength(entries);
    for (final TableDirectoryEntry entry: entries) {
      index += entry.bytes.length;
      index = align4(index);
    }
    return index;
  }

  // Note: if writableFontData is null, just return the size
  private static int writeBase128(final WritableFontData writableFontData,
                                  final long value, final int offset) {
    int size = 1;
    long tmpValue = value;
    int index = offset;
    while (tmpValue >= 128) {
      size += 1;
      tmpValue = tmpValue >> 7;
    }
    for (int i=0; i<size; i++) {
      int b = (int)(value >> (7 * (size - i - 1))) & 0x7f;
      if (i < size - 1) {
        b |= 0x80;
      }
      if (writableFontData != null) {
        writableFontData.writeByte(index, (byte)b);
      }
      index += 1;
    }
    return size;
  }

  private class TableDirectoryEntry {
    private final long tag;
    private final long flags;
    public final long origLength;
    private final long transformLength;
    public final byte[] bytes;

    public TableDirectoryEntry(final long tag, final byte[] uncompressedBytes) {
      this(tag, uncompressedBytes, uncompressedBytes, 0);
    }

    public TableDirectoryEntry(final long tag, final byte[] uncompressedBytes,
                        final byte[] transformedBytes, final long transformFlags) {
      final byte[] compressedBytes = compress(transformedBytes);
      this.tag = tag;
      this.flags = transformFlags | 2L;
      this.origLength = uncompressedBytes.length;
      this.transformLength = transformedBytes.length;
      this.bytes = compressedBytes;
    }

    public int writeEntry(final WritableFontData writableFontData, final int offset) {
      if (longForm) {
        int index = offset;
        if (writableFontData != null) {
          index += writableFontData.writeULong(index, tag);
          index += writableFontData.writeULong(index, flags);
          index += writableFontData.writeULong(index, bytes.length);
          index += writableFontData.writeULong(index, transformLength);
          /*index +=*/ writableFontData.writeULong(index, origLength);
        }
        return TABLE_ENTRY_SIZE;
      }
      else {
        int index = offset;
        int flag_byte = 0x1f;
        if (KNOWN_TABLES.containsKey((int)tag)) {
          flag_byte = KNOWN_TABLES.get((int)tag);
        }
        if ((flags & FLAG_APPLY_TRANSFORM) != 0) {
          flag_byte |= 0x20;
        }
        if ((flags & FLAG_CONTINUE_STREAM) != 0) {
          flag_byte |= 0xc0;
        }
        else {
          flag_byte |= (flags & 3) << 6;
        }
        if (writableFontData != null) {
//          System.out.printf("%d: tag = %08x, flag = %02x\n", offset, tag, flag_byte);
          writableFontData.writeByte(index, (byte)flag_byte);
        }
        index += 1;
        if ((flag_byte & 0x1f) == 0x1f) {
          if (writableFontData != null) {
            writableFontData.writeULong(index, tag);
          }
          index += 4;
        }
        index += writeBase128(writableFontData, origLength, index);
        if ((flag_byte & 0x20) != 0) {
          index += writeBase128(writableFontData, transformLength, index);
        }
        if ((flag_byte & 0xc0) == 0x40 || (flag_byte & 0xc0) == 0x80) {
          index += writeBase128(writableFontData, bytes.length, index);
        }
        return index - offset;
      }
    }

    public int writeData(final WritableFontData writableFontData, final int offset) {
      writableFontData.writeBytes(offset, bytes);
      return bytes.length;
    }

  }

  private static byte[] compress(final byte[] input) {
    try {
      final ByteArrayInputStream in = new ByteArrayInputStream(input);
      final ByteArrayOutputStream out = new ByteArrayOutputStream();
      final Encoder encoder = new Encoder();
      encoder.SetAlgorithm(2);
      encoder.SetDictionarySize(1 << 23);
      encoder.SetNumFastBytes(128);
      encoder.SetMatchFinder(1);
      encoder.SetLcLpPb(3, 0, 2);
      encoder.SetEndMarkerMode(true);
      encoder.WriteCoderProperties(out);
      for (int i=0; i<8; i++) {
        out.write((int) ((long) -1 >>> (8 * i)) & 0xFF);
      }
      encoder.Code(in, out, -1, -1, null);
      out.flush();
      out.close();
      return out.toByteArray();
    }
    catch (final IOException e) {
      throw new RuntimeException(e);
    }
  }

}
