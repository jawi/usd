/**
 * 
 */
package usd.impl;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static usd.Constants.USD_DEFAULT_GROUP_IP;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import usd.Ensure;
import usd.ServiceInfo;
import usd.ServiceListener;


/**
 * Test cases for {@link MulticastServiceAnnouncer}.
 */
public class MulticastServiceAnnouncerTest
{
  static class LatchServiceListener implements ServiceListener
  {
    private final CountDownLatch m_latch;
    private final StepMethod m_method;
    private final boolean m_log;

    public LatchServiceListener( int count, StepMethod method, boolean log )
    {
      m_latch = new CountDownLatch( count );
      m_method = method;
      m_log = log;
    }

    public CountDownLatch getLatch()
    {
      return m_latch;
    }

    @Override
    public void serviceAdded( ServiceInfo info )
    {
      if ( m_method.isAdd() )
      {
        m_latch.countDown();
        if ( m_log )
        {
          System.out.printf( ">> service added: %s (%s): %d...%n", info.getName(), info.getId(), m_latch.getCount() );
        }
      }
    }

    @Override
    public void serviceRemoved( ServiceInfo info )
    {
      if ( m_method.isRemove() )
      {
        m_latch.countDown();
        if ( m_log )
        {
          System.out.printf( ">> service removed: %s (%s): %d...%n", info.getName(), info.getId(), m_latch.getCount() );
        }
      }
    }
  }

  static class EnsureServiceListener implements ServiceListener
  {
    private final Ensure m_ensure;
    private final StepMethod m_method;

    public EnsureServiceListener( Ensure ensure, StepMethod method )
    {
      m_ensure = ensure;
      m_method = method;
    }

    @Override
    public void serviceAdded( ServiceInfo info )
    {
      if ( m_method.isAdd() )
      {
        m_ensure.step();
      }
    }

    @Override
    public void serviceRemoved( ServiceInfo info )
    {
      if ( m_method.isRemove() )
      {
        m_ensure.step();
      }
    }
  }

  static class LoggingServiceListener implements ServiceListener
  {
    private final String m_name;

    public LoggingServiceListener( String name )
    {
      m_name = name;
    }

    @Override
    public void serviceAdded( ServiceInfo info )
    {
      System.out.printf( ">> [%s] service added: %s (%s)...%n", m_name, info.getName(), info.getId() );
    }

    @Override
    public void serviceRemoved( ServiceInfo info )
    {
      System.out.printf( ">> [%s] service removed: %s (%s)...%n", m_name, info.getName(), info.getId() );
    }
  }

  static enum StepMethod
  {
    ADD, REMOVE, ADD_REMOVE;

    boolean isAdd()
    {
      return this == ADD || this == ADD_REMOVE;
    }

    boolean isRemove()
    {
      return this == REMOVE || this == ADD_REMOVE;
    }
  }

  private static final long TIMEOUT = 5000;

  private static AtomicInteger PORT_REF = new AtomicInteger( 50000 );

  private int m_port;
  private MulticastServiceAnnouncer m_ann1;
  private MulticastServiceAnnouncer m_ann2;
  private MulticastServiceAnnouncer m_ann3;

  @Before
  public void setUp() throws Exception
  {
    m_port = PORT_REF.incrementAndGet();
    m_ann1 = new MulticastServiceAnnouncer();
    m_ann2 = new MulticastServiceAnnouncer();
    m_ann3 = new MulticastServiceAnnouncer();
  }

  @After
  public void tearDown() throws Exception
  {
    m_ann1 = stop( m_ann1 );
    m_ann2 = stop( m_ann2 );
    m_ann3 = stop( m_ann3 );
  }

  @Test
  public void testAddServiceNotifiesLocalListenersOk() throws Exception
  {
    Collection<ServiceInfo> services;
    Ensure ensure = new Ensure();

    start( m_ann1 );

    m_ann1.addServiceListener( new EnsureServiceListener( ensure, StepMethod.ADD ) );

    ServiceInfo service1 = new ServiceInfo( "id1", "Service1", URI.create( "http://localhost:8080/serv1" ) );

    m_ann1.addService( service1 );

    ensure.waitForStep( 1, TIMEOUT );

    services = m_ann1.getKnownServices();
    assertEquals( 1, services.size() );
    assertTrue( services.contains( service1 ) );

    ServiceInfo service2 = new ServiceInfo( "id2", "Service2", URI.create( "http://localhost:8080/serv2" ) );

    m_ann1.addService( service2 );

    ensure.waitForStep( 2, TIMEOUT );

    services = m_ann1.getKnownServices();
    assertEquals( 2, services.size() );
    assertTrue( services.contains( service1 ) );
    assertTrue( services.contains( service2 ) );
  }

  @Test
  public void testAddServiceNotifiesRemoteListenersOk() throws Exception
  {
    Collection<ServiceInfo> services;
    Ensure ensure = new Ensure();

    start( m_ann1 );
    start( m_ann2 );

    m_ann2.addServiceListener( new EnsureServiceListener( ensure, StepMethod.ADD ) );

    ServiceInfo service1 = new ServiceInfo( "id1", "Service1", URI.create( "http://localhost:8080/serv1" ) );

    m_ann1.addService( service1 );

    ensure.waitForStep( 1, TIMEOUT );

    services = m_ann2.getKnownServices();
    assertEquals( 1, services.size() );
    assertTrue( services.contains( service1 ) );

    ServiceInfo service2 = new ServiceInfo( "id2", "Service2", URI.create( "http://localhost:8080/serv2" ) );

    m_ann1.addService( service2 );

    ensure.waitForStep( 2, TIMEOUT );

    services = m_ann2.getKnownServices();
    assertEquals( 2, services.size() );
    assertTrue( services.contains( service1 ) );
    assertTrue( services.contains( service2 ) );
  }

  @Test
  public void testRemoveServiceNotifiesLocalListenersOk() throws Exception
  {
    Collection<ServiceInfo> services;
    Ensure ensure = new Ensure();

    ServiceInfo service1 = new ServiceInfo( "id1", "Service1", URI.create( "http://localhost:8080/serv1" ) );
    ServiceInfo service2 = new ServiceInfo( "id2", "Service2", URI.create( "http://localhost:8080/serv2" ) );

    m_ann1.addServiceListener( new EnsureServiceListener( ensure, StepMethod.REMOVE ) );
    m_ann1.addService( service1 );
    m_ann1.addService( service2 );

    start( m_ann1 );

    m_ann1.removeService( service1 );

    ensure.waitForStep( 1, TIMEOUT );

    services = m_ann1.getKnownServices();
    assertEquals( 1, services.size() );
    assertTrue( services.contains( service2 ) );

    m_ann1.removeService( service2 );

    ensure.waitForStep( 2, TIMEOUT );

    services = m_ann1.getKnownServices();
    assertEquals( 0, services.size() );
  }

  @Test
  public void testRemoveServiceNotifiesRemoteListenersOk() throws Exception
  {
    Collection<ServiceInfo> services;
    Ensure addEnsure = new Ensure();
    Ensure removeEnsure = new Ensure();

    ServiceInfo service1 = new ServiceInfo( "id1", "Service1", URI.create( "http://localhost:8080/serv1" ) );
    ServiceInfo service2 = new ServiceInfo( "id2", "Service2", URI.create( "http://localhost:8080/serv2" ) );

    start( m_ann1 );
    start( m_ann2 );

    m_ann1.addService( service1 );
    m_ann1.addService( service2 );

    m_ann2.addServiceListener( new EnsureServiceListener( addEnsure, StepMethod.ADD ) );
    m_ann2.addServiceListener( new EnsureServiceListener( removeEnsure, StepMethod.REMOVE ) );

    addEnsure.waitForStep( 2, TIMEOUT );

    m_ann1.removeService( service1 );

    removeEnsure.waitForStep( 1, TIMEOUT );

    services = m_ann2.getKnownServices();
    assertEquals( 1, services.size() );
    assertTrue( services.contains( service2 ) );

    m_ann1.removeService( service2 );

    removeEnsure.waitForStep( 2, TIMEOUT );

    services = m_ann2.getKnownServices();
    assertEquals( 0, services.size() );
  }

  @Test
  public void testServiceBroadcastOk() throws Exception
  {
    Collection<ServiceInfo> services;
    final int count = 5000;

    List<ServiceInfo> allServices = new ArrayList<ServiceInfo>();
    for ( int i = 0; i < count; i++ )
    {
      String id = String.format( "id%03d", i + 1 );
      String name = String.format( "Service %03d", i + 1 );
      URI uri = URI.create( String.format( "http://localhost:8080/serv%03d", i + 1 ) );
      Map<String, String> props = new HashMap<String, String>();
      props.put( "key1", String.format( "value%03d", i ) );
      props.put( "key2", String.format( "value%03d", i ) );
      props.put( "key3", String.format( "value%03d", i ) );

      allServices.add( new ServiceInfo( id, name, uri, props ) );
    }

    start( m_ann1 );
    start( m_ann2 );

    LatchServiceListener listener1 = new LatchServiceListener( count / 2, StepMethod.ADD, false );
    LatchServiceListener listener2 = new LatchServiceListener( count / 2, StepMethod.ADD, false );
    LatchServiceListener listener3 = new LatchServiceListener( count, StepMethod.ADD, false );

    m_ann1.addServiceListener( listener1 );
    m_ann2.addServiceListener( listener2 );
    m_ann3.addServiceListener( listener3 );

    for ( int i = 0; i < count; i++ )
    {
      if ( i % 2 == 0 )
      {
        m_ann1.addService( allServices.get( i ) );
      }
      else
      {
        m_ann2.addService( allServices.get( i ) );
      }
    }

    assertTrue( listener1.getLatch().await( TIMEOUT, TimeUnit.MILLISECONDS ) );
    assertTrue( listener2.getLatch().await( TIMEOUT, TimeUnit.MILLISECONDS ) );

    start( m_ann3 );

    assertTrue( listener3.getLatch().await( 2 * TIMEOUT, TimeUnit.MILLISECONDS ) );

    services = m_ann3.getKnownServices();
    assertEquals( count, services.size() );
    for ( int i = 0; i < count; i++ )
    {
      assertTrue( "Service with ID " + ( i + 1 ) + " not found?!", services.contains( allServices.get( i ) ) );
    }

    m_ann3.stop();
  }

  private MulticastServiceAnnouncer start( MulticastServiceAnnouncer announcer ) throws IOException
  {
    announcer.start( InetAddress.getByName( USD_DEFAULT_GROUP_IP ), m_port );
    return announcer;
  }

  private MulticastServiceAnnouncer stop( MulticastServiceAnnouncer announcer )
  {
    if ( announcer != null )
    {
      try
      {
        announcer.stop();
      }
      catch ( Exception e )
      {
        // Ignore...
      }
    }
    return null;
  }
}
