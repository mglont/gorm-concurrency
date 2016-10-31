package example

import org.hibernate.HibernateException
import org.hibernate.event.spi.AbstractEvent
import org.hibernate.event.spi.AutoFlushEvent
import org.hibernate.event.spi.AutoFlushEventListener
import org.hibernate.event.spi.FlushEntityEvent
import org.hibernate.event.spi.FlushEntityEventListener
import org.hibernate.event.spi.PersistEvent
import org.hibernate.event.spi.PersistEventListener
import org.hibernate.event.spi.PostInsertEvent
import org.hibernate.event.spi.PostInsertEventListener
import org.hibernate.event.spi.SaveOrUpdateEvent
import org.hibernate.event.spi.SaveOrUpdateEventListener
import org.hibernate.persister.entity.EntityPersister

/**
 * @short Simple Hibernate event listener that prints events to the console.
 *
 * @author Mihai Glonț <mglont@ebi.ac.uk>
 * Date: 09/12/14
 */
class HibernateEventSniffer implements PostInsertEventListener, AutoFlushEventListener,
        SaveOrUpdateEventListener, FlushEntityEventListener, PersistEventListener {

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
    boolean requiresPostCommitHanding(EntityPersister entityPersister) {
        return false
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
