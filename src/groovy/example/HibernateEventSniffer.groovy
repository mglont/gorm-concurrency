package example

import org.codehaus.groovy.grails.orm.hibernate.events.SaveOrUpdateEventListener
import org.hibernate.HibernateException
import org.hibernate.event.AbstractEvent
import org.hibernate.event.AutoFlushEvent
import org.hibernate.event.AutoFlushEventListener
import org.hibernate.event.FlushEntityEvent
import org.hibernate.event.FlushEntityEventListener
import org.hibernate.event.PersistEvent
import org.hibernate.event.PersistEventListener
import org.hibernate.event.PostInsertEvent
import org.hibernate.event.PostInsertEventListener
import org.hibernate.event.SaveOrUpdateEvent

/**
 * @short Simple Hibernate event listener that prints events to the console.
 *
 * @author Mihai Glonț <mglont@ebi.ac.uk>
 * Date: 09/12/14
 */
class HibernateEventSniffer extends SaveOrUpdateEventListener implements PostInsertEventListener,
        AutoFlushEventListener, FlushEntityEventListener, PersistEventListener {
    /** Handle the given auto-flush event.
     *
     * @param event The auto-flush event to be handled.
     * @throws HibernateException
     */
    @Override
    void onAutoFlush(AutoFlushEvent event) throws HibernateException {
        sniff(event)
    }

    @Override
    void onPostInsert(PostInsertEvent event) {
        sniff(event)
    }

    @Override
    void onFlushEntity(FlushEntityEvent event) throws HibernateException {
        sniff(event)
    }

    /**
     * Handle the given create event.
     *
     * @param event The create event to be handled.
     * @throws HibernateException
     */
    @Override
    void onPersist(PersistEvent event) throws HibernateException {
        sniff(event)
    }

    /**
     * Handle the given create event.
     *
     * @param event The create event to be handled.
     * @param createdAlready @throws HibernateException
     */
    @Override
    void onPersist(PersistEvent event, Map createdAlready) throws HibernateException {
        sniff(event)
    }

    @Override
    void onSaveOrUpdate(SaveOrUpdateEvent event) {
        sniff(event)
    }

    static void sniff(AbstractEvent event) {
        println event.dump()
    }
}
