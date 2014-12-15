// Place your Spring DSL code here
import example.HibernateEventSniffer
import org.codehaus.groovy.grails.orm.hibernate.HibernateEventListeners

//noinspection GroovyUnusedAssignment
beans = {
    persistenceListener(HibernateEventSniffer)
/*    hibernateEventListeners(HibernateEventListeners) {
        listenerMap = [
                'auto-flush': persistenceListener,
                'flush-entity' : persistenceListener,
                'flush' : persistenceListener,
                'create' : persistenceListener,
                'save' : persistenceListener,
                'save-update' : persistenceListener,
                'update' : persistenceListener,
                'post-insert' : persistenceListener,
                'create-onflush' : persistenceListener
        ]
    }*/
}
