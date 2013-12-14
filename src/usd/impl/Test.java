/**
 * 
 */
package usd.impl;


import java.net.URI;
import java.util.concurrent.TimeUnit;

import usd.ServiceInfo;
import usd.ServiceListener;


/**
 * @author jawi
 */
public class Test
{

  /**
   * @param args
   */
  public static void main( String[] args ) throws Exception
  {
    MulticastServiceAnnouncer ann1 = new MulticastServiceAnnouncer();
    MulticastServiceAnnouncer ann2 = new MulticastServiceAnnouncer();

    ann1.addServiceListener( new ServiceListener()
    {
      @Override
      public void serviceRemoved( ServiceInfo info )
      {
        System.out.printf( ">> [Ann1] service removed: %s (%s)...%n", info.getName(), info.getId() );
      }

      @Override
      public void serviceAdded( ServiceInfo info )
      {
        System.out.printf( ">> [Ann1] service added: %s (%s)...%n", info.getName(), info.getId() );
      }
    } );

    ann1.start();
    ann2.start();

    System.out.println( "2 announcers started..." );

    ServiceInfo service1 = new ServiceInfo( "id1", "Service1", URI.create( "http://localhost:8080/serv1" ) );
    ServiceInfo service2 = new ServiceInfo( "id2", "Service2", URI.create( "http://localhost:8080/serv2" ) );

    ann1.addService( service1 );

    System.out.println( "Service 1 added..." );

    TimeUnit.MILLISECONDS.sleep( 500 );

    System.out.println( "Adding service listener..." );

    ann2.addServiceListener( new ServiceListener()
    {
      @Override
      public void serviceRemoved( ServiceInfo info )
      {
        System.out.printf( ">> [Ann2] service removed: %s (%s)...%n", info.getName(), info.getId() );
      }

      @Override
      public void serviceAdded( ServiceInfo info )
      {
        System.out.printf( ">> [Ann2] service added: %s (%s)...%n", info.getName(), info.getId() );
      }
    } );

    TimeUnit.MILLISECONDS.sleep( 500 );

    ann2.addService( service2 );

    System.out.println( "Service 2 added..." );

    TimeUnit.MILLISECONDS.sleep( 500 );

    ann2.removeService( service2 );

    System.out.println( "Service 2 removed..." );

    TimeUnit.MILLISECONDS.sleep( 750 );

    ann2.addService( service2 );

    System.out.println( "Service 2 added..." );

    TimeUnit.MILLISECONDS.sleep( 500 );

    ann1.removeService( service1 );
    ann2.removeService( service2 );

    System.out.println( "Service 1 & 2 removed..." );

    TimeUnit.SECONDS.sleep( 5 );

    ann1.stop();
    ann2.stop();

    System.out.println( "2 announcers stopped..." );
  }
}
