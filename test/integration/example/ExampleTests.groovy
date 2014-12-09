package example

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
    @Before
    void setUp() {
        User.withNewSession { session ->
            User.withTransaction {
                10.times { i ->
                    def user = new User(username: "testUser$i", password: "secret")
                    assertTrue user.validate()
                    assertFalse user.hasErrors()
                    assertNotNull(user.save(validate: false))
                }
            }
        }
        assertEquals 10, User.count()
    }

    @After
    void tearDown() {
        User.list().each { u -> u.delete() }
        assertEquals 0, User.count()
    }

    @Test
    void everythingIsOkayOnSameThread() {
        final int SIZE = 2
        SIZE.times{ i ->
            def someUser = User.findByUsername("testUser$i")
            // creating new content in this session is all right
            def newUser = new User(username: "testUser${10 + i}", password: "letMeIn")
            assertTrue newUser.validate()
            assertNotNull newUser.save()
            assertNotNull someUser
            assertEquals 11 + i, User.count()
        }
    }

    @Test
    void testConcurrentSubmissions() {
        final int SIZE = 2 // or any value less than 10
        final CountDownLatch startLatch = new CountDownLatch(1)
        final CountDownLatch finishLatch = new CountDownLatch(SIZE)
        final AtomicReferenceArray<User> userRefs = new AtomicReferenceArray<>(SIZE)
        ExecutorService pool = Executors.newFixedThreadPool(SIZE)
        SIZE.times { i ->
            pool.submit(new Runnable() {
                @Override
                void run() {
                    startLatch.await()
                    try {
                        User.withNewSession { session ->
                            User.withTransaction {
                                def someUser = User.findByUsername("testUser$i")
                                // creating new content in this session is all right
                                String un = "testUser${10+i}"
                                assertNull(User.findByUsername(un))
                                def newUser = new User(username: un, password: "letMeIn")
                                assertTrue(newUser.validate())
                                assertNotNull(newUser.save())
                                userRefs.compareAndSet(i, null, newUser)

                                //TODO getting something inserted in another session is problematic
                                assertNull someUser // SHOULD BE assertNotNull someUser
                                assertEquals 1, User.count() // SHOULD BE assertEquals 10+i, User.count()

                            }
                            //session.flush()
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
        10.times{ i ->
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