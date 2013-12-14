/**
 * 
 */
package usd.impl;


import static usd.Constants.USD_DEFAULT_GROUP_IP;
import static usd.Constants.USD_DEFAULT_PORT;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import usd.ServiceAnnouncer;
import usd.ServiceInfo;
import usd.ServiceListener;


/**
 * Multicast implementation of {@link ServiceAnnouncer}.
 */
public class MulticastServiceAnnouncer implements ServiceAnnouncer
{
  /**
   * Task for listening to service announcements.
   */
  final class MulticastListener implements Callable<Void>
  {
    private final InetAddress m_group;
    private final int m_port;
    private final AtomicLong m_recvCount;

    MulticastListener( InetAddress group, int port ) throws IOException
    {
      m_group = group;
      m_port = port;
      m_recvCount = new AtomicLong();
    }

    @Override
    public Void call() throws Exception
    {
      final int bufSize = 32 * 1024;
      MulticastSocket socket = null;

      try
      {
        socket = new MulticastSocket( m_port );
        socket.joinGroup( m_group );
        socket.setSoTimeout( 150 );
        socket.setReuseAddress( true );

        final byte[] buffer = new byte[bufSize];
        while ( !Thread.currentThread().isInterrupted() && !socket.isClosed() )
        {
          try
          {
            DatagramPacket packet = new DatagramPacket( buffer, buffer.length );
            socket.receive( packet );

            MulticastMessage msg = decodeMessage( packet );
            if ( msg != null )
            {
              notifyLocalServiceListeners( msg );
            }
          }
          catch ( SocketTimeoutException e )
          {
            // No worries, we simply try again...
          }
          catch ( Exception e )
          {
            // Break...
            socket.close();
          }
        }

        socket.leaveGroup( m_group );
        socket.close();
      }
      finally
      {
        System.out.printf( "Received %d bytes...%n", m_recvCount.get() );
      }
      
      return null;
    }

    private MulticastMessage decodeMessage( DatagramPacket packet )
    {
      try
      {
        MulticastMessage msg = CborCodec.decode( packet.getData(), packet.getOffset(), packet.getLength() );
        m_recvCount.addAndGet( packet.getLength() );
        return msg;
      }
      catch ( IOException e )
      {
        return null;
      }
    }

    private void notifyLocalServiceListeners( MulticastMessage msg )
    {
      if ( msg.isBroadcastState() )
      {
        broadcastState();
      }
      else if ( msg.isServiceAdded() )
      {
        addService( ServiceLocality.REMOTE, msg.getServiceInfo() );
      }
      else if ( msg.isServiceRemoved() )
      {
        removeService( ServiceLocality.REMOTE, msg.getServiceInfo() );
      }
    }
  }

  /**
   * Short-lived task for sending a multicast announcement.
   */
  static class MulticastSender implements Callable<Void>
  {
    private final InetAddress m_group;
    private final int m_port;
    private final MulticastMessage[] m_messages;

    public MulticastSender( InetAddress group, int port, MulticastMessage... messages )
    {
      m_group = group;
      m_port = port;
      m_messages = messages;
    }

    @Override
    public Void call() throws Exception
    {
      MulticastSocket socket = null;

      try
      {
        socket = new MulticastSocket();
        socket.setReuseAddress( true );

        for ( MulticastMessage msg : m_messages )
        {
          byte[] data = encodeMessage( msg );

          socket.send( new DatagramPacket( data, data.length, m_group, m_port ) );

          // Allow the data to be processed without flooding...
          TimeUnit.NANOSECONDS.sleep( 150L );
        }
      }
      finally
      {
        if ( socket != null )
        {
          socket.close();
        }
      }

      return null;
    }

    private byte[] encodeMessage( MulticastMessage message ) throws IOException
    {
      return CborCodec.encode( message );
    }
  }

  static class ServiceInfoHolder
  {
    final ServiceInfo m_info;
    final ServiceLocality m_locality;

    public ServiceInfoHolder( ServiceLocality locality, ServiceInfo info )
    {
      m_locality = locality;
      m_info = info;
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

      ServiceInfoHolder other = ( ServiceInfoHolder )obj;
      if ( !m_info.equals( other.m_info ) )
      {
        return false;
      }
      if ( m_locality != other.m_locality )
      {
        return false;
      }
      return true;
    }

    @Override
    public int hashCode()
    {
      final int prime = 31;
      int result = 1;
      result = prime * result + ( ( m_info == null ) ? 0 : m_info.hashCode() );
      result = prime * result + ( ( m_locality == null ) ? 0 : m_locality.hashCode() );
      return result;
    }

    boolean isSame( ServiceInfoHolder infoHolder )
    {
      if ( infoHolder == null )
      {
        return false;
      }
      ServiceInfo info = infoHolder.m_info;

      return m_info.getId().equals( info.getId() ) && m_info.getName().equals( info.getName() );
    }
  }

  static enum ServiceLocality
  {
    LOCAL, REMOTE;

    boolean isLocal()
    {
      return this == LOCAL;
    }
  }

  private static final int POOL_SIZE = 2;

  private final CopyOnWriteArrayList<ServiceListener> m_listeners;
  private final ConcurrentMap<String, ServiceInfoHolder> m_services;
  private final ScheduledExecutorService m_executor;

  private volatile InetAddress m_group;
  private volatile int m_port;

  /**
   * Creates a new {@link MulticastServiceAnnouncer} instance.
   */
  public MulticastServiceAnnouncer()
  {
    m_listeners = new CopyOnWriteArrayList<ServiceListener>();
    m_services = new ConcurrentHashMap<String, ServiceInfoHolder>();

    m_executor = Executors.newScheduledThreadPool( POOL_SIZE );
  }

  @Override
  public void addService( ServiceInfo info )
  {
    addService( ServiceLocality.LOCAL, info );
  }

  @Override
  public void addServiceListener( final ServiceListener listener )
  {
    m_listeners.add( listener );

    // Tell the listener the current state...
    m_executor.submit( new Runnable()
    {
      @Override
      public void run()
      {
        for ( ServiceInfoHolder infoHolder : m_services.values() )
        {
          listener.serviceAdded( infoHolder.m_info );
        }
      }
    } );
  }

  @Override
  public Collection<ServiceInfo> getKnownServices()
  {
    List<ServiceInfo> result = new ArrayList<ServiceInfo>();
    for ( ServiceInfoHolder holder : m_services.values() )
    {
      result.add( holder.m_info );
    }
    return result;
  }

  @Override
  public void removeService( ServiceInfo info )
  {
    removeService( ServiceLocality.LOCAL, info );
  }

  @Override
  public void removeServiceListener( ServiceListener listener )
  {
    m_listeners.remove( listener );
  }

  public void start() throws IOException
  {
    // TODO make this configurable...
    start( InetAddress.getByName( USD_DEFAULT_GROUP_IP ), USD_DEFAULT_PORT );
  }

  public void start( InetAddress group, int port ) throws IOException
  {
    m_group = group;
    m_port = port;

    // Schedule a listener...
    m_executor.submit( new MulticastListener( m_group, m_port ) );

    // Request the state from all existing announcers...
    requestState();
  }

  public void stop() throws IOException, InterruptedException
  {
    // Terminate all running jobs...
    m_executor.shutdownNow();
    m_executor.awaitTermination( 5, TimeUnit.SECONDS );
  }

  final void addService( ServiceLocality locality, ServiceInfo info )
  {
    ServiceInfoHolder holder = new ServiceInfoHolder( locality, info );

    ServiceInfoHolder oldInfo = m_services.putIfAbsent( info.getId(), holder );
    if ( oldInfo != null && !oldInfo.isSame( holder ) )
    {
      throw new IllegalArgumentException( "Duplicate service added!" );
    }
    if ( oldInfo == null )
    {
      if ( locality.isLocal() )
      {
        // Announce this to the rest of the world...
        announceServiceAdded( info );
      }

      // Tell our listeners about this...
      notifyServiceListenersServiceAdded( info );
    }
  }

  /**
   * Called when a multi-cast message is received for a broadcast of the current
   * state.
   */
  final void broadcastState()
  {
    List<MulticastMessage> msgs = new ArrayList<MulticastMessage>();
    for ( ServiceInfoHolder holder : m_services.values() )
    {
      if ( holder.m_locality.isLocal() )
      {
        msgs.add( MulticastMessage.createServiceAddedMessage( holder.m_info ) );
      }
    }

    MulticastMessage[] messages = msgs.toArray( new MulticastMessage[msgs.size()] );

    m_executor.submit( new MulticastSender( m_group, m_port, messages ) );
  }

  final void removeService( ServiceLocality locality, ServiceInfo info )
  {
    ServiceInfoHolder holder = new ServiceInfoHolder( locality, info );

    if ( m_services.remove( info.getId(), holder ) )
    {
      if ( locality.isLocal() )
      {
        // Announce this to the rest of the world...
        announceServiceRemoved( info );
      }

      // Tell our listeners about this...
      notifyServiceListenersServiceRemoved( info );
    }
  }

  /**
   * @param info
   */
  private void announceServiceAdded( ServiceInfo info )
  {
    MulticastMessage message = MulticastMessage.createServiceAddedMessage( info );
    m_executor.submit( new MulticastSender( m_group, m_port, message ) );
  }

  /**
   * @param info
   */
  private void announceServiceRemoved( ServiceInfo info )
  {
    MulticastMessage message = MulticastMessage.createServiceRemovedMessage( info );
    m_executor.submit( new MulticastSender( m_group, m_port, message ) );
  }

  private void notifyServiceListenersServiceAdded( final ServiceInfo info )
  {
    m_executor.submit( new Runnable()
    {
      @Override
      public void run()
      {
        for ( ServiceListener listener : m_listeners )
        {
          listener.serviceAdded( info );
        }
      }
    } );
  }

  private void notifyServiceListenersServiceRemoved( final ServiceInfo info )
  {
    m_executor.submit( new Runnable()
    {
      @Override
      public void run()
      {
        for ( ServiceListener listener : m_listeners )
        {
          listener.serviceRemoved( info );
        }
      }
    } );
  }

  /**
   * 
   */
  private void requestState()
  {
    MulticastMessage message = MulticastMessage.createBroadcastStateMessage();
    m_executor.submit( new MulticastSender( m_group, m_port, message ) );
  }
}
