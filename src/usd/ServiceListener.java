/**
 * 
 */
package usd;


/**
 * Denotes a listener for service-availability announcements.
 */
public interface ServiceListener
{
  /**
   * @param info
   */
  void serviceAdded( ServiceInfo info );

  /**
   * @param info
   */
  void serviceRemoved( ServiceInfo info );
}
