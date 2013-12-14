/**
 * 
 */
package usd;


import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;


/**
 * Provides information about a shared service.
 */
public final class ServiceInfo
{
  private final String m_id;
  private final String m_name;
  private final URI m_uri;
  private final Map<String, String> m_properties;

  /**
   * Creates a new {@link ServiceInfo} instance.
   */
  public ServiceInfo( String id, String name, URI uri )
  {
    this( id, name, uri, Collections.<String, String> emptyMap() );
  }

  /**
   * Creates a new {@link ServiceInfo} instance.
   */
  public ServiceInfo( String id, String name, URI uri, Map<String, String> properties )
  {
    m_id = id;
    m_name = name;
    m_uri = uri;
    m_properties = Collections.unmodifiableMap( new HashMap<String, String>( properties ) );
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

    ServiceInfo other = ( ServiceInfo )obj;
    if ( m_id == null )
    {
      if ( other.m_id != null )
      {
        return false;
      }
    }
    else if ( !m_id.equals( other.m_id ) )
    {
      return false;
    }

    if ( m_name == null )
    {
      if ( other.m_name != null )
      {
        return false;
      }
    }
    else if ( !m_name.equals( other.m_name ) )
    {
      return false;
    }

    if ( m_properties == null )
    {
      if ( other.m_properties != null )
      {
        return false;
      }
    }
    else if ( !m_properties.equals( other.m_properties ) )
    {
      return false;
    }

    if ( m_uri == null )
    {
      if ( other.m_uri != null )
      {
        return false;
      }
    }
    else if ( !m_uri.equals( other.m_uri ) )
    {
      return false;
    }

    return true;
  }

  /**
   * @return the service identifier, never <code>null</code>.
   */
  public String getId()
  {
    return m_id;
  }

  /**
   * @return a human-readable name of the service, never <code>null</code>.
   */
  public String getName()
  {
    return m_name;
  }

  /**
   * @return an optional set of additional service properties.
   */
  public Map<String, String> getProperties()
  {
    return m_properties;
  }

  /**
   * @return the service endpoint, as {@link URI}.
   */
  public URI getURI()
  {
    return m_uri;
  }

  @Override
  public int hashCode()
  {
    final int prime = 31;
    int result = 1;
    result = prime * result + ( ( m_id == null ) ? 0 : m_id.hashCode() );
    result = prime * result + ( ( m_name == null ) ? 0 : m_name.hashCode() );
    result = prime * result + ( ( m_properties == null ) ? 0 : m_properties.hashCode() );
    result = prime * result + ( ( m_uri == null ) ? 0 : m_uri.hashCode() );
    return result;
  }
}
