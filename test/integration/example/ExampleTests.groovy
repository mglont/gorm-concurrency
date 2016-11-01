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
    // how many items to insert in setUp()
    final int ORIG_SIZE = 1000
    // how many items to insert via worker threads
    final int SIZE = 200
    // the size of the thread pool -- assumes a hyper threading cpu
    final int POOL_SIZE = 2 * Runtime.getRuntime().availableProcessors()
    ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE)

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
            final int currentIdx = i
            assertNotNull User.findByUsername("testUser$currentIdx")
            pool.submit(new Runnable() {
                @Override
                void run() {
                    startLatch.await()
                    String un = "testUser${ORIG_SIZE + currentIdx}"
                    try {
                        User.withNewSession { session ->
                            boolean canFlush = true
                            def txDefinition = [
                                    name: "worker$currentIdx".toString(),
                                    isolationLevel:
                                            TransactionDefinition.ISOLATION_READ_COMMITTED
                            ]
                            User.withTransaction(txDefinition) {
                                try {
                                    // creating new content in this session is all right
                                    assertNull(User.findByUsername(un))
                                    def newUser = new User(username: un, password: "letMeIn")
                                    assertNotNull(newUser.save())
                                    assertFalse(newUser.hasErrors())
                                    userRefs.compareAndSet(currentIdx, null, newUser)

                                    def someUser = User.findByUsername("testUser$currentIdx")
                                    assertNotNull someUser
                                } catch (Throwable e) {
                                    canFlush = false
                                    fail("you got error $e.message")
                                }
                            }
                            if (canFlush) {
                                session.flush()
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
        // wait for workers to finish
        finishLatch.await()
        // start the shutdown process
        pool.shutdown()
        // wait at most 1s for the pool to finish
        pool.awaitTermination(1, TimeUnit.SECONDS)
        // interrupt any running jobs -- there should be none
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
