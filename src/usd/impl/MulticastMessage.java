/**
 * 
 */
package usd.impl;


import usd.ServiceInfo;


/**
 * Multicast message.
 */
public class MulticastMessage
{
  private static final int INFO_GET_STATE = 0x00;
  private static final int INFO_REMOVED = 0x02;
  private static final int INFO_ADDED = 0x03;

  private final int m_info;
  private final ServiceInfo m_serviceInfo;

  /**
   * Creates a new {@link MulticastMessage} instance.
   */
  MulticastMessage( int info, ServiceInfo serviceInfo )
  {
    m_info = info;
    m_serviceInfo = serviceInfo;
  }

  public static MulticastMessage createBroadcastStateMessage()
  {
    return new MulticastMessage( INFO_GET_STATE, null );
  }

  public static MulticastMessage createServiceAddedMessage( ServiceInfo info )
  {
    return new MulticastMessage( INFO_ADDED, info );
  }

  public static MulticastMessage createServiceRemovedMessage( ServiceInfo info )
  {
    return new MulticastMessage( INFO_REMOVED, info );
  }

  @Override
  public boolean equals( Object obj )
  {
    if ( this == obj )
    {
      return true;
    }
    if ( obj == null || getClass() != obj.getClass() )
    {
      return false;
    }

    MulticastMessage other = ( MulticastMessage )obj;
    if ( m_info != other.m_info )
    {
      return false;
    }
    if ( m_serviceInfo == null )
    {
      if ( other.m_serviceInfo != null )
      {
        return false;
      }
    }
    else if ( !m_serviceInfo.equals( other.m_serviceInfo ) )
    {
      return false;
    }

    return true;
  }

  public int getInfo()
  {
    return m_info;
  }

  public ServiceInfo getServiceInfo()
  {
    return m_serviceInfo;
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + m_info;
    result = prime * result + ( ( m_serviceInfo == null ) ? 0 : m_serviceInfo.hashCode() );
    return result;
  }

  public boolean isBroadcastState()
  {
    return ( m_info & 0x3 ) == 0;
  }

  public boolean isServiceAdded()
  {
    return ( m_info & INFO_ADDED ) == INFO_ADDED;
  }

  public boolean isServiceRemoved()
  {
    return ( m_info & INFO_REMOVED ) == INFO_REMOVED;
  }
}
