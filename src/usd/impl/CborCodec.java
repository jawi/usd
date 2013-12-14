/**
 * 
 */
package usd.impl;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import usd.ServiceInfo;


/**
 * Minimal CBOR (RFC7049) encoder/decoder.
 */
public class CborCodec
{
  static final int MT_UNSIGNED_INT = 0;
  static final int MT_TEXT_STRING = 3;
  static final int MT_MAP = 5;
  static final int MT_TAG = 6;

  private static final int ONE_BYTE = 0x18;
  private static final int TWO_BYTES = 0x19;
  private static final int FOUR_BYTES = 0x1a;
  private static final int EIGHT_BYTES = 0x1b;

  private static final int MAGIC = 55799;

  public static MulticastMessage decode( byte[] data ) throws IOException
  {
    return decode( data, 0, data.length );
  }

  public static MulticastMessage decode( byte[] data, int offset, int length ) throws IOException
  {
    ByteArrayInputStream bais = new ByteArrayInputStream( data, offset, length );
    // Header
    long magic = decodeInt( bais, MT_TAG );
    if ( magic != MAGIC )
    {
      throw new IOException( "Invalid packet, missing magic!" );
    }

    ServiceInfo serviceInfo = null;

    // Info byte
    int info = ( int )( decodeInt( bais, MT_UNSIGNED_INT ) & 0xffffffff );
    if ( ( info & 0x03 ) != 0 )
    {
      // ID
      String id = decodeString( bais );
      // Name
      String name = decodeString( bais );
      // URI
      URI uri = URI.create( decodeString( bais ) );
      // Props
      Map<String, String> props = decodeMap( bais );

      serviceInfo = new ServiceInfo( id, name, uri, props );
    }

    return new MulticastMessage( info, serviceInfo );
  }

  public static byte[] encode( MulticastMessage message ) throws IOException
  {
    ByteArrayOutputStream baos = new ByteArrayOutputStream( 32 * 1024 );
    // Header
    encodeInt( baos, MT_TAG, MAGIC );
    // Info byte
    encodeInt( baos, MT_UNSIGNED_INT, message.getInfo() );

    ServiceInfo info = message.getServiceInfo();
    if ( info != null )
    {
      // ID
      encodeString( baos, info.getId() );
      // Name
      encodeString( baos, info.getName() );
      // URI
      encodeString( baos, info.getURI().toASCIIString() );
      // Props
      encodeMap( baos, info.getProperties() );
    }

    return baos.toByteArray();
  }

  static long decodeInt( InputStream is, int type ) throws IOException
  {
    int ib = is.read();

    int mt = ib >>> 5;
    if ( mt != type )
    {
      throw new IOException( "Unexpected type!" );
    }

    int len = ib & 0x1f;
    if ( len < ONE_BYTE )
    {
      return len;
    }
    else if ( len == ONE_BYTE )
    {
      return is.read();
    }
    else if ( len == TWO_BYTES )
    {
      byte[] buf = readFully( is, new byte[2] );
      return ( buf[0] & 0xFF ) << 8 | ( buf[1] & 0xFF );
    }
    else if ( len == FOUR_BYTES )
    {
      byte[] buf = readFully( is, new byte[4] );
      return ( ( buf[0] & 0xFF ) << 24 | ( buf[1] & 0xFF ) << 16 | ( buf[2] & 0xFF ) << 8 | ( buf[3] & 0xFF ) ) & 0xffffffffL;
    }
    else if ( len == EIGHT_BYTES )
    {
      byte[] buf = readFully( is, new byte[8] );
      return ( buf[0] & 0xFFL ) << 56 | ( buf[1] & 0xFFL ) << 48 | ( buf[2] & 0xFFL ) << 40 | ( buf[3] & 0xFFL ) << 32 | //
          ( buf[4] & 0xFFL ) << 24 | ( buf[5] & 0xFFL ) << 16 | ( buf[6] & 0xFFL ) << 8 | ( buf[7] & 0xFFL );
    }
    throw new IOException( "Invalid integer!" );
  }

  static Map<String, String> decodeMap( InputStream is ) throws IOException
  {
    long size = decodeInt( is, MT_MAP );
    if ( size > Short.MAX_VALUE )
    {
      throw new IOException( "Too many map entries!" );
    }
    int len = ( int )( size & Short.MAX_VALUE );
    Map<String, String> result = new HashMap<String, String>( len );
    for ( int i = 0; i < len; i++ )
    {
      result.put( decodeString( is ), decodeString( is ) );
    }
    return result;
  }

  static String decodeString( InputStream is ) throws IOException
  {
    long size = decodeInt( is, MT_TEXT_STRING );
    if ( size > Short.MAX_VALUE )
    {
      throw new IOException( "String too long!" );
    }
    return new String( readFully( is, new byte[( int )( size & Short.MAX_VALUE )] ) );
  }

  static void encodeInt( OutputStream baos, int type, long value ) throws IOException
  {
    int mt = ( type << 5 );
    if ( value < ONE_BYTE )
    {
      baos.write( ( int )( mt | value ) );
    }
    else if ( value < 0x100L )
    {
      baos.write( mt | ONE_BYTE );
      baos.write( ( int )( value & 0xFF ) );
    }
    else if ( value < 0x10000L )
    {
      baos.write( mt | TWO_BYTES );
      baos.write( ( int )( value >> 8 ) );
      baos.write( ( int )( value & 0xFF ) );
    }
    else if ( value < 0x100000000L )
    {
      baos.write( mt | FOUR_BYTES );
      baos.write( ( int )( value >> 24 ) );
      baos.write( ( int )( value >> 16 ) );
      baos.write( ( int )( value >> 8 ) );
      baos.write( ( int )( value & 0xFF ) );
    }
    else
    {
      baos.write( mt | EIGHT_BYTES );
      baos.write( ( int )( value >> 56 ) );
      baos.write( ( int )( value >> 48 ) );
      baos.write( ( int )( value >> 40 ) );
      baos.write( ( int )( value >> 32 ) );
      baos.write( ( int )( value >> 24 ) );
      baos.write( ( int )( value >> 16 ) );
      baos.write( ( int )( value >> 8 ) );
      baos.write( ( int )( value & 0xFF ) );
    }
  }

  static void encodeMap( OutputStream baos, Map<String, String> props ) throws IOException
  {
    encodeInt( baos, MT_MAP, props.size() );
    for ( Map.Entry<String, String> entry : props.entrySet() )
    {
      encodeString( baos, entry.getKey() );
      encodeString( baos, entry.getValue() );
    }
  }

  static void encodeString( OutputStream baos, String str ) throws IOException
  {
    encodeInt( baos, MT_TEXT_STRING, str.length() );
    for ( byte b : str.getBytes( "UTF-8" ) )
    {
      baos.write( b );
    }
  }

  private static byte[] readFully( InputStream is, byte[] buf ) throws IOException
  {
    int len = buf.length;
    int n = 0, off = 0;
    while ( n < len )
    {
      int count = is.read( buf, off + n, len - n );
      if ( count < 0 )
      {
        throw new EOFException();
      }
      n += count;
    }
    return buf;
  }

}
