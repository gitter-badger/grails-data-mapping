package org.grails.orm.hibernate

import grails.persistence.Entity

import org.junit.Test

import static junit.framework.Assert.*

/**
 * Tests using domain events.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class DomainEventsTests extends AbstractGrailsHibernateTests {

    // test for GRAILS-4059
    @Test
    void testLastUpdateDoesntChangeWhenNotDirty() {
        def personClass = ga.getDomainClass(PersonDomainEvent.name).clazz
        def p = personClass.newInstance()

        p.name = "Fred"
        assertNotNull "person should have been saved",p.save()

        p.addToAddresses(postCode:"23209")
        assertNotNull "person should have been updated",p.save(flush:true)

        def address = p.addresses.iterator().next()

        assertTrue "address should have been saved", session.contains(address)
        def current = address.lastUpdated
        assertNotNull "should have created time sstamp",current

        session.flush()

        personClass.executeQuery("select f from PersonDomainEvent f join fetch f.addresses") // cause auto-flush of session

        def now = address.lastUpdated

        assertEquals "The last updated date should not have been changed!", current, now
    }

    // test for GRAILS-4041
    @Test
    void testNoModifyVersion() {

        def personClass = ga.getDomainClass(PersonDomainEvent2.name).clazz
        def p = personClass.newInstance()

        p.name = "Fred"
        p.beforeUpdate = { name = "Wilma" }
        p.save(flush:true)

        p.name = "Body"
        p.save()

        personClass.findWhere(name:"Wilma")
    }

    @Test
    void testDisabledAutoTimestamps() {
        def personClass = ga.getDomainClass(PersonDomainEvent2.name)
        def p = personClass.newInstance()

        p.name = "Fred"
        p.save()
        session.flush()
        session.clear()

        p = personClass.clazz.get(1)

        assertNull p.dateCreated
        assertNull p.lastUpdated

        p.name = "Wilma"
        p.save()
        session.flush()

        assertNull p.dateCreated
        assertNull p.lastUpdated
    }

    @Test
    void testAutoTimestamps() {
        def personClass = ga.getDomainClass(PersonDomainEvent.name)
        def p = personClass.newInstance()

        p.name = "Fred"
        p.save()
        session.flush()
        session.clear()

        p = personClass.clazz.get(1)

        sleep(2000)

        assertEquals p.dateCreated, p.lastUpdated

        p.name = "Wilma"
        p.save()
        session.flush()

        assertTrue p.dateCreated.before(p.lastUpdated)
    }

    @Test
    void testOnloadEvent() {
        def personClass = ga.getDomainClass(PersonDomainEvent.name)
        def p = personClass.newInstance()

        p.name = "Fred"
        p.save()
        session.flush()
        session.clear()

        p = personClass.clazz.get(1)
        assertEquals "Bob", p.name
    }

    @Test
    void testBeforeDeleteEvent() {
        def personClass = ga.getDomainClass(PersonDomainEvent.name)
        def p = personClass.newInstance()

        p.name = "Fred"
        p.save()
        session.flush()

        def success = false
        p.beforeDelete = { success = true }
        p.delete()
        session.flush()
        assertTrue success
    }

    @Test
    void testBeforeUpdateEvent() {
        def personClass = ga.getDomainClass(PersonDomainEvent2.name).clazz
        def p = personClass.newInstance()

        p.name = "Fred"
        p.save(flush:true)
        session.clear()

        p = personClass.get(1)
        assertEquals "Fred", p.name

        p.beforeUpdate = { name = "Wilma" }
        p.name = "Bob"
        assertNotNull p.save(flush:true)

        assertEquals "Wilma", p.name
        session.clear()

        p = personClass.get(1)
        assertEquals "Wilma", p.name
    }

    @Test
    void testBeforeInsertEvent() {
        def personClass = ga.getDomainClass(PersonDomainEvent.name).clazz
        def p = personClass.newInstance()

        p.name = "Fred"
        def success = false
        p.beforeInsert = { name = "Bob" }
        p.save()
        session.flush()

        assertEquals "Bob", p.name

        session.clear()

        p = personClass.get(1)

        assertEquals "Bob", p.name
        p.beforeInsert = { name = "Marge" }
        p.save(flush:true)

        assertEquals "Bob", p.name
    }

    protected getDomainClasses() {
        [PersonDomainEvent, DomainEventAddress, PersonDomainEvent2]
    }
}

@Entity
class PersonDomainEvent {
    Long id
    Long version
    String name
    Date dateCreated
    Date lastUpdated

    def afterLoad = {
        name = "Bob"
    }
    def beforeDelete = {}
    def beforeUpdate = {}
    def beforeInsert = {}

    Set addresses
    static hasMany = [addresses:DomainEventAddress]
}

@Entity
class DomainEventAddress {
    Long id
    Long version

    String postCode
    Date lastUpdated
    PersonDomainEvent person
    static belongsTo = [person:PersonDomainEvent]
}

@Entity
class PersonDomainEvent2 {
    Long id
    Long version
    String name
    Date dateCreated
    Date lastUpdated

    def beforeUpdate = {}
    static mapping = {
        autoTimestamp false
    }
    static constraints = {
        dateCreated nullable:true
        lastUpdated nullable:true
    }
}

