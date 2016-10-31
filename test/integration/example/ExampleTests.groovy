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
    final int SIZE = 2 * Runtime.getRuntime().availableProcessors()
    final int ORIG_SIZE = 10
    ExecutorService pool = Executors.newFixedThreadPool(SIZE)

    @Before
    void setUp() {
        final CountDownLatch finishLatch = new CountDownLatch(1)
        // use a separate thread to ensure data is in the db
        pool.submit(new Runnable() {
            void run() {
                try {
                    User.withNewSession { session ->
                        User.withTransaction {
                            for (int i = 0; i < ORIG_SIZE; i++) {
                                def user = new User(username: "testUser$i", password: "secret")
                                assertTrue user.validate()
                                assertFalse user.hasErrors()
                                assertNotNull(user.save(validate: false))
                            }
                        }
                        session.flush()
                    }
                } catch (Exception e) {
                    fail( "setup error $e.message")
                } finally {
                    finishLatch.countDown()
                }
            }
        })
        finishLatch.await()
        assertEquals ORIG_SIZE, User.withNewSession { User.count() }
    }

    @After
    void tearDown() {
        def users = User.list()
        users.each { u ->
            u.delete(flush: true)
        }
        assertEquals 0, User.withNewSession { User.count() }
    }

    @Test
    void testConcurrentSubmissions() {
        assertEquals ORIG_SIZE, User.count()
        final CountDownLatch startLatch = new CountDownLatch(1)
        final CountDownLatch finishLatch = new CountDownLatch(SIZE)
        final AtomicReferenceArray<User> userRefs = new AtomicReferenceArray<>(SIZE)
        for (int i = 0; i < SIZE; i++) {
            final int j = i
            //assertNotNull User.findByUsername("testUser$i")
            pool.submit(new Runnable() {
                @Override
                void run() {
                    startLatch.await()
                    String un = "testUser${ORIG_SIZE + j}"
                    try {
                        User.withNewSession { session ->
                            User.withTransaction([
                                    name: "worker$j".toString(),
                                    isolationLevel:
                                            TransactionDefinition.ISOLATION_READ_COMMITTED
                            ]) {
                                try {
                                    // creating new content in this session is all right
                                    assertNull(User.findByUsername(un))
                                    def newUser = new User(username: un, password: "letMeIn")
                                    assertNotNull(newUser.save())
                                    assertFalse(newUser.hasErrors())
                                    assertEquals(0, newUser.errors.allErrors.size())
                                    userRefs.compareAndSet(j, null, newUser)

                                    //TODO getting something inserted in another session is problematic
                                    def someUser = User.findByUsername("testUser$j")
                                    assertNotNull someUser
                                } catch (Throwable e) {
                                    fail("you got error $e.message")
                                }
                            }
                            session.flush()
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

        // see what we got
        assertEquals SIZE + ORIG_SIZE, User.withNewSession { User.count() }

        for (int i = 0; i < SIZE; i++) {
            User expected = User.findByUsername("testUser$i")
            assertNotNull expected
        }

        for (int i = 0; i < SIZE; i++) {
            User actual = userRefs.get(i)
            User expected = User.findByUsername("testUser${ORIG_SIZE + i}")
            assertNotNull actual
            assertNotNull expected
            assertEquals expected, actual
        }
    }
}
