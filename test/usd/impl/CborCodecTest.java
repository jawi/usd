/**
 * 
 */
package usd.impl;


import static org.junit.Assert.assertEquals;
import static usd.impl.CborCodec.MT_UNSIGNED_INT;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import usd.ServiceInfo;


/**
 * Test cases for {@link CborCodec}.
 */
public class CborCodecTest
{
  @Test
  public void testCodec() throws IOException
  {
    MulticastMessage orig = new MulticastMessage( 0x01, new ServiceInfo( "id1", "name1",
        URI.create( "http://localhost:8080/" ) ) );

    MulticastMessage read = CborCodec.decode( CborCodec.encode( orig ) );

    assertEquals( orig, read );
  }

  @Test
  public void testDecodeInt() throws IOException
  {
    assertEquals( 0, decodeInt( MT_UNSIGNED_INT, 0x00 ) );
    assertEquals( 1, decodeInt( MT_UNSIGNED_INT, 0x01 ) );
    assertEquals( 10, decodeInt( MT_UNSIGNED_INT, 0x0a ) );
    assertEquals( 23, decodeInt( MT_UNSIGNED_INT, 0x17 ) );
    assertEquals( 24, decodeInt( MT_UNSIGNED_INT, 0x18, 0x18 ) );
    assertEquals( 25, decodeInt( MT_UNSIGNED_INT, 0x18, 0x19 ) );
    assertEquals( 1000, decodeInt( MT_UNSIGNED_INT, 0x19, 0x03, 0xe8 ) );
    assertEquals( 1000000000000L, decodeInt( MT_UNSIGNED_INT, 0x1b, 0x00, 0x00, 0x00, 0xe8, 0xd4, 0xa5, 0x10, 0x00 ) );
  }

  @Test
  public void testDecodeMap() throws IOException
  {
    Map<String, String> map = new HashMap<String, String>();
    map.put( "a", "A" );
    map.put( "b", "B" );
    map.put( "c", "C" );
    map.put( "d", "D" );
    map.put( "e", "E" );

    assertEquals(
        map,
        decodeMap( 0xa5, 0x61, 0x61, 0x61, 0x41, 0x61, 0x62, 0x61, 0x42, 0x61, 0x63, 0x61, 0x43, 0x61, 0x64, 0x61,
            0x44, 0x61, 0x65, 0x61, 0x45 ) );
  }

  @Test
  public void testDecodeString() throws IOException
  {
    assertEquals( "IETF", decodeString( 0x64, 0x49, 0x45, 0x54, 0x46 ) );
  }

  @Test
  public void testEncodeInt() throws IOException
  {
    assertEncoded( encodeInt( MT_UNSIGNED_INT, 0 ), 0x00 );
    assertEncoded( encodeInt( MT_UNSIGNED_INT, 1 ), 0x01 );
    assertEncoded( encodeInt( MT_UNSIGNED_INT, 10 ), 0x0a );
    assertEncoded( encodeInt( MT_UNSIGNED_INT, 23 ), 0x17 );
    assertEncoded( encodeInt( MT_UNSIGNED_INT, 24 ), 0x18, 0x18 );
    assertEncoded( encodeInt( MT_UNSIGNED_INT, 25 ), 0x18, 0x19 );
    assertEncoded( encodeInt( MT_UNSIGNED_INT, 1000 ), 0x19, 0x03, 0xe8 );
    assertEncoded( encodeInt( MT_UNSIGNED_INT, 1000000000000L ), 0x1b, 0x00, 0x00, 0x00, 0xe8, 0xd4, 0xa5, 0x10, 0x00 );
  }

  @Test
  public void testEncodeMap() throws IOException
  {
    Map<String, String> map = new LinkedHashMap<String, String>();
    map.put( "a", "A" );
    map.put( "b", "B" );
    map.put( "c", "C" );
    map.put( "d", "D" );
    map.put( "e", "E" );

    assertEncoded( encodeMap( map ), 0xa5, 0x61, 0x61, 0x61, 0x41, 0x61, 0x62, 0x61, 0x42, 0x61, 0x63, 0x61, 0x43,
        0x61, 0x64, 0x61, 0x44, 0x61, 0x65, 0x61, 0x45 );
  }

  @Test
  public void testEncodeString() throws IOException
  {
    assertEncoded( encodeString( "IETF" ), 0x64, 0x49, 0x45, 0x54, 0x46 );
  }

  private static void assertEncoded( byte[] encoded, int... expected )
  {
    assertEquals( expected.length, encoded.length );
    for ( int i = 0; i < expected.length; i++ )
    {
      assertEquals( "Index: " + i, ( byte )expected[i], encoded[i] );
    }
  }

  private long decodeInt( int type, int... bytes ) throws IOException
  {
    byte[] data = toByteArray( bytes );
    return CborCodec.decodeInt( new ByteArrayInputStream( data ), type );
  }

  private Map<String, String> decodeMap( int... bytes ) throws IOException
  {
    byte[] data = toByteArray( bytes );
    return CborCodec.decodeMap( new ByteArrayInputStream( data ) );
  }

  private String decodeString( int... bytes ) throws IOException
  {
    byte[] data = toByteArray( bytes );
    return CborCodec.decodeString( new ByteArrayInputStream( data ) );
  }

  private byte[] encodeInt( int type, long value ) throws IOException
  {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    CborCodec.encodeInt( baos, type, value );
    return baos.toByteArray();
  }

  private byte[] encodeMap( Map<String, String> map ) throws IOException
  {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    CborCodec.encodeMap( baos, map );
    return baos.toByteArray();
  }

  private byte[] encodeString( String value ) throws IOException
  {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    CborCodec.encodeString( baos, value );
    return baos.toByteArray();
  }

  private byte[] toByteArray( int... bytes )
  {
    byte[] data = new byte[bytes.length];
    for ( int i = 0; i < bytes.length; i++ )
    {
      data[i] = ( byte )( bytes[i] & 0xff );
    }
    return data;
  }
}
