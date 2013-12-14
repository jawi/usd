/**
 * 
 */
package usd;


import java.util.Collection;


/**
 * Service interface for announcing the availability of services.
 */
public interface ServiceAnnouncer
{
  /**
   * Announces the availability of a new/updated service.
   * 
   * @param info
   */
  void addService( ServiceInfo info );

  /**
   * @param listener
   */
  void addServiceListener( ServiceListener listener );

  /**
   * @return an unmodifiable collection of all known services, never
   *         <code>null</code>.
   */
  Collection<ServiceInfo> getKnownServices();

  /**
   * Announces a service is no longer available.
   * 
   * @param info
   */
  void removeService( ServiceInfo info );

  /**
   * @param listener
   */
  void removeServiceListener( ServiceListener listener );

}
