package example

import org.springframework.transaction.TransactionDefinition

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReferenceArray
import grails.test.mixin.TestMixin
import grails.test.mixin.integration.IntegrationTestMixin
import org.junit.*
import static org.junit.Assert.*

@TestMixin(IntegrationTestMixin)
class ExampleTests {
    def dataSource
    def sessionFactory
    @Before
    void setUp() {
/*        User.withNewSession { session ->
            User.withTransaction([
                    name: "setUp",
                    propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRED,
                    isolationLevel: TransactionDefinition.ISOLATION_READ_COMMITTED
            ]) {*/
                10.times { i ->
                    def user = new User(username: "testUser$i", password: "secret")
                    assertTrue user.validate()
                    assertFalse user.hasErrors()
                    assertNotNull(user.save(validate: false))
                }
  /*          }
        }*/
        assertEquals 10, User.count()
    }

    @After
    void tearDown() {
/*        User.withSession { session ->
            User.withTransaction([
                    name: "tearDown",
                    propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRED,
                    isolationLevel:
                            TransactionDefinition.ISOLATION_READ_COMMITTED
            ]) {*/
                def users = User.list()
                users.each { u ->
                    u.delete()
                }
            /*}
        }*/
        assertEquals 0, User.count()
    }

    //@Test
    @SuppressWarnings("GrMethodMayBeStatic")
    void everythingIsOkayOnSameThread() {
        final int SIZE = 2
        SIZE.times{ i ->
            def someUser = User.findByUsername("testUser$i")
            // creating new content in this session is all right
            def newUser = new User(username: "testUser${10 + i}", password: "letMeIn")
            assertTrue newUser.validate()
            assertNotNull newUser.save(validate: false, failOnError: true, flush: true)
            assertNotNull someUser
            assertEquals(11 + i, User.count())
        }
    }

    @Test
    void testConcurrentSubmissions() {
        final int SIZE = 1 // or any value less than 10
        final CountDownLatch startLatch = new CountDownLatch(1)
        final CountDownLatch finishLatch = new CountDownLatch(SIZE)
        final AtomicReferenceArray<User> userRefs = new AtomicReferenceArray<>(SIZE)
        ExecutorService pool = Executors.newFixedThreadPool(SIZE)
        SIZE.times { i ->
            pool.submit(new Runnable() {
                @Override
                void run() {
                    startLatch.await()
                    String un = "testUser${10 + i}"
                    try {
                        User.withNewSession {
                            User.withTransaction([
                                    name: "worker$i".toString(),
                                    propagationBehavior:
                                            TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                                    isolationLevel:
                                            TransactionDefinition.ISOLATION_READ_UNCOMMITTED
                            ]) {
                                try {
                                    def someUser = User.findByUsername("testUser$i")
                                    // creating new content in this session is all right
                                    assertNull(User.findByUsername(un))
                                    def newUser = new User(username: un, password: "letMeIn")
                                    assertTrue(newUser.validate())
                                    assertNotNull(newUser.save(validate: false, failOnError:
                                            true, flush: true))
                                    assertFalse(newUser.hasErrors())
                                    assertEquals(0, newUser.errors.allErrors.size())
                                    assertNotNull(newUser)
                                    userRefs.compareAndSet(i, null, newUser)

                                    //TODO getting something inserted in another session is problematic
                                    assertNotNull someUser
                                    assertEquals 11 + i, User.count()
                                } catch (Throwable e) {
                                    println "you got error $e"
                                    e.printStackTrace()
                                    println "end of your stack trace"
                                    fail("you lose!")
                                }
                            }
                        }
                    } finally {
                        finishLatch.countDown()
                    }
                }
            })
        }
        // simulate throwing concurrent requests to another service
        startLatch.countDown()
        finishLatch.await()
        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.SECONDS)
        def queuingJobs = pool.shutdownNow()
        assertEquals 0, queuingJobs.size()
        assertTrue pool.isTerminated()

        User.withNewSession { session ->
            User.withTransaction([
                    name: "mainThread-validation",
                    propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRES_NEW,
                    isolationLevel: TransactionDefinition.ISOLATION_READ_UNCOMMITTED
            ]) {
                // see what we got
                10.times { i ->
                    User expected = User.findByUsername("testUser$i")
                    assertNotNull expected
                }

                SIZE.times { i ->
                    User actual = userRefs.get(i)
                    User expected = User.findByUsername("testUser${10 + i}")
                    assertNotNull actual
                    assertNotNull expected
                    assertEquals expected, actual
                }
            }
        }
    }
}
